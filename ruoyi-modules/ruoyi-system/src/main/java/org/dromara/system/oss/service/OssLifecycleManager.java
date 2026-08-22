package org.dromara.system.oss.service;

import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import lombok.RequiredArgsConstructor;
import org.dromara.common.oss.model.OssPresignedRequest;
import org.dromara.system.api.OssService;
import org.dromara.system.domain.SysOss;
import org.dromara.system.mapper.SysOssMapper;
import org.dromara.system.oss.config.OssLifecycleProperties;
import org.dromara.system.oss.domain.SysOssRef;
import org.dromara.system.oss.exception.OssLifecycleError;
import org.dromara.system.oss.exception.OssLifecycleException;
import org.dromara.system.oss.mapper.SysOssRefMapper;
import org.dromara.system.oss.provider.OssObjectStore;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 通过 sys_oss 行锁协调引用变更、删除和临时对象清理。
 */
@Service
@RequiredArgsConstructor
public class OssLifecycleManager {

    private static final Pattern PHYSICAL_TABLE = Pattern.compile("[a-z][a-z0-9_]{0,63}");
    private static final Pattern PHYSICAL_ID = Pattern.compile("[^\\s]{1,64}");

    private final SysOssMapper ossMapper;
    private final SysOssRefMapper refMapper;
    private final OssObjectStore objectStore;
    private final OssLifecycleProperties properties;

    @DSTransactional
    public OssService.OssReferenceState bind(Long ossId, String refType, String refId) {
        validateReference(refType, refId);
        SysOss oss = requireLocked(ossId);
        long before = refMapper.countActiveByOssId(ossId);
        if (!refMapper.existsActive(ossId, refType, refId)) {
            if (refMapper.restoreReference(ossId, refType, refId) == 0) {
                SysOssRef reference = new SysOssRef();
                reference.setOssId(ossId);
                reference.setRefType(refType);
                reference.setRefId(refId);
                reference.setDelFlag("0");
                refMapper.insert(reference);
            }
            before++;
        }
        if ("Y".equals(oss.getIsTemp()) || oss.getExpireTime() != null) {
            ossMapper.updateLifecycle(ossId, "N", null);
        }
        return new OssService.OssReferenceState(ossId, false, null, before);
    }

    @DSTransactional
    public OssService.OssReferenceState unbind(Long ossId, String refType, String refId) {
        validateReference(refType, refId);
        SysOss oss = requireLocked(ossId);
        long count = refMapper.countActiveByOssId(ossId);
        if (refMapper.existsActive(ossId, refType, refId)
            && refMapper.deactivateReference(ossId, refType, refId) > 0) {
            count--;
        }
        if (count == 0) {
            LocalDateTime expireTime = LocalDateTime.now().plus(properties.getTempRetention());
            ossMapper.updateLifecycle(ossId, "Y", expireTime);
            return new OssService.OssReferenceState(ossId, true, expireTime, 0);
        }
        return new OssService.OssReferenceState(ossId, false, null, count);
    }

    public OssService.OssLifecycleSnapshot snapshot(Long ossId) {
        SysOss oss = require(ossId);
        List<OssService.OssReference> references = refMapper.selectActiveByOssId(ossId).stream()
            .map(ref -> new OssService.OssReference(ref.getRefType(), ref.getRefId()))
            .toList();
        return new OssService.OssLifecycleSnapshot(ossId, "Y".equals(oss.getIsTemp()),
            oss.getExpireTime(), references);
    }

    public OssService.OssDownloadUrl presignDownload(Long ossId) {
        SysOss oss = require(ossId);
        OssPresignedRequest request = objectStore.presign(oss, properties.getDownloadTtl());
        return new OssService.OssDownloadUrl(request.url(), request.expiresAt(), oss.getOriginalName());
    }

    @DSTransactional
    public boolean deleteObjects(Collection<Long> ossIds) {
        if (ossIds == null || ossIds.isEmpty()) {
            return false;
        }
        List<Long> orderedIds = ossIds.stream().distinct().sorted().toList();
        List<SysOss> objects = ossMapper.selectByIdsForUpdate(orderedIds).stream()
            .sorted(Comparator.comparing(SysOss::getOssId)).toList();
        if (objects.size() != orderedIds.size()) {
            throw new OssLifecycleException(OssLifecycleError.OBJECT_NOT_FOUND, "部分 OSS 对象不存在");
        }
        for (SysOss oss : objects) {
            if (refMapper.countActiveByOssId(oss.getOssId()) > 0) {
                throw new OssLifecycleException(OssLifecycleError.OBJECT_REFERENCED,
                    "OSS 对象仍被业务数据引用: " + oss.getOssId());
            }
        }
        for (SysOss oss : objects) {
            objectStore.delete(oss);
        }
        return ossMapper.deleteByIds(orderedIds) > 0;
    }

    @DSTransactional
    public boolean cleanupExpired(Long ossId, LocalDateTime now, boolean dryRun) {
        SysOss oss = requireLocked(ossId);
        if (!"Y".equals(oss.getIsTemp()) || oss.getExpireTime() == null || oss.getExpireTime().isAfter(now)) {
            return false;
        }
        if (refMapper.countActiveByOssId(ossId) > 0) {
            ossMapper.updateLifecycle(ossId, "N", null);
            return false;
        }
        if (dryRun) {
            return true;
        }
        objectStore.delete(oss);
        return ossMapper.deleteById(ossId) > 0;
    }

    public List<Long> findExpiredTempIds(LocalDateTime now) {
        int limit = Math.max(1, properties.getCleanupBatchSize());
        return new ArrayList<>(ossMapper.selectExpiredTempIds(now, limit));
    }

    private SysOss requireLocked(Long ossId) {
        SysOss oss = ossMapper.selectByIdForUpdate(ossId);
        if (oss == null) {
            throw new OssLifecycleException(OssLifecycleError.OBJECT_NOT_FOUND, "OSS 对象不存在: " + ossId);
        }
        return oss;
    }

    private SysOss require(Long ossId) {
        SysOss oss = ossMapper.selectById(ossId);
        if (oss == null) {
            throw new OssLifecycleException(OssLifecycleError.OBJECT_NOT_FOUND, "OSS 对象不存在: " + ossId);
        }
        return oss;
    }

    private void validateReference(String refType, String refId) {
        if (refType == null || !PHYSICAL_TABLE.matcher(refType).matches()
            || refId == null || !PHYSICAL_ID.matcher(refId).matches()) {
            throw new OssLifecycleException(OssLifecycleError.INVALID_REFERENCE,
                "refType 必须是实际物理表名，refId 必须是真实主键");
        }
    }
}

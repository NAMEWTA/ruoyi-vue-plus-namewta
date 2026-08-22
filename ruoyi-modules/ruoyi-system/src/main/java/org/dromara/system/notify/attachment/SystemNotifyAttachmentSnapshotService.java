package org.dromara.system.notify.attachment;

import lombok.RequiredArgsConstructor;
import org.dromara.common.notify.attachment.NotifyAttachmentResource;
import org.dromara.common.notify.attachment.NotifyAttachmentSnapshot;
import org.dromara.common.notify.attachment.NotifyAttachmentSnapshotService;
import org.dromara.common.notify.exception.NotifyAttachmentSnapshotException;
import org.dromara.common.notify.model.NotifyContext;
import org.dromara.system.domain.SysOss;
import org.dromara.system.domain.SysOssExt;
import org.dromara.system.mapper.SysOssMapper;
import org.dromara.system.oss.config.OssLifecycleProperties;
import org.dromara.system.oss.service.OssLifecycleManager;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 将业务附件复制为每次逻辑通知独享的 TEMP OSS 对象。
 */
@Service
@RequiredArgsConstructor
public class SystemNotifyAttachmentSnapshotService implements NotifyAttachmentSnapshotService {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String NOTIFY_LOG_TABLE = "sys_notify_log";

    private final SysOssMapper mapper;
    private final NotifyAttachmentObjectStore objectStore;
    private final OssLifecycleManager lifecycleManager;
    private final OssLifecycleProperties lifecycleProperties;

    @Override
    public List<NotifyAttachmentSnapshot> createSnapshots(long notifyLogId, List<Long> sourceOssIds,
                                                           NotifyContext context) {
        List<NotifyAttachmentSnapshot> snapshots = new ArrayList<>(sourceOssIds.size());
        try {
            for (Long sourceOssId : sourceOssIds) {
                snapshots.add(createSnapshot(notifyLogId, sourceOssId));
            }
            return List.copyOf(snapshots);
        } catch (RuntimeException exception) {
            cleanupSnapshotsQuietly(snapshots, exception);
            if (exception instanceof NotifyAttachmentSnapshotException snapshotException) {
                throw snapshotException;
            }
            throw new NotifyAttachmentSnapshotException("SNAPSHOT_COPY_FAILED", "通知附件快照创建失败", exception);
        }
    }

    @Override
    public void cleanupSnapshots(List<NotifyAttachmentSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return;
        }
        lifecycleManager.deleteObjects(snapshotIds(snapshots));
    }

    private NotifyAttachmentSnapshot createSnapshot(long notifyLogId, Long sourceOssId) {
        SysOss source = mapper.selectById(sourceOssId);
        if (source == null) {
            throw new NotifyAttachmentSnapshotException("SOURCE_ATTACHMENT_NOT_FOUND",
                "通知源附件不存在: " + sourceOssId);
        }
        NotifyAttachmentObjectStore.CopiedObject copied = objectStore.copy(source, notifyLogId);
        try {
            SysOss snapshot = metadata(source, copied, notifyLogId);
            if (mapper.insert(snapshot) != 1 || snapshot.getOssId() == null) {
                throw new NotifyAttachmentSnapshotException("SNAPSHOT_METADATA_FAILED", "通知附件快照元数据登记失败");
            }
            NotifyAttachmentResource resource = new NotifyAttachmentResource(snapshot.getOssId(),
                snapshot.getOriginalName(), copied.contentType(), copied.size(),
                target -> objectStore.materialize(copied, target));
            return new NotifyAttachmentSnapshot(sourceOssId, resource);
        } catch (RuntimeException exception) {
            try {
                objectStore.delete(copied);
            } catch (RuntimeException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
    }

    private SysOss metadata(SysOss source, NotifyAttachmentObjectStore.CopiedObject copied, long notifyLogId) {
        SysOssExt ext = new SysOssExt();
        ext.setFileSize(copied.size());
        ext.setContentType(copied.contentType());
        ext.setSource("notifySnapshot");
        ext.setRefType(NOTIFY_LOG_TABLE);
        ext.setRefId(String.valueOf(notifyLogId));
        ext.setIsTemp(true);

        SysOss snapshot = new SysOss();
        snapshot.setFileName(copied.objectKey());
        snapshot.setOriginalName(source.getOriginalName());
        snapshot.setFileSuffix(source.getFileSuffix());
        snapshot.setUrl("");
        snapshot.setExt1(JSON.writeValueAsString(ext));
        snapshot.setService(copied.service());
        snapshot.setIsTemp("Y");
        snapshot.setExpireTime(LocalDateTime.now().plus(lifecycleProperties.getTempRetention()));
        return snapshot;
    }

    private void cleanupSnapshotsQuietly(List<NotifyAttachmentSnapshot> snapshots, RuntimeException original) {
        try {
            cleanupSnapshots(snapshots);
        } catch (RuntimeException cleanupException) {
            original.addSuppressed(cleanupException);
        }
    }

    private List<Long> snapshotIds(List<NotifyAttachmentSnapshot> snapshots) {
        return snapshots.stream().map(snapshot -> snapshot.resource().ossId()).distinct().toList();
    }
}

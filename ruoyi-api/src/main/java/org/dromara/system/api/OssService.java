package org.dromara.system.api;

import org.dromara.system.api.domain.OssDTO;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 通用 OSS服务
 *
 * @author Lion Li
 */
public interface OssService {

    /**
     * 通过ossId查询对应的url
     *
     * @param ossIds ossId串逗号分隔
     * @return url串逗号分隔
     */
    String selectUrlByIds(String ossIds);

    /**
     * 通过ossId查询列表
     *
     * @param ossIds ossId串逗号分隔
     * @return 列表
     */
    List<OssDTO> selectByIds(String ossIds);

    /**
     * 将一条业务数据保存前后的 OSS 集合协调为真实引用。
     *
     * <p>调用方必须先完成业务授权，并在保存业务数据的同一个动态数据源事务中调用。
     * null 集合按空集合处理；集合中的 ID 必须是正数。</p>
     *
     * @param refType       真实物理表名
     * @param refId         真实业务主键
     * @param previousOssIds 保存前的 OSS ID 集合
     * @param currentOssIds  保存后的 OSS ID 集合
     */
    void reconcileReferences(String refType, String refId,
                             Collection<Long> previousOssIds, Collection<Long> currentOssIds);

    /**
     * 查询对象当前生命周期及反向定位信息。
     */
    OssLifecycleSnapshot snapshot(Long ossId);

    /**
     * 在调用方完成业务权限校验后解析对象访问地址。
     */
    OssAccessUrl resolveAccessUrl(Long ossId);

    /**
     * 在调用方完成业务权限校验后为私有对象生成默认短时下载授权。
     */
    OssDownloadUrl presignDownload(Long ossId);

    /**
     * 在调用方完成业务权限校验后按服务端命名策略为私有对象生成下载授权。
     */
    OssDownloadUrl presignDownload(Long ossId, String policyName);

    record OssReferenceState(Long ossId, boolean temporary, LocalDateTime expireTime, long referenceCount) {
    }

    record OssReference(String refType, String refId) {
    }

    record OssLifecycleSnapshot(Long ossId, boolean temporary, LocalDateTime expireTime,
                                List<OssReference> references) {
        public OssLifecycleSnapshot {
            references = references == null ? List.of() : List.copyOf(references);
        }
    }

    record OssDownloadUrl(String url, Instant expiresAt, String fileName) {
    }

    record OssAccessUrl(String accessType, String url, @Nullable Instant expiresAt, String fileName) {
    }
}

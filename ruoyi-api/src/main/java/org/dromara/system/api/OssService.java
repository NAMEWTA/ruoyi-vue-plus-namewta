package org.dromara.system.api;

import org.dromara.system.api.domain.OssDTO;

import java.time.Instant;
import java.time.LocalDateTime;
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
     * 将 OSS 对象绑定到一条真实业务数据。
     */
    OssReferenceState bind(Long ossId, String refType, String refId);

    /**
     * 解除 OSS 对象与一条真实业务数据的绑定。
     */
    OssReferenceState unbind(Long ossId, String refType, String refId);

    /**
     * 查询对象当前生命周期及反向定位信息。
     */
    OssLifecycleSnapshot snapshot(Long ossId);

    /**
     * 在调用方完成业务权限校验后生成短时下载授权。
     */
    OssDownloadUrl presignDownload(Long ossId);

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
}

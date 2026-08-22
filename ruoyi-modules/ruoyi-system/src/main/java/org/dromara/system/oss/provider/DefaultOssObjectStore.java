package org.dromara.system.oss.provider;

import org.dromara.common.oss.client.OssClient;
import org.dromara.common.oss.factory.OssFactory;
import org.dromara.common.oss.model.OssPresignedRequest;
import org.dromara.system.domain.SysOss;
import org.dromara.system.oss.exception.OssLifecycleError;
import org.dromara.system.oss.exception.OssLifecycleException;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 基于 common-oss 的默认 Provider 实现。
 */
@Component
public class DefaultOssObjectStore implements OssObjectStore {

    @Override
    public OssPresignedRequest presign(SysOss oss, Duration ttl) {
        return client(oss).presignGet(oss.getFileName(), ttl);
    }

    @Override
    public void delete(SysOss oss) {
        try {
            if (!client(oss).delete(oss.getFileName())) {
                throw new OssLifecycleException(OssLifecycleError.PROVIDER_DELETE_FAILED,
                    "OSS Provider 删除失败: " + oss.getOssId());
            }
        } catch (OssLifecycleException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new OssLifecycleException(OssLifecycleError.PROVIDER_DELETE_FAILED,
                "OSS Provider 删除失败: " + oss.getOssId(), e);
        }
    }

    private OssClient client(SysOss oss) {
        return OssFactory.instance(oss.getService());
    }
}

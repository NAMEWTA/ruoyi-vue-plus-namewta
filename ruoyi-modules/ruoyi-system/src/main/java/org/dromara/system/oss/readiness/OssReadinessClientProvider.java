package org.dromara.system.oss.readiness;

import org.dromara.common.oss.client.OssClient;
import org.dromara.common.oss.factory.OssFactory;
import org.springframework.stereotype.Component;

/**
 * readiness 对 OSS client factory 的窄适配器。
 */
@FunctionalInterface
public interface OssReadinessClientProvider {

    OssClient client(String configKey);

    @Component
    class Default implements OssReadinessClientProvider {
        @Override
        public OssClient client(String configKey) {
            return OssFactory.instance(configKey);
        }
    }
}

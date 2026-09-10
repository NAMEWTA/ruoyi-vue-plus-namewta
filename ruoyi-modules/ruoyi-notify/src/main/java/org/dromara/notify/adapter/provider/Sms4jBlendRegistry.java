package org.dromara.notify.adapter.provider;

import org.dromara.notify.domain.entity.NotifyChannelAccount;
import org.dromara.notify.port.SmsBlendRegistryPort;
import org.dromara.sms4j.core.factory.SmsFactory;
import org.dromara.sms4j.provider.config.BaseConfig;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 把短信渠道账号注册为 SMS4J blend。
 */
@Component
public class Sms4jBlendRegistry implements SmsBlendRegistryPort {

    /**
     * 注册或更新。
     *
     * @param account 短信账号
     */
    @Override
    public void upsert(NotifyChannelAccount account) {
        if (account == null || account.getConfigKey() == null) {
            return;
        }
        remove(account.getConfigKey());
        RuntimeSmsConfig config = new RuntimeSmsConfig(account.getSupplier());
        config.setConfigId(account.getConfigKey());
        config.setAccessKeyId(account.getAccessKeyId());
        config.setAccessKeySecret(account.getAccessKeySecret());
        config.setSignature(account.getSignature());
        invokeOptional(config, "setSdkAppId", account.getSdkAppId());
        SmsFactory.createSmsBlend(config);
    }

    /**
     * 注销。
     *
     * @param configKey 账号标识
     */
    @Override
    public void remove(String configKey) {
        if (configKey == null || configKey.isBlank()) {
            return;
        }
        try {
            Method unregister = SmsFactory.class.getMethod("unregister", String.class);
            unregister.invoke(null, configKey);
        } catch (ReflectiveOperationException ignored) {
            // SMS4J 版本若无 unregister，下一次 create 会覆盖。
        }
    }

    private static final class RuntimeSmsConfig extends BaseConfig {
        private final String supplierName;

        private RuntimeSmsConfig(String supplierName) {
            this.supplierName = supplierName;
        }

        @Override
        public String getSupplier() {
            return supplierName;
        }
    }

    private void invokeOptional(Object target, String method, String value) {
        if (value == null) {
            return;
        }
        try {
            Method setter = target.getClass().getMethod(method, String.class);
            setter.invoke(target, value);
        } catch (ReflectiveOperationException ignored) {
            // 厂商差异字段忽略。
        }
    }
}

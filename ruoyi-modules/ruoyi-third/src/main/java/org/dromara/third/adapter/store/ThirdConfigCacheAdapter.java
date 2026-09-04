package org.dromara.third.adapter.store;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.third.domain.ThirdEndpoint;
import org.dromara.third.domain.ThirdProvider;
import org.dromara.third.port.ThirdConfigSnapshot;
import org.dromara.third.port.ThirdConfigSnapshotPort;
import org.dromara.third.port.ThirdEndpointConfigStore;
import org.dromara.third.port.ThirdProviderConfigStore;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class ThirdConfigCacheAdapter implements ThirdConfigSnapshotPort {
    private static final String PREFIX = "third:config:";
    private static final String CHANNEL = "third:config:invalidation";
    private final ThirdProviderConfigStore providerStore;
    private final ThirdEndpointConfigStore endpointStore;

    public ThirdConfigSnapshot get(String providerCode, String endpointCode) {
        String key = key(providerCode, endpointCode);
        try {
            String cached = RedisUtils.getCacheObject(key);
            if (cached != null && !cached.isBlank()) {
                ThirdConfigSnapshot snapshot = JsonUtils.parseObject(cached, ThirdConfigSnapshot.class);
                if (snapshot != null && snapshot.getProvider() != null && snapshot.getEndpoint() != null) return snapshot;
            }
        } catch (Throwable e) {
            throw unavailable(e);
        }
        ThirdProvider provider = providerStore.findActiveByCode(providerCode);
        ThirdEndpoint endpoint = provider == null ? null : endpointStore.findActiveByProviderAndCode(provider.getProviderId(), endpointCode);
        if (provider == null || endpoint == null || !provider.getProviderId().equals(endpoint.getProviderId())) {
            throw new ServiceException("第三方接口配置不存在");
        }
        ThirdConfigSnapshot snapshot = new ThirdConfigSnapshot(provider, endpoint);
        try {
            RedisUtils.setCacheObject(key, JsonUtils.toJsonString(snapshot), Duration.ofMinutes(10));
        } catch (Throwable e) {
            throw unavailable(e);
        }
        return snapshot;
    }

    public void evict(String providerCode, String endpointCode) {
        try {
            if (providerCode != null && endpointCode != null) {
                RedisUtils.deleteObject(key(providerCode, endpointCode));
            } else if (providerCode != null) {
                endpointStore.findAllByProviderCode(providerCode).forEach(endpoint ->
                    RedisUtils.deleteObject(key(providerCode, endpoint.getEndpointCode())));
            }
            RedisUtils.publish(CHANNEL, providerCode + ":" + (endpointCode == null ? "*" : endpointCode));
        } catch (RuntimeException e) {
            throw unavailable(e);
        }
    }

    public static String key(String providerCode, String endpointCode) {
        return PREFIX + providerCode + ":" + endpointCode;
    }

    private static ServiceException unavailable(Throwable cause) {
        return new ServiceException("第三方配置缓存不可用");
    }
}

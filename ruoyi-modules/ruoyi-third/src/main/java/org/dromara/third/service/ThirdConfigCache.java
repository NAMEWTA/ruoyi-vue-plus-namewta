package org.dromara.third.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.third.domain.ThirdEndpoint;
import org.dromara.third.domain.ThirdProvider;
import org.dromara.third.mapper.ThirdEndpointMapper;
import org.dromara.third.mapper.ThirdProviderMapper;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class ThirdConfigCache {
    private static final String PREFIX = "third:config:";
    private static final String CHANNEL = "third:config:invalidation";
    private final ThirdProviderMapper providerMapper;
    private final ThirdEndpointMapper endpointMapper;

    public ThirdConfigSnapshot get(String providerCode, String endpointCode) {
        String key = key(providerCode, endpointCode);
        try {
            String cached = RedisUtils.getCacheObject(key);
            if (cached != null && !cached.isBlank()) {
                ThirdConfigSnapshot snapshot = JsonUtils.parseObject(cached, ThirdConfigSnapshot.class);
                if (snapshot != null && snapshot.getProvider() != null && snapshot.getEndpoint() != null) return snapshot;
            }
        } catch (RuntimeException e) {
            throw unavailable(e);
        }
        ThirdProvider provider = providerMapper.selectOne(new LambdaQueryWrapper<ThirdProvider>()
            .eq(ThirdProvider::getProviderCode, providerCode).eq(ThirdProvider::getDelFlag, "0"));
        ThirdEndpoint endpoint = endpointMapper.selectOne(new LambdaQueryWrapper<ThirdEndpoint>()
            .eq(ThirdEndpoint::getProviderCode, providerCode).eq(ThirdEndpoint::getEndpointCode, endpointCode).eq(ThirdEndpoint::getDelFlag, "0"));
        if (provider == null || endpoint == null || !provider.getProviderId().equals(endpoint.getProviderId())) {
            throw new ServiceException("第三方接口配置不存在");
        }
        ThirdConfigSnapshot snapshot = new ThirdConfigSnapshot(provider, endpoint);
        try {
            RedisUtils.setCacheObject(key, JsonUtils.toJsonString(snapshot), Duration.ofMinutes(10));
        } catch (RuntimeException e) {
            throw unavailable(e);
        }
        return snapshot;
    }

    public void evict(String providerCode, String endpointCode) {
        try {
            if (providerCode != null && endpointCode != null) {
                RedisUtils.deleteObject(key(providerCode, endpointCode));
            } else if (providerCode != null) {
                endpointMapper.selectList(new LambdaQueryWrapper<ThirdEndpoint>()
                    .eq(ThirdEndpoint::getProviderCode, providerCode)).forEach(endpoint ->
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

package org.dromara.third.service;

import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.third.api.ThirdPartyFailureCategory;
import org.dromara.third.domain.ThirdEndpoint;
import org.dromara.third.domain.ThirdProvider;
import org.redisson.api.RSemaphore;
import org.redisson.api.RateType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ThirdResiliencePolicy {
    public ThirdLimitLease acquire(ThirdProvider provider, ThirdEndpoint endpoint) {
        List<RSemaphore> acquired = new ArrayList<>();
        try {
            if (positive(provider.getRateLimit()) && RedisUtils.rateLimiter("third:rate:provider:" + provider.getProviderCode(), RateType.OVERALL, provider.getRateLimit(), 1) < 0) {
                throw new ThirdRejectedException(ThirdPartyFailureCategory.RATE_LIMITED, "Provider rate limit exceeded");
            }
            if (positive(endpoint.getRateLimit()) && RedisUtils.rateLimiter("third:rate:endpoint:" + provider.getProviderCode() + ":" + endpoint.getEndpointCode(), RateType.OVERALL, endpoint.getRateLimit(), 1) < 0) {
                throw new ThirdRejectedException(ThirdPartyFailureCategory.RATE_LIMITED, "Endpoint rate limit exceeded");
            }
            acquireSemaphore(acquired, "third:concurrency:provider:" + provider.getProviderCode(), provider.getConcurrencyLimit());
            acquireSemaphore(acquired, "third:concurrency:endpoint:" + provider.getProviderCode() + ":" + endpoint.getEndpointCode(), endpoint.getConcurrencyLimit());
            return () -> acquired.forEach(this::release);
        } catch (ThirdRejectedException e) {
            acquired.forEach(this::release);
            throw e;
        } catch (RuntimeException e) {
            acquired.forEach(this::release);
            throw new ThirdRejectedException(ThirdPartyFailureCategory.CONFIG_UNAVAILABLE, "Third-party limit service unavailable");
        }
    }

    public int maxAttempts(ThirdEndpoint endpoint) {
        return Boolean.TRUE.equals(endpoint.getIdempotent()) ? 1 + Math.min(Math.max(endpoint.getRetryCount() == null ? 0 : endpoint.getRetryCount(), 0), 3) : 1;
    }

    private void acquireSemaphore(List<RSemaphore> acquired, String key, Integer limit) {
        if (!positive(limit)) return;
        RSemaphore semaphore = RedisUtils.getClient().getSemaphore(key);
        semaphore.trySetPermits(limit);
        if (!semaphore.tryAcquire()) throw new ThirdRejectedException(ThirdPartyFailureCategory.REJECTED, "Concurrency limit exceeded");
        acquired.add(semaphore);
    }

    private void release(RSemaphore semaphore) {
        try {
            semaphore.release();
        } catch (RuntimeException ignored) {
        }
    }

    private static boolean positive(Integer value) {
        return value != null && value > 0;
    }
}

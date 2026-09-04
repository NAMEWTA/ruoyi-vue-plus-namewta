package org.dromara.third.port;

import org.dromara.third.domain.ThirdEndpoint;
import org.dromara.third.domain.ThirdProvider;
import org.dromara.third.support.ThirdLimitLease;

/** Pre-send rate/concurrency and retry policy boundary. */
public interface ThirdResiliencePort {
    ThirdLimitLease acquire(ThirdProvider provider, ThirdEndpoint endpoint);

    int maxAttempts(ThirdEndpoint endpoint);
}

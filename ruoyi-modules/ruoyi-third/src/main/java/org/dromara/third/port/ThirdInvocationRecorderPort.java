package org.dromara.third.port;

import org.dromara.third.api.ThirdPartyRequest;
import org.dromara.third.api.ThirdPartyResponse;

/** Logical invocation and outbound audit recording boundary. */
public interface ThirdInvocationRecorderPort {
    void record(ThirdPartyRequest request, ThirdPartyResponse<?> response, long durationMs, int attempts);
}

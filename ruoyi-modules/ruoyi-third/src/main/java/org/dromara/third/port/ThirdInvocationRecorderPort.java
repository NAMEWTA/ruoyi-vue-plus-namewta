package org.dromara.third.port;

import org.dromara.third.api.ThirdPartyRequest;
import org.dromara.third.api.ThirdPartyResponse;

import java.util.Set;

/** Logical invocation and outbound audit recording boundary. */
public interface ThirdInvocationRecorderPort {
    void record(ThirdPartyRequest request, ThirdPartyResponse<?> response, long durationMs, int attempts);

    default void record(ThirdPartyRequest request, ThirdPartyResponse<?> response, long durationMs, int attempts,
                        Set<String> additionalSensitiveFields) {
        record(request, response, durationMs, attempts);
    }
}

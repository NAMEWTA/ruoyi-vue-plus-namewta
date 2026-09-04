package org.dromara.third.spi;

import org.dromara.third.api.ThirdPartyRequest;

public record ThirdAdapterResponse(ThirdPartyRequest request, int httpStatus, Object body,
                                   long durationMs, String requestId) {
}

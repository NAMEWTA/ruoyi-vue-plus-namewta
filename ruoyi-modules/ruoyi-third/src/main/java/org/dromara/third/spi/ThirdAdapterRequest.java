package org.dromara.third.spi;

import org.dromara.third.api.ThirdPartyRequest;
import org.dromara.third.port.ThirdConfigSnapshot;

import java.util.Map;

public record ThirdAdapterRequest(ThirdPartyRequest request, ThirdConfigSnapshot snapshot,
                                  Map<String, String> headers, Object body) {
}

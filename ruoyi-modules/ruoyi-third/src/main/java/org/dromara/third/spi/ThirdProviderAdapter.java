package org.dromara.third.spi;

import org.dromara.third.api.ThirdPartyResponse;

/** Provider-specific signing, encryption and business-error mapping extension. */
public interface ThirdProviderAdapter {
    String providerCode();

    default ThirdAdapterRequest prepare(ThirdAdapterRequest request) {
        return request;
    }

    default ThirdPartyResponse<?> mapResponse(ThirdAdapterResponse response) {
        return null;
    }
}

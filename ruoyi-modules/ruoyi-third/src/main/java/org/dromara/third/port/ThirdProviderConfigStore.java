package org.dromara.third.port;

import org.dromara.third.domain.ThirdProvider;

/** Minimal provider read boundary used by the configuration snapshot adapter. */
public interface ThirdProviderConfigStore {
    ThirdProvider findActiveByCode(String providerCode);
}

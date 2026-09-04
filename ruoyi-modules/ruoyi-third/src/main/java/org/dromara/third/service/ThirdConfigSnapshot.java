package org.dromara.third.service;

import lombok.Data;
import org.dromara.third.domain.ThirdEndpoint;
import org.dromara.third.domain.ThirdProvider;

@Data
public class ThirdConfigSnapshot {
    private ThirdProvider provider;
    private ThirdEndpoint endpoint;

    public ThirdConfigSnapshot() {
    }

    public ThirdConfigSnapshot(ThirdProvider provider, ThirdEndpoint endpoint) {
        this.provider = provider;
        this.endpoint = endpoint;
    }
}

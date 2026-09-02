package org.dromara.profile.enterprise.service.impl;

import org.dromara.profile.enterprise.service.EnterpriseVerificationTimeSource;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class SystemEnterpriseVerificationTimeSource implements EnterpriseVerificationTimeSource {

    @Override
    public Instant now() {
        return Instant.now();
    }
}

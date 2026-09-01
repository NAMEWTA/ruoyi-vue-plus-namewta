package org.dromara.profile.enterprise.verification;

import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class SystemEnterpriseVerificationTimeSource implements EnterpriseVerificationTimeSource {

    @Override
    public Instant now() {
        return Instant.now();
    }
}

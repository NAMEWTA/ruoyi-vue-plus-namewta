package org.dromara.profile.enterprise.verification;

import java.time.Instant;

@FunctionalInterface
public interface EnterpriseVerificationTimeSource {

    Instant now();
}

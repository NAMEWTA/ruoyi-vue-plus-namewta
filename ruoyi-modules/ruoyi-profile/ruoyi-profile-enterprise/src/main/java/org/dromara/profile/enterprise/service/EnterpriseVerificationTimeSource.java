package org.dromara.profile.enterprise.service;

import java.time.Instant;

@FunctionalInterface
public interface EnterpriseVerificationTimeSource {

    Instant now();
}

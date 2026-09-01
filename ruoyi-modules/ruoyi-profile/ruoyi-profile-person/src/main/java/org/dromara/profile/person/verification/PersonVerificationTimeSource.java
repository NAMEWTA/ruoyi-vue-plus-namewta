package org.dromara.profile.person.verification;

import java.time.Instant;

@FunctionalInterface
public interface PersonVerificationTimeSource {

    Instant now();
}

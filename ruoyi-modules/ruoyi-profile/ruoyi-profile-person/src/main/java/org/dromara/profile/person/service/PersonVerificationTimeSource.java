package org.dromara.profile.person.service;

import java.time.Instant;

@FunctionalInterface
public interface PersonVerificationTimeSource {

    Instant now();
}

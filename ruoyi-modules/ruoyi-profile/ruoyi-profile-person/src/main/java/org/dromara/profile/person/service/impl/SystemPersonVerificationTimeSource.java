package org.dromara.profile.person.service.impl;

import org.dromara.profile.person.service.PersonVerificationTimeSource;

import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class SystemPersonVerificationTimeSource implements PersonVerificationTimeSource {

    @Override
    public Instant now() {
        return Instant.now();
    }
}

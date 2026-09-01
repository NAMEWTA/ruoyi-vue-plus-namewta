package org.dromara.profile.person.verification;

import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class SystemPersonVerificationTimeSource implements PersonVerificationTimeSource {

    @Override
    public Instant now() {
        return Instant.now();
    }
}

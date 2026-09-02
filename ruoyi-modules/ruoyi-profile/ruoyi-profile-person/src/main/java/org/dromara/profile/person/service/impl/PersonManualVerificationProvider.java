package org.dromara.profile.person.service.impl;

import org.dromara.profile.person.service.PersonVerificationProvider;

import org.dromara.profile.person.domain.exception.PersonVerificationException;
import org.dromara.profile.person.domain.verification.PersonProviderCallbackEnvelope;
import org.dromara.profile.person.domain.verification.PersonProviderStartCommand;
import org.dromara.profile.person.domain.verification.PersonProviderStartResult;
import org.dromara.profile.person.domain.verification.PersonVerificationFailureCategory;
import org.dromara.profile.person.domain.verification.PersonVerifiedCallback;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class PersonManualVerificationProvider implements PersonVerificationProvider {

    @Override
    public String providerCode() {
        return "manual";
    }

    @Override
    public PersonProviderStartResult start(PersonProviderStartCommand command) {
        return PersonProviderStartResult.pending();
    }

    @Override
    public PersonVerifiedCallback authenticate(PersonProviderCallbackEnvelope callback, Instant receivedAt) {
        throw new PersonVerificationException(
            PersonVerificationFailureCategory.UNSUPPORTED_CALLBACK,
            "Manual person verification does not accept callbacks");
    }
}

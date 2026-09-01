package org.dromara.profile.person.verification;

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

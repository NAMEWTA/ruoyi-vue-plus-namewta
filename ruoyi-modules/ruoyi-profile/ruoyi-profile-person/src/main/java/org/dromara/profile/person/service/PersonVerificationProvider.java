package org.dromara.profile.person.service;

import org.dromara.profile.person.domain.verification.PersonProviderCallbackEnvelope;
import org.dromara.profile.person.domain.verification.PersonProviderStartCommand;
import org.dromara.profile.person.domain.verification.PersonProviderStartResult;
import org.dromara.profile.person.domain.verification.PersonVerifiedCallback;
import java.time.Instant;

/**
 * Adapter contract for one person-verification provider.
 *
 * <p>The adapter authenticates provider-specific callbacks and returns only
 * normalized evidence. It must never publish a profile or change a binding.</p>
 */
public interface PersonVerificationProvider {

    String providerCode();

    PersonProviderStartResult start(PersonProviderStartCommand command);

    PersonVerifiedCallback authenticate(PersonProviderCallbackEnvelope callback, Instant receivedAt);
}

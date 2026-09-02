package org.dromara.profile.enterprise.service;

import org.dromara.profile.enterprise.domain.verification.EnterpriseProviderCallbackEnvelope;
import org.dromara.profile.enterprise.domain.verification.EnterpriseProviderStartCommand;
import org.dromara.profile.enterprise.domain.verification.EnterpriseProviderStartResult;
import org.dromara.profile.enterprise.domain.verification.EnterpriseVerifiedCallback;
import java.time.Instant;

/**
 * Adapter contract for one enterprise-verification provider.
 *
 * <p>The adapter authenticates provider-specific callbacks and returns only
 * normalized evidence. It must never publish a profile or change a binding.</p>
 */
public interface EnterpriseVerificationProvider {

    String providerCode();

    EnterpriseProviderStartResult start(EnterpriseProviderStartCommand command);

    EnterpriseVerifiedCallback authenticate(EnterpriseProviderCallbackEnvelope callback, Instant receivedAt);
}

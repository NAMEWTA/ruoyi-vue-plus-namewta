package org.dromara.profile.enterprise.verification;

import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class EnterpriseManualVerificationProvider implements EnterpriseVerificationProvider {

    @Override
    public String providerCode() {
        return "manual";
    }

    @Override
    public EnterpriseProviderStartResult start(EnterpriseProviderStartCommand command) {
        return EnterpriseProviderStartResult.pending();
    }

    @Override
    public EnterpriseVerifiedCallback authenticate(EnterpriseProviderCallbackEnvelope callback, Instant receivedAt) {
        throw new EnterpriseVerificationException(
            EnterpriseVerificationFailureCategory.UNSUPPORTED_CALLBACK,
            "Manual enterprise verification does not accept callbacks");
    }
}

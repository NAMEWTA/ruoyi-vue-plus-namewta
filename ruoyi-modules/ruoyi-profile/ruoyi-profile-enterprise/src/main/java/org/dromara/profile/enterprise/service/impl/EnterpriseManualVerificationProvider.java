package org.dromara.profile.enterprise.service.impl;

import org.dromara.profile.enterprise.domain.exception.EnterpriseVerificationException;
import org.dromara.profile.enterprise.domain.verification.EnterpriseProviderCallbackEnvelope;
import org.dromara.profile.enterprise.domain.verification.EnterpriseProviderStartCommand;
import org.dromara.profile.enterprise.domain.verification.EnterpriseProviderStartResult;
import org.dromara.profile.enterprise.domain.verification.EnterpriseVerificationFailureCategory;
import org.dromara.profile.enterprise.domain.verification.EnterpriseVerifiedCallback;
import org.dromara.profile.enterprise.service.EnterpriseVerificationProvider;
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

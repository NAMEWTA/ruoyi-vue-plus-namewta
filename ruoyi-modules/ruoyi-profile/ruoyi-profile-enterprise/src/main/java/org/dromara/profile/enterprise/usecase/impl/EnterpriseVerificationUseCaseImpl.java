package org.dromara.profile.enterprise.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.profile.enterprise.domain.verification.EnterpriseProviderCallbackEnvelope;
import org.dromara.profile.enterprise.domain.verification.EnterpriseVerificationCallbackOutcome;
import org.dromara.profile.enterprise.service.EnterpriseVerificationService;
import org.dromara.profile.enterprise.usecase.EnterpriseVerificationUseCase;
import org.springframework.stereotype.Service;

/**
 * EnterpriseVerificationUseCaseImpl 应用用例合同，定义入口可调用的业务场景。
 */
@Service
@RequiredArgsConstructor
public class EnterpriseVerificationUseCaseImpl implements EnterpriseVerificationUseCase {
    private final EnterpriseVerificationService coordinator;
    /**
     * 编排 callback 应用用例。
     */
    @Override public EnterpriseVerificationCallbackOutcome callback(String providerCode,
                                                                       EnterpriseProviderCallbackEnvelope envelope,
                                                                       java.time.Instant receivedAt) {
        return coordinator.handleCallback(providerCode, envelope, receivedAt);
    }
}

package org.dromara.profile.enterprise.usecase;

import org.dromara.profile.enterprise.domain.verification.EnterpriseProviderCallbackEnvelope;
import org.dromara.profile.enterprise.domain.verification.EnterpriseVerificationCallbackOutcome;

import java.time.Instant;

/**
 * EnterpriseVerificationUseCase 应用用例合同，定义入口可调用的业务场景。
 */
public interface EnterpriseVerificationUseCase {
    /**
     * 编排 callback 应用用例。
     */
    EnterpriseVerificationCallbackOutcome callback(String providerCode,
                                                   EnterpriseProviderCallbackEnvelope envelope,
                                                   Instant receivedAt);
}

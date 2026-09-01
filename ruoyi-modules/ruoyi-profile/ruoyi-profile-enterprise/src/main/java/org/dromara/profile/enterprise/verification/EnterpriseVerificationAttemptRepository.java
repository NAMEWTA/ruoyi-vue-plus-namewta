package org.dromara.profile.enterprise.verification;

import java.util.Optional;

/**
 * Database port for serialized attempt and callback updates.
 */
public interface EnterpriseVerificationAttemptRepository {

    EnterpriseApplicationVerificationState lockApplication(long applicationId);

    int nextAttemptNo(long applicationId);

    EnterpriseVerificationAttempt append(EnterpriseVerificationAttempt attempt);

    Optional<EnterpriseVerificationAttempt> lockByProviderRequest(String providerCode, String providerRequestId);

    void complete(long verificationAttemptId, EnterpriseVerifiedCallback callback);

    void appendSecurityAudit(EnterpriseVerificationSecurityAudit audit);
}

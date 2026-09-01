package org.dromara.profile.person.verification;

import java.util.Optional;

/**
 * Database port for serialized attempt and callback updates.
 */
public interface PersonVerificationAttemptRepository {

    PersonApplicationVerificationState lockApplication(long applicationId);

    int nextAttemptNo(long applicationId);

    PersonVerificationAttempt append(PersonVerificationAttempt attempt);

    Optional<PersonVerificationAttempt> lockByProviderRequest(String providerCode, String providerRequestId);

    void complete(long verificationAttemptId, PersonVerifiedCallback callback);

    void appendSecurityAudit(PersonVerificationSecurityAudit audit);
}

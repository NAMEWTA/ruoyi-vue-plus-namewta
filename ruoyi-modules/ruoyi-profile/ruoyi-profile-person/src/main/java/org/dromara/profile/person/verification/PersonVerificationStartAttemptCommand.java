package org.dromara.profile.person.verification;

import java.util.Objects;

public record PersonVerificationStartAttemptCommand(
    long applicationId,
    long submissionId,
    String requestFingerprint
) {
    public PersonVerificationStartAttemptCommand {
        if (applicationId <= 0 || submissionId <= 0) {
            throw new IllegalArgumentException("applicationId and submissionId must be positive");
        }
        Objects.requireNonNull(requestFingerprint, "requestFingerprint");
        if (requestFingerprint.isBlank()) {
            throw new IllegalArgumentException("requestFingerprint must not be blank");
        }
    }
}

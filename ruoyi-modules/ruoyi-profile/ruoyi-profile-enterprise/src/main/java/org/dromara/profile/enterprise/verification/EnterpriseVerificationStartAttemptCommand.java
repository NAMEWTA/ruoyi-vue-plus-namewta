package org.dromara.profile.enterprise.verification;

import java.util.Objects;

public record EnterpriseVerificationStartAttemptCommand(
    long applicationId,
    long submissionId,
    String requestFingerprint
) {
    public EnterpriseVerificationStartAttemptCommand {
        if (applicationId <= 0 || submissionId <= 0) {
            throw new IllegalArgumentException("applicationId and submissionId must be positive");
        }
        Objects.requireNonNull(requestFingerprint, "requestFingerprint");
        if (requestFingerprint.isBlank()) {
            throw new IllegalArgumentException("requestFingerprint must not be blank");
        }
    }
}

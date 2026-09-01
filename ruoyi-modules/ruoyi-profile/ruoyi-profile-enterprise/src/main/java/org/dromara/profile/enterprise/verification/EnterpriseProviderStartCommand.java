package org.dromara.profile.enterprise.verification;

import java.util.Objects;

public record EnterpriseProviderStartCommand(
    long applicationId,
    long submissionId,
    int attemptNo,
    String requestFingerprint
) {
    public EnterpriseProviderStartCommand {
        if (applicationId <= 0 || submissionId <= 0 || attemptNo <= 0) {
            throw new IllegalArgumentException("applicationId, submissionId and attemptNo must be positive");
        }
        Objects.requireNonNull(requestFingerprint, "requestFingerprint");
        if (requestFingerprint.isBlank()) {
            throw new IllegalArgumentException("requestFingerprint must not be blank");
        }
    }
}

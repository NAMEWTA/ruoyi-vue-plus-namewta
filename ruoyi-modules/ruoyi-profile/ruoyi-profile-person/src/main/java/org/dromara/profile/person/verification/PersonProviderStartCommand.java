package org.dromara.profile.person.verification;

import java.util.Objects;

public record PersonProviderStartCommand(
    long applicationId,
    long submissionId,
    int attemptNo,
    String requestFingerprint
) {
    public PersonProviderStartCommand {
        if (applicationId <= 0 || submissionId <= 0 || attemptNo <= 0) {
            throw new IllegalArgumentException("applicationId, submissionId and attemptNo must be positive");
        }
        Objects.requireNonNull(requestFingerprint, "requestFingerprint");
        if (requestFingerprint.isBlank()) {
            throw new IllegalArgumentException("requestFingerprint must not be blank");
        }
    }
}

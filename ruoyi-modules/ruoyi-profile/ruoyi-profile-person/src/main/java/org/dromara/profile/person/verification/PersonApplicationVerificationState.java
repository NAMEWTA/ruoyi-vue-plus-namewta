package org.dromara.profile.person.verification;

import java.util.Objects;
import java.util.Set;

public record PersonApplicationVerificationState(
    long applicationId,
    long submissionId,
    String providerCode,
    String status
) {
    private static final Set<String> TERMINAL_STATUSES = Set.of("FINISH", "INVALID", "TERMINATION");

    public PersonApplicationVerificationState {
        if (applicationId <= 0 || submissionId <= 0) {
            throw new IllegalArgumentException("applicationId and submissionId must be positive");
        }
        Objects.requireNonNull(providerCode, "providerCode");
        Objects.requireNonNull(status, "status");
    }

    public boolean terminal() {
        return TERMINAL_STATUSES.contains(status);
    }
}

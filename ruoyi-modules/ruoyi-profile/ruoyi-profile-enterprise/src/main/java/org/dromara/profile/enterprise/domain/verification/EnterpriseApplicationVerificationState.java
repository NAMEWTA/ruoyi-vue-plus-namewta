package org.dromara.profile.enterprise.domain.verification;

import java.util.Objects;
import java.util.Set;

public record EnterpriseApplicationVerificationState(
    long applicationId,
    long submissionId,
    String providerCode,
    String status
) {
    private static final Set<String> TERMINAL_STATUSES = Set.of("FINISH", "INVALID", "TERMINATION");

    public EnterpriseApplicationVerificationState {
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

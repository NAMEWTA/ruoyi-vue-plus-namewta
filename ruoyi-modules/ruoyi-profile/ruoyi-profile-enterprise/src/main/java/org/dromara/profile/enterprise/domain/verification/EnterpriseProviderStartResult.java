package org.dromara.profile.enterprise.domain.verification;

import java.time.Instant;
import java.util.Objects;

public record EnterpriseProviderStartResult(
    String providerRequestId,
    EnterpriseProviderAttemptStatus status,
    String normalizedResultJson,
    String providerEvidenceJson,
    String errorCode,
    Instant completedAt
) {
    public EnterpriseProviderStartResult {
        Objects.requireNonNull(status, "status");
        if ((status == EnterpriseProviderAttemptStatus.PENDING) != (completedAt == null)) {
            throw new IllegalArgumentException("Only pending enterprise attempts may omit completedAt");
        }
    }

    public static EnterpriseProviderStartResult pending() {
        return new EnterpriseProviderStartResult(
            null, EnterpriseProviderAttemptStatus.PENDING, null, null, null, null);
    }
}

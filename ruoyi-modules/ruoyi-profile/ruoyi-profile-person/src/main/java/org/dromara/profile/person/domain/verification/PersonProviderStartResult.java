package org.dromara.profile.person.domain.verification;

import java.time.Instant;
import java.util.Objects;

public record PersonProviderStartResult(
    String providerRequestId,
    PersonProviderAttemptStatus status,
    String normalizedResultJson,
    String providerEvidenceJson,
    String errorCode,
    Instant completedAt
) {
    public PersonProviderStartResult {
        Objects.requireNonNull(status, "status");
        if ((status == PersonProviderAttemptStatus.PENDING) != (completedAt == null)) {
            throw new IllegalArgumentException("Only pending person attempts may omit completedAt");
        }
    }

    public static PersonProviderStartResult pending() {
        return new PersonProviderStartResult(
            null, PersonProviderAttemptStatus.PENDING, null, null, null, null);
    }
}

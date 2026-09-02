package org.dromara.profile.person.domain.verification;

import java.time.Instant;
import java.util.Objects;

public record PersonVerifiedCallback(
    String providerRequestId,
    String callbackDigest,
    PersonProviderAttemptStatus status,
    String normalizedResultJson,
    String providerEvidenceJson,
    String errorCode,
    Instant completedAt
) {
    public PersonVerifiedCallback {
        Objects.requireNonNull(providerRequestId, "providerRequestId");
        Objects.requireNonNull(callbackDigest, "callbackDigest");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(completedAt, "completedAt");
    }
}

package org.dromara.profile.enterprise.domain.verification;

import java.time.Instant;
import java.util.Objects;

public record EnterpriseVerifiedCallback(
    String providerRequestId,
    String callbackDigest,
    EnterpriseProviderAttemptStatus status,
    String normalizedResultJson,
    String providerEvidenceJson,
    String errorCode,
    Instant completedAt
) {
    public EnterpriseVerifiedCallback {
        Objects.requireNonNull(providerRequestId, "providerRequestId");
        Objects.requireNonNull(callbackDigest, "callbackDigest");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(completedAt, "completedAt");
    }
}

package org.dromara.profile.enterprise.domain.verification;

import java.time.Instant;
import java.util.Objects;

public record EnterpriseVerificationSecurityAudit(
    Long applicationId,
    EnterpriseVerificationFailureCategory category,
    Instant occurredAt
) {
    public EnterpriseVerificationSecurityAudit {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}

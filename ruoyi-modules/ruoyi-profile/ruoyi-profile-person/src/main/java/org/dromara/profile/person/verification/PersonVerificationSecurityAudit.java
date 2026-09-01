package org.dromara.profile.person.verification;

import java.time.Instant;
import java.util.Objects;

public record PersonVerificationSecurityAudit(
    Long applicationId,
    PersonVerificationFailureCategory category,
    Instant occurredAt
) {
    public PersonVerificationSecurityAudit {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}

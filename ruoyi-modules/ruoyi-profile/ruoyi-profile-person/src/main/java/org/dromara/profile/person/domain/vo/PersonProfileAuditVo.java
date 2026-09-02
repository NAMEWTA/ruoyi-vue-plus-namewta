package org.dromara.profile.person.domain.vo;

import java.time.Instant;

public record PersonProfileAuditVo(
    long auditId,
    String operationType,
    long operatorUserId,
    String capability,
    String reason,
    String beforeStatus,
    String afterStatus,
    String result,
    String failureCategory,
    Instant occurredTime
) {
}

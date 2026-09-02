package org.dromara.profile.enterprise.domain.vo;

import java.time.Instant;

public record EnterpriseProfileAuditVo(
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

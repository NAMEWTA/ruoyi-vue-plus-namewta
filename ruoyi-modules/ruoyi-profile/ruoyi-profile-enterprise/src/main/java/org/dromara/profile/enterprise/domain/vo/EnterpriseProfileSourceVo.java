package org.dromara.profile.enterprise.domain.vo;

import java.time.Instant;

public record EnterpriseProfileSourceVo(
    long sourceId,
    String sourceType,
    long operatorUserId,
    String reason,
    String fieldSnapshotJson,
    Instant occurredTime
) {
}

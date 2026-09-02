package org.dromara.profile.person.domain.vo;

import java.time.Instant;

public record PersonProfileSourceVo(
    long sourceId,
    String sourceType,
    long operatorUserId,
    String reason,
    String fieldSnapshotJson,
    Instant occurredTime
) {
}

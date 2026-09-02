package org.dromara.profile.person.domain.vo;

import java.time.Instant;

public record PersonProfileBindingVo(
    long bindingId,
    long userId,
    String status,
    int bindingVersion,
    String sourceType,
    Long sourceId,
    Instant boundTime,
    Instant unboundTime
) {
}

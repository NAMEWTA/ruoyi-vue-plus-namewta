package org.dromara.profile.enterprise.domain.vo;

import java.time.Instant;

public record EnterpriseProfileBindingVo(
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

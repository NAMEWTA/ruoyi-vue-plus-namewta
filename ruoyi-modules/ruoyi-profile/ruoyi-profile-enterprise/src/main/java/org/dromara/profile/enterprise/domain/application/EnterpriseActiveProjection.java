package org.dromara.profile.enterprise.domain.application;

import java.time.Instant;

public record EnterpriseActiveProjection(long userId, long enterpriseProfileId, Instant verifiedAt) {
}

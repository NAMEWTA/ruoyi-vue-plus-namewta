package org.dromara.profile.person.application;

import java.time.Instant;

public record PersonActiveProjection(long userId, long personProfileId, Instant verifiedAt) {
}

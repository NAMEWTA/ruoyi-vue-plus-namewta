package org.dromara.profile.enterprise.domain.application;

public record EnterpriseDraftUpdate(EnterpriseIdentityFields fields, Long targetProfileId, int expectedVersion) {
}

package org.dromara.profile.enterprise.application;

public record EnterpriseDraftUpdate(EnterpriseIdentityFields fields, Long targetProfileId, int expectedVersion) {
}

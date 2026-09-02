package org.dromara.profile.enterprise.domain.application;

public record EnterprisePublication(
    long enterpriseProfileId,
    long enterpriseVersionId,
    long enterpriseBindingId,
    boolean successorProfile
) {
}

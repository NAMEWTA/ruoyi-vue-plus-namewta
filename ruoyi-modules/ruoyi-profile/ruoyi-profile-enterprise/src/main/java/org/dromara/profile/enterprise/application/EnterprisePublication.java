package org.dromara.profile.enterprise.application;

public record EnterprisePublication(
    long enterpriseProfileId,
    long enterpriseVersionId,
    long enterpriseBindingId,
    boolean successorProfile
) {
}

package org.dromara.profile.enterprise.domain.bo;

public record EnterpriseAdminQueryBo(
    String enterpriseName,
    String unifiedCreditCode,
    String status,
    int pageNum,
    int pageSize
) {
}

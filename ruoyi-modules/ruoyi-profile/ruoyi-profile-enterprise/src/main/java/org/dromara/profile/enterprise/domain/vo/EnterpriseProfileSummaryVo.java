package org.dromara.profile.enterprise.domain.vo;

import java.time.Instant;

public record EnterpriseProfileSummaryVo(
    long profileId,
    Long previousProfileId,
    String enterpriseName,
    String unifiedCreditCode,
    String enterpriseType,
    String legalRepresentativeName,
    String status,
    Long bindingUserId,
    String bindingStatus,
    Instant createTime
) {
}

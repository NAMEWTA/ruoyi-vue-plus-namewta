package org.dromara.profile.enterprise.domain.application;

import java.time.Instant;

public record EnterpriseApplication(
    long enterpriseApplicationId,
    long applicantUserId,
    Long targetProfileId,
    String status,
    EnterpriseIdentityFields fields,
    String providerCode,
    int submissionSeq,
    int decisionVersion,
    int version,
    Instant submittedTime,
    Instant finishedTime
) {

    public boolean editable() {
        return "DRAFT".equals(status) || "BACK".equals(status) || "CANCEL".equals(status);
    }

    public boolean terminal() {
        return "FINISH".equals(status) || "INVALID".equals(status) || "TERMINATION".equals(status);
    }
}

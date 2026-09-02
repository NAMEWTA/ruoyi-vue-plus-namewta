package org.dromara.profile.enterprise.domain.application;

import java.time.Instant;

public record EnterpriseSubmission(
    long enterpriseSubmissionId,
    long enterpriseApplicationId,
    int submissionSeq,
    long applicantUserId,
    EnterpriseIdentityFields fields,
    String providerCode,
    Instant submittedTime
) {
}

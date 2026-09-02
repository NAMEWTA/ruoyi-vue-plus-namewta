package org.dromara.profile.person.domain.application;

import java.time.Instant;

public record PersonSubmission(
    long personSubmissionId,
    long personApplicationId,
    int submissionSeq,
    long applicantUserId,
    PersonIdentityFields fields,
    String providerCode,
    Instant submittedTime
) {
}

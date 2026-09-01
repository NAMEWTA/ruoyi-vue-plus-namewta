package org.dromara.profile.person.application;

import java.time.Instant;

public record PersonApplication(
    long personApplicationId,
    long applicantUserId,
    Long targetProfileId,
    String status,
    PersonIdentityFields fields,
    String providerCode,
    int submissionSeq,
    boolean rebindIntent,
    Long expectedBindingId,
    Integer expectedBindingVersion,
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

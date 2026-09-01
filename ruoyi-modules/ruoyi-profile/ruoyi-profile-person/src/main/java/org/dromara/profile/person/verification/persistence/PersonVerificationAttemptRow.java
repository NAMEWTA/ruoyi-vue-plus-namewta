package org.dromara.profile.person.verification.persistence;

import lombok.Data;

import java.time.Instant;

@Data
public class PersonVerificationAttemptRow {

    private Long verificationAttemptId;
    private Long applicationId;
    private Long submissionId;
    private String providerCode;
    private String providerRequestId;
    private String requestFingerprint;
    private Integer attemptNo;
    private String status;
    private String normalizedResultJson;
    private String providerEvidenceJson;
    private String errorCode;
    private Instant completedTime;
}

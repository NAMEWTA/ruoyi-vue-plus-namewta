package org.dromara.profile.person.verification.persistence;

import lombok.Data;

@Data
public class PersonVerificationApplicationRow {

    private Long applicationId;
    private Long submissionId;
    private String providerCode;
    private String status;
}

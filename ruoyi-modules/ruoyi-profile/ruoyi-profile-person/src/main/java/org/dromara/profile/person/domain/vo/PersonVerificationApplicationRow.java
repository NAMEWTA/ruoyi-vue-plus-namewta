package org.dromara.profile.person.domain.vo;

import lombok.Data;

@Data
public class PersonVerificationApplicationRow {

    private Long applicationId;
    private Long submissionId;
    private String providerCode;
    private String status;
}

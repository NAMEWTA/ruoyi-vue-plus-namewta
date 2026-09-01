package org.dromara.profile.enterprise.verification.persistence;

import lombok.Data;

@Data
public class EnterpriseVerificationApplicationRow {

    private Long applicationId;
    private Long submissionId;
    private String providerCode;
    private String status;
}

package org.dromara.profile.enterprise.domain.vo;

import lombok.Data;

@Data
public class EnterpriseDocumentTypeRow {

    private String documentTypeCode;
    private String numberPattern;
    private String validityRequired;
}

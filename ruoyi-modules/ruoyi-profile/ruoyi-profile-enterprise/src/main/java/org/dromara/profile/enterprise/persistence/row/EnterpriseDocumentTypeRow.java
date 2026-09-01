package org.dromara.profile.enterprise.persistence.row;

import lombok.Data;

@Data
public class EnterpriseDocumentTypeRow {

    private String documentTypeCode;
    private String numberPattern;
    private String validityRequired;
}

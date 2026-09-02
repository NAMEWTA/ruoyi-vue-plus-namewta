package org.dromara.profile.person.domain.vo;

import lombok.Data;

@Data
public class PersonDocumentTypeRow {

    private String documentTypeCode;
    private String numberPattern;
    private String validityRequired;
}

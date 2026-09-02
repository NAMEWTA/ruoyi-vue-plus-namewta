package org.dromara.profile.person.domain.vo;

import lombok.Data;

import java.time.LocalDate;

@Data
public class PersonProfileRow {

    private Long personProfileId;
    private Long previousProfileId;
    private Long currentVersionId;
    private String fullName;
    private String documentTypeCode;
    private String documentNumber;
    private String identityKey;
    private String gender;
    private LocalDate birthDate;
    private LocalDate validFrom;
    private LocalDate validUntil;
    private String status;
    private Integer version;
}

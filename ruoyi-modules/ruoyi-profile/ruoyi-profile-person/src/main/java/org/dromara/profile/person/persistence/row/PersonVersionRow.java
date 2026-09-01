package org.dromara.profile.person.persistence.row;

import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

@Data
public class PersonVersionRow {

    private Long personVersionId;
    private Long personProfileId;
    private Integer versionNo;
    private String sourceType;
    private Long sourceId;
    private String fullName;
    private String documentTypeCode;
    private String documentNumber;
    private String identityKey;
    private String gender;
    private LocalDate birthDate;
    private LocalDate validFrom;
    private LocalDate validUntil;
    private String status;
    private Instant publishedTime;
}

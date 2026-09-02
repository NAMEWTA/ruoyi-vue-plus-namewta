package org.dromara.profile.person.domain.vo;

import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

@Data
public class PersonSubmissionRow {

    private Long personSubmissionId;
    private Long personApplicationId;
    private Integer submissionSeq;
    private Long applicantUserId;
    private String fullName;
    private String documentTypeCode;
    private String documentNumber;
    private String identityKey;
    private String gender;
    private LocalDate birthDate;
    private LocalDate validFrom;
    private LocalDate validUntil;
    private String providerCode;
    private String rebindIntent;
    private Long targetProfileId;
    private Long expectedBindingId;
    private Integer expectedBindingVersion;
    private String fieldSnapshotJson;
    private Instant submittedTime;
}

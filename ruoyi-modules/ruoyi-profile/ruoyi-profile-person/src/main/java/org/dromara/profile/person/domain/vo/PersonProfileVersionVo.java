package org.dromara.profile.person.domain.vo;

import java.time.Instant;
import java.time.LocalDate;

public record PersonProfileVersionVo(
    long versionId,
    int versionNo,
    String sourceType,
    long sourceId,
    String fullName,
    String documentTypeCode,
    String documentNumber,
    String gender,
    LocalDate birthDate,
    LocalDate validFrom,
    LocalDate validUntil,
    String status,
    Instant publishedTime
) {
}

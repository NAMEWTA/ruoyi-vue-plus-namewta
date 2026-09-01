package org.dromara.profile.person.application;

import java.time.LocalDate;

public record PersonDraftCommand(
    String fullName,
    String documentTypeCode,
    String documentNumber,
    String gender,
    LocalDate birthDate,
    LocalDate validFrom,
    LocalDate validUntil,
    int expectedVersion
) {
}

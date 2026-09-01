package org.dromara.profile.person.application;

import java.time.LocalDate;
import java.util.Locale;

public record PersonIdentityFields(
    String fullName,
    String documentTypeCode,
    String documentNumber,
    String identityKey,
    String gender,
    LocalDate birthDate,
    LocalDate validFrom,
    LocalDate validUntil
) {

    public static PersonIdentityFields normalize(PersonDraftCommand command) {
        if (command == null) {
            throw new PersonApplicationException("PERSON_DRAFT_REQUIRED");
        }
        String documentType = upper(command.documentTypeCode());
        String documentNumber = upper(command.documentNumber());
        String identityKey = documentType == null || documentNumber == null
            ? null : documentType + ":" + documentNumber;
        return new PersonIdentityFields(text(command.fullName()), documentType, documentNumber, identityKey,
            upper(command.gender()), command.birthDate(), command.validFrom(), command.validUntil());
    }

    private static String upper(String value) {
        String normalized = text(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private static String text(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}

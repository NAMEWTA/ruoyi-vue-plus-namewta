package org.dromara.profile.person.domain.vo;

import org.dromara.profile.person.domain.application.PersonApplication;
import org.dromara.profile.person.domain.application.PersonIdentityFields;

import java.time.Instant;
import java.time.LocalDate;

public record PersonApplicationVo(
    Long personApplicationId,
    String status,
    String fullName,
    String documentTypeCode,
    String documentNumber,
    String gender,
    LocalDate birthDate,
    LocalDate validFrom,
    LocalDate validUntil,
    String providerCode,
    int snapshotVersion,
    int version,
    Instant submittedTime,
    Instant finishedTime
) {

    public static PersonApplicationVo from(PersonApplication application) {
        PersonIdentityFields fields = application.fields();
        return new PersonApplicationVo(application.personApplicationId(), application.status(),
            fields.fullName(), fields.documentTypeCode(), fields.documentNumber(), fields.gender(),
            fields.birthDate(), fields.validFrom(), fields.validUntil(), application.providerCode(),
            application.submissionSeq(), application.version(), application.submittedTime(),
            application.finishedTime());
    }
}

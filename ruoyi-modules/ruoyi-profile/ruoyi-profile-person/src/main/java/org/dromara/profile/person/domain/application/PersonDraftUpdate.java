package org.dromara.profile.person.domain.application;

public record PersonDraftUpdate(PersonIdentityFields fields, Long targetProfileId, int expectedVersion) {
}

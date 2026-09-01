package org.dromara.profile.person.application;

public record PersonDraftUpdate(PersonIdentityFields fields, Long targetProfileId, int expectedVersion) {
}

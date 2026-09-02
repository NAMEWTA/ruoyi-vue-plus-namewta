package org.dromara.profile.person.domain.application;

import org.dromara.profile.person.event.PersonReboundEvent;

public record PersonRebindPublication(long personSubmissionId, long personVersionId, PersonReboundEvent event) {
}

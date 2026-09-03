package org.dromara.profile.person.domain.application;

import org.dromara.profile.person.event.PersonReboundEvent;

/** PersonRebindPublication 应用层领域模型。 */
public record PersonRebindPublication(long personSubmissionId, long personVersionId, PersonReboundEvent event) {
}

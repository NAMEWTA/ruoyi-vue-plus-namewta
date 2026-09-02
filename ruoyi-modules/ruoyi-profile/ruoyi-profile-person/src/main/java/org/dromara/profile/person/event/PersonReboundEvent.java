package org.dromara.profile.person.event;

public record PersonReboundEvent(
    long personProfileId,
    long personApplicationId,
    long oldUserId
) {
}

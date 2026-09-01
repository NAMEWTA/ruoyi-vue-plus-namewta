package org.dromara.profile.person.rebind;

public record PersonReboundEvent(
    long personProfileId,
    long personApplicationId,
    long oldUserId
) {
}

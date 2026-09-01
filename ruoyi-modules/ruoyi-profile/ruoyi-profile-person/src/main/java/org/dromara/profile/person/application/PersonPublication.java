package org.dromara.profile.person.application;

public record PersonPublication(
    long personProfileId,
    long personVersionId,
    long personBindingId,
    boolean successorProfile
) {
}

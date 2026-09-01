package org.dromara.profile.person.verification;

import java.util.Objects;

public class PersonVerificationException extends RuntimeException {

    private final PersonVerificationFailureCategory category;

    public PersonVerificationException(PersonVerificationFailureCategory category, String message) {
        super(message);
        this.category = Objects.requireNonNull(category, "category");
    }

    public PersonVerificationException(PersonVerificationFailureCategory category, String message, Throwable cause) {
        super(message, cause);
        this.category = Objects.requireNonNull(category, "category");
    }

    public PersonVerificationFailureCategory category() {
        return category;
    }
}

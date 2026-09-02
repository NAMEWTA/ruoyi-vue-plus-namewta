package org.dromara.profile.person.domain.exception;

public class PersonRebindException extends RuntimeException {

    public PersonRebindException(String category) {
        super(category);
    }

    public PersonRebindException(String category, Throwable cause) {
        super(category, cause);
    }
}

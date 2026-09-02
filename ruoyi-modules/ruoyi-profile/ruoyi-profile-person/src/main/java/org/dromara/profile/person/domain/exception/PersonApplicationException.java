package org.dromara.profile.person.domain.exception;

public class PersonApplicationException extends RuntimeException {

    public PersonApplicationException(String category) {
        super(category);
    }

    public PersonApplicationException(String category, Throwable cause) {
        super(category, cause);
    }
}

package org.dromara.profile.person.application;

public class PersonApplicationException extends RuntimeException {

    public PersonApplicationException(String category) {
        super(category);
    }

    public PersonApplicationException(String category, Throwable cause) {
        super(category, cause);
    }
}

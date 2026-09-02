package org.dromara.profile.person.domain.exception;

public class PersonAdminException extends RuntimeException {

    public PersonAdminException(String category) {
        super(category);
    }

    public PersonAdminException(String category, Throwable cause) {
        super(category, cause);
    }
}

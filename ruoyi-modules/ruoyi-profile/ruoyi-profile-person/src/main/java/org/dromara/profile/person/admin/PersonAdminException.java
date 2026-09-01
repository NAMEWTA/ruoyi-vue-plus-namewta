package org.dromara.profile.person.admin;

public class PersonAdminException extends RuntimeException {

    public PersonAdminException(String category) {
        super(category);
    }

    public PersonAdminException(String category, Throwable cause) {
        super(category, cause);
    }
}

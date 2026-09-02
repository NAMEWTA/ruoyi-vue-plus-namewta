package org.dromara.profile.enterprise.domain.exception;

public class EnterpriseApplicationException extends RuntimeException {

    public EnterpriseApplicationException(String category) {
        super(category);
    }

    public EnterpriseApplicationException(String category, Throwable cause) {
        super(category, cause);
    }
}

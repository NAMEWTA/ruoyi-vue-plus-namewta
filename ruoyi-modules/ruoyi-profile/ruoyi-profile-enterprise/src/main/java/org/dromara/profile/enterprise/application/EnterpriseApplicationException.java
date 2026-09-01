package org.dromara.profile.enterprise.application;

public class EnterpriseApplicationException extends RuntimeException {

    public EnterpriseApplicationException(String category) {
        super(category);
    }

    public EnterpriseApplicationException(String category, Throwable cause) {
        super(category, cause);
    }
}

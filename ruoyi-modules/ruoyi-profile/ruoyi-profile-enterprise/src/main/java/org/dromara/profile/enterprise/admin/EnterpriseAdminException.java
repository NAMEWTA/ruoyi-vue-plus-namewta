package org.dromara.profile.enterprise.admin;

public class EnterpriseAdminException extends RuntimeException {

    public EnterpriseAdminException(String category) {
        super(category);
    }

    public EnterpriseAdminException(String category, Throwable cause) {
        super(category, cause);
    }
}

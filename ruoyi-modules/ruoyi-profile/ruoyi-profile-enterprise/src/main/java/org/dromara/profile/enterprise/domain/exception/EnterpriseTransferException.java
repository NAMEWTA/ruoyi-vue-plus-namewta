package org.dromara.profile.enterprise.domain.exception;

public class EnterpriseTransferException extends RuntimeException {

    public EnterpriseTransferException(String category) {
        super(category);
    }

    public EnterpriseTransferException(String category, Throwable cause) {
        super(category, cause);
    }
}

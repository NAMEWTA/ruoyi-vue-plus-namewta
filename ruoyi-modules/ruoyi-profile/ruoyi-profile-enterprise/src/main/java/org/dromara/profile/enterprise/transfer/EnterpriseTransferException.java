package org.dromara.profile.enterprise.transfer;

public class EnterpriseTransferException extends RuntimeException {

    public EnterpriseTransferException(String category) {
        super(category);
    }

    public EnterpriseTransferException(String category, Throwable cause) {
        super(category, cause);
    }
}

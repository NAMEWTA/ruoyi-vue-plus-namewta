package org.dromara.profile.enterprise.verification;

import java.util.Objects;

public class EnterpriseVerificationException extends RuntimeException {

    private final EnterpriseVerificationFailureCategory category;

    public EnterpriseVerificationException(EnterpriseVerificationFailureCategory category, String message) {
        super(message);
        this.category = Objects.requireNonNull(category, "category");
    }

    public EnterpriseVerificationException(EnterpriseVerificationFailureCategory category, String message,
                                           Throwable cause) {
        super(message, cause);
        this.category = Objects.requireNonNull(category, "category");
    }

    public EnterpriseVerificationFailureCategory category() {
        return category;
    }
}

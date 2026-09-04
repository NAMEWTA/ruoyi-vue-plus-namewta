package org.dromara.third.service;

import org.dromara.third.api.ThirdPartyFailureCategory;

public class ThirdRejectedException extends RuntimeException {
    private final ThirdPartyFailureCategory category;

    public ThirdRejectedException(ThirdPartyFailureCategory category, String message) {
        super(message);
        this.category = category;
    }

    public ThirdPartyFailureCategory category() {
        return category;
    }
}

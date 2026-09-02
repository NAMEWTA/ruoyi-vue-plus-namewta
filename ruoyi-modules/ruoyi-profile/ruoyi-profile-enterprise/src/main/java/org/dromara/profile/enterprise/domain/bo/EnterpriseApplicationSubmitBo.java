package org.dromara.profile.enterprise.domain.bo;

import jakarta.validation.constraints.PositiveOrZero;

public record EnterpriseApplicationSubmitBo(@PositiveOrZero int expectedVersion) {
}

package org.dromara.profile.enterprise.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EnterpriseApplicationProbeBo(@NotBlank @Size(max = 64) String unifiedCreditCode) {
}

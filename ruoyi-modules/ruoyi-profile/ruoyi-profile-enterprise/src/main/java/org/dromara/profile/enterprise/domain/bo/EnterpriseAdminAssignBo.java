package org.dromara.profile.enterprise.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record EnterpriseAdminAssignBo(@Positive Long userId,
                                      @NotBlank @Size(max = 500) String reason) {
}

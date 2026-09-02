package org.dromara.profile.enterprise.domain.bo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record EnterpriseAdminReviseBo(@Valid @NotNull EnterpriseAdminIdentityBo identity,
                                      @NotBlank @Size(max = 500) String reason,
                                      @PositiveOrZero int expectedVersion) {
}

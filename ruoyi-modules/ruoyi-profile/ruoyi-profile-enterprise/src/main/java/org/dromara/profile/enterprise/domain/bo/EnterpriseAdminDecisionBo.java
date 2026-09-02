package org.dromara.profile.enterprise.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EnterpriseAdminDecisionBo(@NotBlank @Size(max = 32) String decision,
                                        @NotBlank @Size(max = 500) String reason) {
}

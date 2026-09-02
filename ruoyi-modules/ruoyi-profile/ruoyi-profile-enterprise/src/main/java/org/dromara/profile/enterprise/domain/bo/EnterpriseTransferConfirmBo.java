package org.dromara.profile.enterprise.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EnterpriseTransferConfirmBo(
    @NotBlank @Size(max = 64) String challengeId,
    @NotBlank @Pattern(regexp = "\\d{6}") String code
) {
}

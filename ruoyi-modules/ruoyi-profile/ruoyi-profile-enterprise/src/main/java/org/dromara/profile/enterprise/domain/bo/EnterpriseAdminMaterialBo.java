package org.dromara.profile.enterprise.domain.bo;

import jakarta.validation.constraints.Positive;

public record EnterpriseAdminMaterialBo(@Positive Long ossId, @Positive Long materialNodeId) {
}

package org.dromara.profile.person.domain.bo;

import java.io.Serial;
import java.io.Serializable;
import jakarta.validation.constraints.Positive;

public record PersonAdminMaterialBo(@Positive Long ossId, @Positive Long materialNodeId) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}

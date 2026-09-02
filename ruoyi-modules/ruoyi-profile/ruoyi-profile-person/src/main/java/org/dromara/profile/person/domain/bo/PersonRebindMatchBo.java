package org.dromara.profile.person.domain.bo;

import java.io.Serial;
import java.io.Serializable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record PersonRebindMatchBo(@Valid @NotNull PersonRebindIdentityBo identity) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}

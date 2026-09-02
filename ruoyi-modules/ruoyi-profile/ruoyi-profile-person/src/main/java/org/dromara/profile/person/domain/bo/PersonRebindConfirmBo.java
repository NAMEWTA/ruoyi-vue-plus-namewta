package org.dromara.profile.person.domain.bo;

import java.io.Serial;
import java.io.Serializable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record PersonRebindConfirmBo(@Valid @NotNull PersonRebindIdentityBo identity,
                                    @PositiveOrZero int expectedVersion) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}

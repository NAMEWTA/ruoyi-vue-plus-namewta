package org.dromara.profile.person.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class PersonActiveIdentityMatchRow implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long userId;
    private Long personProfileId;
}

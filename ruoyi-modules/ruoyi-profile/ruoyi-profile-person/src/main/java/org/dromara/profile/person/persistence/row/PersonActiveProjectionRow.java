package org.dromara.profile.person.persistence.row;

import lombok.Data;

import java.time.Instant;

@Data
public class PersonActiveProjectionRow {

    private Long userId;
    private Long personProfileId;
    private Instant verifiedAt;
}

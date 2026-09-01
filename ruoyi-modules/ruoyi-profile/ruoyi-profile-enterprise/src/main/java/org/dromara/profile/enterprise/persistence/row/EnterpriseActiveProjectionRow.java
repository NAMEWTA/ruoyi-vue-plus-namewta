package org.dromara.profile.enterprise.persistence.row;

import lombok.Data;

import java.time.Instant;

@Data
public class EnterpriseActiveProjectionRow {

    private Long userId;
    private Long enterpriseProfileId;
    private Instant verifiedAt;
}

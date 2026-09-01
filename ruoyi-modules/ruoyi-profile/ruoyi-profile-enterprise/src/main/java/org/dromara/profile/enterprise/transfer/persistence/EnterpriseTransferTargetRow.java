package org.dromara.profile.enterprise.transfer.persistence;

import lombok.Data;

@Data
public class EnterpriseTransferTargetRow {

    private Long userId;
    private Long personProfileId;
    private String phone;
}

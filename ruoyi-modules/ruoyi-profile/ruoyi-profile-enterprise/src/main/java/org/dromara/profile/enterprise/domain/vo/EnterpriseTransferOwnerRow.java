package org.dromara.profile.enterprise.domain.vo;

import lombok.Data;

@Data
public class EnterpriseTransferOwnerRow {

    private Long bindingId;
    private Long profileId;
    private Long userId;
    private Integer bindingVersion;
}

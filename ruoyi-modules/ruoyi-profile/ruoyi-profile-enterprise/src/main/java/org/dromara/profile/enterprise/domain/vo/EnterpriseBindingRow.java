package org.dromara.profile.enterprise.domain.vo;

import lombok.Data;

import java.time.Instant;

@Data
public class EnterpriseBindingRow {

    private Long enterpriseBindingId;
    private Long enterpriseProfileId;
    private Long userId;
    private String status;
    private Integer bindingVersion;
    private String sourceType;
    private Long sourceId;
    private Instant boundTime;
}

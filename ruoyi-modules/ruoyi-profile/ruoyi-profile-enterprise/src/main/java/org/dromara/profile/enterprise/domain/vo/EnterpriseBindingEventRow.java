package org.dromara.profile.enterprise.domain.vo;

import lombok.Data;

import java.time.Instant;

@Data
public class EnterpriseBindingEventRow {

    private Long enterpriseBindingEventId;
    private Long enterpriseBindingId;
    private Long enterpriseProfileId;
    private Long userId;
    private String eventType;
    private Integer bindingVersion;
    private String sourceType;
    private Long sourceId;
    private String reason;
    private Instant occurredTime;
}

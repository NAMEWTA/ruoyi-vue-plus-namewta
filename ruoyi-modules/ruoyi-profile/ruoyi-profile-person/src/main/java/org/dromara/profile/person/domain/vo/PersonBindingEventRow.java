package org.dromara.profile.person.domain.vo;

import lombok.Data;

import java.time.Instant;

@Data
public class PersonBindingEventRow {

    private Long personBindingEventId;
    private Long personBindingId;
    private Long personProfileId;
    private Long userId;
    private String eventType;
    private Integer bindingVersion;
    private String sourceType;
    private Long sourceId;
    private String reason;
    private Instant occurredTime;
}

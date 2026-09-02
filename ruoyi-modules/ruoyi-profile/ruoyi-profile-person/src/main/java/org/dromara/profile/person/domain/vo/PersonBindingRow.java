package org.dromara.profile.person.domain.vo;

import lombok.Data;

import java.time.Instant;

@Data
public class PersonBindingRow {

    private Long personBindingId;
    private Long personProfileId;
    private Long userId;
    private String status;
    private Integer bindingVersion;
    private String sourceType;
    private Long sourceId;
    private Instant boundTime;
}

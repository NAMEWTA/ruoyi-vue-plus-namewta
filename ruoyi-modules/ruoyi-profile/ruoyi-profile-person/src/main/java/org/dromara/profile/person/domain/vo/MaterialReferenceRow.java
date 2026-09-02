package org.dromara.profile.person.domain.vo;

import java.time.Instant;

public record MaterialReferenceRow(Long materialRefId, String ownerType, Long ownerId, String profileType,
                                   Long ossId, Long materialNodeId, String materialTagCode,
                                   String materialTagName, String fileName, Long fileSize,
                                   String fileExtension, String mimeType, String status, String immutableFlag,
                                   Instant attachedTime, Instant detachedTime, Integer version) {
}

package org.dromara.profile.person.domain.vo;

import org.dromara.profile.api.material.ProfileMaterialPort.MaterialReferenceView;

import java.time.Instant;
import java.util.List;

public record PersonReviewContextVo(
    long applicationId,
    long applicantUserId,
    String status,
    int submissionSeq,
    int decisionVersion,
    int version,
    long submissionId,
    String fieldSnapshotJson,
    Instant submittedTime,
    List<MaterialReferenceView> materials
) {
}

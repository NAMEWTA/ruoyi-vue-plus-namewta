package org.dromara.profile.enterprise.domain.vo;

import org.dromara.profile.api.material.ProfileMaterialPort.MaterialReferenceView;

import java.time.Instant;
import java.util.List;

/** EnterpriseReviewContextVo 对外返回模型。 */
public record EnterpriseReviewContextVo(
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

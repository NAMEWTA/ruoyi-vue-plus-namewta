package org.dromara.profile.enterprise.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface EnterpriseApplicationRepository {

    Optional<EnterpriseApplication> findOpenByUserId(long userId);

    EnterpriseApplication lockOpenByUserId(long userId);

    EnterpriseApplication lockById(long applicationId);

    Optional<DocumentTypeRule> findDocumentType(String documentTypeCode);

    Long findActiveProfileIdByIdentity(String identityKey);

    Long findEffectiveProfileIdByUser(long userId);

    String probeStatus(String identityKey);

    EnterpriseApplication saveDraft(long userId, String providerCode, EnterpriseDraftUpdate update);

    void requireSubmissionAllowed(long userId, Long targetProfileId, String identityKey);

    EnterpriseSubmission insertSubmission(EnterpriseApplication application, EnterpriseIdentityFields fields,
                                      int submissionSeq, Instant submittedTime);

    EnterpriseSubmission requireSubmission(long applicationId, int submissionSeq);

    EnterpriseApplication markWaiting(long applicationId, int submissionSeq, int expectedVersion,
                                  Instant submittedTime);

    EnterprisePublication publishApproved(long applicationId, int snapshotVersion, Instant finishedTime);

    void updateWorkflowStatus(long applicationId, int snapshotVersion, String status,
                              int expectedVersion, Instant occurredTime);

    List<EnterpriseActiveProjection> findActiveProjections(Set<Long> userIds);
}

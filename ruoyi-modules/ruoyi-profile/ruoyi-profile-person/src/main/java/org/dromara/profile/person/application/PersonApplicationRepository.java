package org.dromara.profile.person.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PersonApplicationRepository {

    Optional<PersonApplication> findOpenByUserId(long userId);

    PersonApplication lockOpenByUserId(long userId);

    PersonApplication lockById(long applicationId);

    Optional<DocumentTypeRule> findDocumentType(String documentTypeCode);

    Long findActiveProfileIdByIdentity(String identityKey);

    Long findEffectiveProfileIdByUser(long userId);

    PersonApplication saveDraft(long userId, String providerCode, PersonDraftUpdate update);

    void requireSubmissionAllowed(long userId, Long targetProfileId, String identityKey);

    PersonSubmission insertSubmission(PersonApplication application, PersonIdentityFields fields,
                                      int submissionSeq, Instant submittedTime);

    PersonSubmission requireSubmission(long applicationId, int submissionSeq);

    PersonApplication markWaiting(long applicationId, int submissionSeq, int expectedVersion,
                                  Instant submittedTime);

    PersonPublication publishApproved(long applicationId, int snapshotVersion, Instant finishedTime);

    void updateWorkflowStatus(long applicationId, int snapshotVersion, String status,
                              int expectedVersion, Instant occurredTime);

    List<PersonActiveProjection> findActiveProjections(Set<Long> userIds);
}

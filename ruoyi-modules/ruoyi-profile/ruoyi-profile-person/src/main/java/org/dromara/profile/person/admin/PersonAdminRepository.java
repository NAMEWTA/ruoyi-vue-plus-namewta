package org.dromara.profile.person.admin;

import org.dromara.common.core.domain.PageResult;
import org.dromara.profile.person.admin.PersonAdminContracts.*;
import org.dromara.profile.person.application.PersonIdentityFields;

import java.time.Instant;

public interface PersonAdminRepository {

    PageResult<Summary> page(Query query);

    Detail detail(long profileId);

    ReviewData review(long applicationId);

    DecisionState beginDecision(long applicationId, String decision, long operatorId, String reason, Instant now);

    void resumeForApproval(DecisionState state, long operatorId);

    void finalizeApproved(DecisionState state, long profileId, long versionId, long operatorId,
                          String reason, Instant now);

    void finalizeRejected(DecisionState state, long operatorId, String reason, Instant now);

    CreateState beginCreate(PersonIdentityFields fields, long operatorId, String reason, Instant now);

    Result completeCreate(CreateState state, Long bindUserId, long operatorId, String reason, Instant now);

    ReviseState beginRevise(long profileId, PersonIdentityFields fields, int expectedVersion,
                            long operatorId, String reason, Instant now);

    Result completeRevise(ReviseState state, long operatorId, String reason, Instant now);

    Result manageBinding(long profileId, String action, int expectedBindingVersion,
                         long operatorId, String reason, Instant now);

    Result assign(long profileId, long userId, long operatorId, String reason, Instant now);

    Result revoke(long profileId, int expectedVersion, long operatorId, String reason, Instant now);

    boolean hasEffectiveBinding(long userId);

    record ReviewData(long applicationId, long applicantUserId, String status, int submissionSeq,
                      int decisionVersion, int version, long submissionId, String fieldSnapshotJson,
                      Instant submittedTime) {
    }

    record DecisionState(long applicationId, long submissionId, int snapshotVersion, int decisionVersion) {
    }

    record CreateState(long profileId, long sourceId, PersonIdentityFields fields) {
    }

    record ReviseState(long profileId, long sourceId, int nextVersionNo, int profileVersion,
                       PersonIdentityFields fields) {
    }
}

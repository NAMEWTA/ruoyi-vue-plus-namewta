package org.dromara.profile.person.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.profile.person.domain.ProfilePersonApplication;
import org.apache.ibatis.annotations.Param;
import org.dromara.profile.person.domain.vo.PersonActiveProjectionRow;
import org.dromara.profile.person.domain.vo.PersonActiveIdentityMatchRow;
import org.dromara.profile.person.domain.vo.PersonApplicationRow;
import org.dromara.profile.person.domain.vo.PersonBindingEventRow;
import org.dromara.profile.person.domain.vo.PersonBindingRow;
import org.dromara.profile.person.domain.vo.PersonDocumentTypeRow;
import org.dromara.profile.person.domain.vo.PersonProfileRow;
import org.dromara.profile.person.domain.vo.PersonSubmissionRow;
import org.dromara.profile.person.domain.vo.PersonVersionRow;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface PersonApplicationMapper extends BaseMapperPlus<ProfilePersonApplication, ProfilePersonApplication> {

    List<PersonActiveIdentityMatchRow> selectActiveIdentityMatches(
        @Param("fullName") String fullName,
        @Param("documentLastFour") String documentLastFour);

    PersonActiveIdentityMatchRow lockActiveIdentityMatch(
        @Param("userId") long userId,
        @Param("personProfileId") long personProfileId,
        @Param("fullName") String fullName,
        @Param("documentLastFour") String documentLastFour);

    Long lockMaterialWorkingOwner(@Param("ownerId") long ownerId);

    Long lockMaterialSubmissionOwner(@Param("ownerId") long ownerId);

    Long lockMaterialImmutableOwner(@Param("ownerType") String ownerType,
                                    @Param("ownerId") long ownerId);

    long countEditableMaterialWorkingOwner(@Param("ownerId") long ownerId);

    long countMaterialSnapshotRelationship(@Param("sourceType") String sourceType,
                                           @Param("sourceId") long sourceId,
                                           @Param("targetType") String targetType,
                                           @Param("targetId") long targetId);

    PersonApplicationRow selectOpenByUserId(@Param("userId") long userId);

    PersonApplicationRow lockOpenByUserId(@Param("userId") long userId);

    PersonApplicationRow lockApplicationById(@Param("applicationId") long applicationId);

    PersonDocumentTypeRow selectDocumentType(@Param("code") String documentTypeCode);

    Long selectActiveProfileIdByIdentity(@Param("identityKey") String identityKey);

    Long selectEffectiveProfileIdByUser(@Param("userId") long userId);

    int insertApplication(PersonApplicationRow row);

    int updateDraft(PersonApplicationRow row);

    int insertSubmission(PersonSubmissionRow row);

    PersonSubmissionRow selectSubmission(@Param("applicationId") long applicationId,
                                         @Param("submissionSeq") int submissionSeq);

    int markWaiting(@Param("applicationId") long applicationId,
                    @Param("submissionSeq") int submissionSeq,
                    @Param("expectedVersion") int expectedVersion,
                    @Param("submittedTime") Instant submittedTime);

    PersonProfileRow lockActiveProfileByIdentity(@Param("identityKey") String identityKey);

    PersonProfileRow lockActiveProfileById(@Param("profileId") long profileId);

    int updateIdentityGuard(@Param("profileId") long profileId,
                            @Param("identityKey") String identityKey);

    PersonProfileRow lockLatestRevokedProfileByIdentity(@Param("identityKey") String identityKey);

    int insertIdentityGuard(@Param("guardId") long guardId,
                            @Param("identityKey") String identityKey,
                            @Param("profileId") long profileId);

    int insertProfile(PersonProfileRow row);

    PersonVersionRow selectCurrentVersionForUpdate(@Param("profileId") long profileId);

    int supersedeVersion(@Param("versionId") long personVersionId);

    int insertVersion(PersonVersionRow row);

    int updateProfile(PersonProfileRow row);

    PersonBindingRow lockEffectiveBindingByUser(@Param("userId") long userId);

    PersonBindingRow lockEffectiveBindingByProfile(@Param("profileId") long personProfileId);

    int insertBinding(PersonBindingRow row);

    int insertBindingEvent(PersonBindingEventRow row);

    int finishApplication(@Param("applicationId") long applicationId,
                          @Param("snapshotVersion") int snapshotVersion,
                          @Param("decisionVersion") int decisionVersion,
                          @Param("expectedVersion") int expectedVersion,
                          @Param("finishedTime") Instant finishedTime);

    int updateWorkflowStatus(@Param("applicationId") long applicationId,
                             @Param("snapshotVersion") int snapshotVersion,
                             @Param("status") String status,
                             @Param("expectedVersion") int expectedVersion,
                             @Param("occurredTime") Instant occurredTime);

    List<PersonActiveProjectionRow> selectActiveProjections(@Param("userIds") Set<Long> userIds);
}

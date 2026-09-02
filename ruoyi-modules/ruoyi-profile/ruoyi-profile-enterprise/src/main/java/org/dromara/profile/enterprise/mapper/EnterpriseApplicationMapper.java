package org.dromara.profile.enterprise.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.profile.enterprise.domain.ProfileEnterpriseApplication;
import org.dromara.profile.enterprise.domain.vo.EnterpriseApplicationVo;

import org.apache.ibatis.annotations.Param;
import org.dromara.profile.enterprise.domain.vo.EnterpriseActiveProjectionRow;
import org.dromara.profile.enterprise.domain.vo.EnterpriseApplicationRow;
import org.dromara.profile.enterprise.domain.vo.EnterpriseBindingEventRow;
import org.dromara.profile.enterprise.domain.vo.EnterpriseBindingRow;
import org.dromara.profile.enterprise.domain.vo.EnterpriseDocumentTypeRow;
import org.dromara.profile.enterprise.domain.vo.EnterpriseProfileRow;
import org.dromara.profile.enterprise.domain.vo.EnterpriseSubmissionRow;
import org.dromara.profile.enterprise.domain.vo.EnterpriseVersionRow;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface EnterpriseApplicationMapper extends BaseMapperPlus<ProfileEnterpriseApplication, EnterpriseApplicationVo> {

    EnterpriseApplicationRow selectOpenByUserId(@Param("userId") long userId);

    EnterpriseApplicationRow lockOpenByUserId(@Param("userId") long userId);

    EnterpriseApplicationRow lockApplicationById(@Param("applicationId") long applicationId);

    EnterpriseDocumentTypeRow selectDocumentType(@Param("code") String documentTypeCode);

    Long selectActiveProfileIdByIdentity(@Param("identityKey") String identityKey);

    Long selectEffectiveProfileIdByUser(@Param("userId") long userId);

    String selectProbeStatus(@Param("identityKey") String identityKey);

    int insertApplication(EnterpriseApplicationRow row);

    int updateDraft(EnterpriseApplicationRow row);

    int insertSubmission(EnterpriseSubmissionRow row);

    EnterpriseSubmissionRow selectSubmission(@Param("applicationId") long applicationId,
                                             @Param("submissionSeq") int submissionSeq);

    int markWaiting(@Param("applicationId") long applicationId,
                    @Param("submissionSeq") int submissionSeq,
                    @Param("expectedVersion") int expectedVersion,
                    @Param("submittedTime") Instant submittedTime);

    EnterpriseProfileRow lockActiveProfileByIdentity(@Param("identityKey") String identityKey);

    EnterpriseProfileRow lockLatestRevokedProfileByIdentity(@Param("identityKey") String identityKey);

    int insertProfile(EnterpriseProfileRow row);

    EnterpriseVersionRow selectCurrentVersionForUpdate(@Param("profileId") long profileId);

    int supersedeVersion(@Param("versionId") long enterpriseVersionId);

    int insertVersion(EnterpriseVersionRow row);

    int updateProfile(EnterpriseProfileRow row);

    EnterpriseBindingRow lockEffectiveBindingByUser(@Param("userId") long userId);

    EnterpriseBindingRow lockEffectiveBindingByProfile(@Param("profileId") long enterpriseProfileId);

    int insertBinding(EnterpriseBindingRow row);

    int insertBindingEvent(EnterpriseBindingEventRow row);

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

    List<EnterpriseActiveProjectionRow> selectActiveProjections(@Param("userIds") Set<Long> userIds);

    Long lockMaterialWorkingOwner(@Param("ownerId") long ownerId);

    Long lockMaterialSubmissionOwner(@Param("ownerId") long ownerId);

    Long lockMaterialSourceOwner(@Param("ownerId") long ownerId);

    Long lockMaterialVersionOwner(@Param("ownerId") long ownerId);

    long countEditableMaterialWorkingOwner(@Param("ownerId") long ownerId);

    long countWorkingSubmissionRelationship(@Param("applicationId") long applicationId,
                                            @Param("submissionId") long submissionId);

    long countSubmissionVersionRelationship(@Param("submissionId") long submissionId,
                                            @Param("versionId") long versionId);

    long countSourceVersionRelationship(@Param("sourceId") long sourceId,
                                        @Param("versionId") long versionId);
}

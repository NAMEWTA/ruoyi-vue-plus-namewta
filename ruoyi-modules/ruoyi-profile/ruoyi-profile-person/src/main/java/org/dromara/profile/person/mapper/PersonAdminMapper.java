package org.dromara.profile.person.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.profile.person.domain.ProfilePerson;
import org.apache.ibatis.annotations.Param;
import org.dromara.profile.person.domain.vo.PersonAdminRows.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface PersonAdminMapper extends BaseMapperPlus<ProfilePerson, ProfilePerson> {

    long countProfiles(@Param("fullName") String fullName, @Param("documentNumber") String documentNumber,
                       @Param("status") String status);

    List<ProfileRow> selectProfiles(@Param("fullName") String fullName,
                                    @Param("documentNumber") String documentNumber,
                                    @Param("status") String status,
                                    @Param("limit") int limit, @Param("offset") int offset);

    ProfileRow selectProfile(@Param("profileId") long profileId);

    ProfileRow lockProfile(@Param("profileId") long profileId);

    List<VersionRow> selectVersions(@Param("profileId") long profileId);

    VersionRow lockCurrentVersion(@Param("profileId") long profileId);

    List<BindingRow> selectBindings(@Param("profileId") long profileId);

    BindingRow lockEffectiveBinding(@Param("profileId") long profileId);

    int countEffectiveBindingByUser(@Param("userId") long userId);

    List<SourceRow> selectSources(@Param("profileId") long profileId);

    List<AuditRow> selectAudits(@Param("profileId") long profileId);

    ReviewRow selectReview(@Param("applicationId") long applicationId);

    ReviewRow lockWaitingApplication(@Param("applicationId") long applicationId);

    int markOverridePending(@Param("applicationId") long applicationId, @Param("decision") String decision,
                            @Param("reason") String reason, @Param("decisionVersion") int decisionVersion,
                            @Param("version") int version, @Param("operatorId") long operatorId,
                            @Param("now") Instant now);

    int insertDecision(@Param("id") long id, @Param("applicationId") long applicationId,
                       @Param("submissionId") long submissionId, @Param("decisionVersion") int decisionVersion,
                       @Param("decision") String decision, @Param("operatorId") long operatorId,
                       @Param("reason") String reason, @Param("now") Instant now);

    int resumeWaiting(@Param("applicationId") long applicationId,
                      @Param("decisionVersion") int decisionVersion, @Param("operatorId") long operatorId);

    int markApproved(@Param("applicationId") long applicationId, @Param("operatorId") long operatorId,
                     @Param("reason") String reason, @Param("now") Instant now);

    int markRejected(@Param("applicationId") long applicationId, @Param("decisionVersion") int decisionVersion,
                     @Param("operatorId") long operatorId, @Param("reason") String reason,
                     @Param("now") Instant now);

    int finalizeDecision(@Param("applicationId") long applicationId,
                         @Param("decisionVersion") int decisionVersion,
                         @Param("operatorId") long operatorId, @Param("now") Instant now);

    int insertProfile(@Param("profileId") long profileId, @Param("fullName") String fullName,
                      @Param("documentType") String documentType, @Param("documentNumber") String documentNumber,
                      @Param("identityKey") String identityKey, @Param("gender") String gender,
                      @Param("birthDate") LocalDate birthDate, @Param("validFrom") LocalDate validFrom,
                      @Param("validUntil") LocalDate validUntil, @Param("operatorId") long operatorId,
                      @Param("now") Instant now);

    int insertSource(@Param("sourceId") long sourceId, @Param("profileId") long profileId,
                     @Param("sourceType") String sourceType, @Param("operatorId") long operatorId,
                     @Param("reason") String reason, @Param("fullName") String fullName,
                     @Param("documentType") String documentType, @Param("documentNumber") String documentNumber,
                     @Param("identityKey") String identityKey, @Param("gender") String gender,
                     @Param("birthDate") LocalDate birthDate, @Param("validFrom") LocalDate validFrom,
                     @Param("validUntil") LocalDate validUntil, @Param("json") String json,
                     @Param("now") Instant now);

    int insertVersion(@Param("versionId") long versionId, @Param("profileId") long profileId,
                      @Param("versionNo") int versionNo, @Param("sourceType") String sourceType,
                      @Param("sourceId") long sourceId, @Param("fullName") String fullName,
                      @Param("documentType") String documentType, @Param("documentNumber") String documentNumber,
                      @Param("identityKey") String identityKey, @Param("gender") String gender,
                      @Param("birthDate") LocalDate birthDate, @Param("validFrom") LocalDate validFrom,
                      @Param("validUntil") LocalDate validUntil, @Param("operatorId") long operatorId,
                      @Param("now") Instant now);

    int supersedeVersion(@Param("versionId") long versionId, @Param("operatorId") long operatorId,
                         @Param("now") Instant now);

    int updateProfileVersion(@Param("profileId") long profileId, @Param("versionId") long versionId,
                             @Param("fullName") String fullName, @Param("documentType") String documentType,
                             @Param("documentNumber") String documentNumber, @Param("identityKey") String identityKey,
                             @Param("gender") String gender, @Param("birthDate") LocalDate birthDate,
                             @Param("validFrom") LocalDate validFrom, @Param("validUntil") LocalDate validUntil,
                             @Param("expectedVersion") int expectedVersion, @Param("operatorId") long operatorId,
                             @Param("now") Instant now);

    int insertBinding(@Param("bindingId") long bindingId, @Param("profileId") long profileId,
                      @Param("userId") long userId, @Param("sourceType") String sourceType,
                      @Param("sourceId") Long sourceId, @Param("operatorId") long operatorId,
                      @Param("now") Instant now);

    int updateBinding(@Param("bindingId") long bindingId, @Param("sourceStatus") String sourceStatus,
                      @Param("targetStatus") String targetStatus, @Param("expectedVersion") int expectedVersion,
                      @Param("operatorId") long operatorId, @Param("now") Instant now);

    int insertBindingEvent(@Param("eventId") long eventId, @Param("bindingId") long bindingId,
                           @Param("profileId") long profileId, @Param("userId") long userId,
                           @Param("eventType") String eventType, @Param("bindingVersion") int bindingVersion,
                           @Param("sourceId") Long sourceId, @Param("reason") String reason,
                           @Param("operatorId") long operatorId, @Param("now") Instant now);

    int cloneVersionMaterials(@Param("versionId") long versionId, @Param("sourceId") long sourceId,
                              @Param("newIdBase") long newIdBase, @Param("operatorId") long operatorId,
                              @Param("now") Instant now);

    int revokeProfile(@Param("profileId") long profileId, @Param("expectedVersion") int expectedVersion,
                      @Param("reason") String reason, @Param("operatorId") long operatorId,
                      @Param("now") Instant now);

    int insertAudit(@Param("auditId") long auditId, @Param("profileId") Long profileId,
                    @Param("applicationId") Long applicationId, @Param("bindingId") Long bindingId,
                    @Param("operationType") String operationType, @Param("operatorId") long operatorId,
                    @Param("capability") String capability, @Param("reason") String reason,
                    @Param("beforeStatus") String beforeStatus, @Param("afterStatus") String afterStatus,
                    @Param("now") Instant now);
}

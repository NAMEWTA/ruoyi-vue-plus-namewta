package org.dromara.profile.enterprise.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.profile.enterprise.domain.ProfileEnterprise;
import org.dromara.profile.enterprise.domain.vo.EnterpriseProfileSummaryVo;

import org.apache.ibatis.annotations.Param;
import org.dromara.profile.enterprise.domain.vo.EnterpriseAdminRows.*;

import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;

public interface EnterpriseAdminMapper extends BaseMapperPlus<ProfileEnterprise, EnterpriseProfileSummaryVo> {

    long countProfiles(@Param("enterpriseName") String enterpriseName, @Param("unifiedCreditCode") String unifiedCreditCode,
                       @Param("status") String status);

    List<ProfileRow> selectProfiles(@Param("enterpriseName") String enterpriseName,
                                    @Param("unifiedCreditCode") String unifiedCreditCode,
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

    int insertProfile(@Param("profileId") long profileId, @Param("enterpriseName") String enterpriseName,
                      @Param("unifiedCreditCode") String unifiedCreditCode, @Param("enterpriseType") String enterpriseType,
                      @Param("legalRepresentativeName") String legalRepresentativeName,
                      @Param("legalDocumentTypeCode") String legalDocumentTypeCode,
                      @Param("legalDocumentNumber") String legalDocumentNumber,
                      @Param("establishedDate") LocalDate establishedDate,
                      @Param("businessTermFrom") LocalDate businessTermFrom,
                      @Param("businessTermUntil") LocalDate businessTermUntil,
                      @Param("registeredAddress") String registeredAddress, @Param("businessScope") String businessScope,
                      @Param("contactName") String contactName, @Param("contactPhone") String contactPhone,
                      @Param("email") String email, @Param("registeredCapital") BigDecimal registeredCapital,
                      @Param("industryCode") String industryCode, @Param("website") String website,
                      @Param("operatorId") long operatorId,
                      @Param("now") Instant now);

    int insertSource(@Param("sourceId") long sourceId, @Param("profileId") long profileId,
                     @Param("sourceType") String sourceType, @Param("operatorId") long operatorId,
                     @Param("reason") String reason, @Param("enterpriseName") String enterpriseName,
                     @Param("unifiedCreditCode") String unifiedCreditCode, @Param("identityKey") String identityKey,
                     @Param("enterpriseType") String enterpriseType,
                     @Param("legalRepresentativeName") String legalRepresentativeName,
                     @Param("legalDocumentTypeCode") String legalDocumentTypeCode,
                     @Param("legalDocumentNumber") String legalDocumentNumber,
                     @Param("establishedDate") LocalDate establishedDate,
                     @Param("businessTermFrom") LocalDate businessTermFrom,
                     @Param("businessTermUntil") LocalDate businessTermUntil,
                     @Param("registeredAddress") String registeredAddress, @Param("businessScope") String businessScope,
                     @Param("contactName") String contactName, @Param("contactPhone") String contactPhone,
                     @Param("email") String email, @Param("registeredCapital") BigDecimal registeredCapital,
                     @Param("industryCode") String industryCode, @Param("website") String website,
                     @Param("json") String json,
                     @Param("now") Instant now);

    int insertVersion(@Param("versionId") long versionId, @Param("profileId") long profileId,
                      @Param("versionNo") int versionNo, @Param("sourceType") String sourceType,
                      @Param("sourceId") long sourceId, @Param("enterpriseName") String enterpriseName,
                      @Param("unifiedCreditCode") String unifiedCreditCode, @Param("enterpriseType") String enterpriseType,
                      @Param("legalRepresentativeName") String legalRepresentativeName,
                      @Param("legalDocumentTypeCode") String legalDocumentTypeCode,
                      @Param("legalDocumentNumber") String legalDocumentNumber,
                      @Param("establishedDate") LocalDate establishedDate,
                      @Param("businessTermFrom") LocalDate businessTermFrom,
                      @Param("businessTermUntil") LocalDate businessTermUntil,
                      @Param("registeredAddress") String registeredAddress, @Param("businessScope") String businessScope,
                      @Param("contactName") String contactName, @Param("contactPhone") String contactPhone,
                      @Param("email") String email, @Param("registeredCapital") BigDecimal registeredCapital,
                      @Param("industryCode") String industryCode, @Param("website") String website,
                      @Param("operatorId") long operatorId,
                      @Param("now") Instant now);

    int supersedeVersion(@Param("versionId") long versionId, @Param("operatorId") long operatorId,
                         @Param("now") Instant now);

    int updateProfileVersion(@Param("profileId") long profileId, @Param("versionId") long versionId,
                             @Param("enterpriseName") String enterpriseName,
                             @Param("unifiedCreditCode") String unifiedCreditCode,
                             @Param("enterpriseType") String enterpriseType,
                             @Param("legalRepresentativeName") String legalRepresentativeName,
                             @Param("legalDocumentTypeCode") String legalDocumentTypeCode,
                             @Param("legalDocumentNumber") String legalDocumentNumber,
                             @Param("establishedDate") LocalDate establishedDate,
                             @Param("businessTermFrom") LocalDate businessTermFrom,
                             @Param("businessTermUntil") LocalDate businessTermUntil,
                             @Param("registeredAddress") String registeredAddress,
                             @Param("businessScope") String businessScope,
                             @Param("contactName") String contactName, @Param("contactPhone") String contactPhone,
                             @Param("email") String email, @Param("registeredCapital") BigDecimal registeredCapital,
                             @Param("industryCode") String industryCode, @Param("website") String website,
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

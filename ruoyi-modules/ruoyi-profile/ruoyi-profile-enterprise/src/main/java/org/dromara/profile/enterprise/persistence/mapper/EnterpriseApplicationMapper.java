package org.dromara.profile.enterprise.persistence.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.profile.enterprise.persistence.row.EnterpriseActiveProjectionRow;
import org.dromara.profile.enterprise.persistence.row.EnterpriseApplicationRow;
import org.dromara.profile.enterprise.persistence.row.EnterpriseBindingEventRow;
import org.dromara.profile.enterprise.persistence.row.EnterpriseBindingRow;
import org.dromara.profile.enterprise.persistence.row.EnterpriseDocumentTypeRow;
import org.dromara.profile.enterprise.persistence.row.EnterpriseProfileRow;
import org.dromara.profile.enterprise.persistence.row.EnterpriseSubmissionRow;
import org.dromara.profile.enterprise.persistence.row.EnterpriseVersionRow;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface EnterpriseApplicationMapper {

    String BUSINESS_COLUMNS = """
        enterprise_name, unified_credit_code, enterprise_type, legal_representative_name,
        legal_document_type_code, legal_document_number, established_date, business_term_from,
        business_term_until, registered_address, business_scope, contact_name, contact_phone,
        email, registered_capital, industry_code, website
        """;

    String APPLICATION_COLUMNS = """
        enterprise_application_id, applicant_user_id, target_profile_id, status,
        enterprise_name, unified_credit_code, identity_key, enterprise_type,
        legal_representative_name, legal_document_type_code, legal_document_number,
        handler_is_legal_representative, established_date, business_term_from, business_term_until,
        registered_address, business_scope, contact_name, contact_phone, email, registered_capital,
        industry_code, website, provider_code, submission_seq, decision_version, version,
        submitted_time, finished_time
        """;

    String PROFILE_COLUMNS = """
        enterprise_profile_id, previous_profile_id, current_version_id,
        enterprise_name, unified_credit_code, enterprise_type, legal_representative_name,
        legal_document_type_code, legal_document_number, established_date, business_term_from,
        business_term_until, registered_address, business_scope, contact_name, contact_phone,
        email, registered_capital, industry_code, website, status, version
        """;

    @Select("select " + APPLICATION_COLUMNS + " from profile_enterprise_application"
        + " where applicant_user_id = #{userId} and status in ('DRAFT','BACK','CANCEL','WAITING')"
        + " and del_flag = '0'")
    EnterpriseApplicationRow selectOpenByUserId(@Param("userId") long userId);

    @Select("select " + APPLICATION_COLUMNS + " from profile_enterprise_application"
        + " where applicant_user_id = #{userId} and status in ('DRAFT','BACK','CANCEL','WAITING')"
        + " and del_flag = '0' for update")
    EnterpriseApplicationRow lockOpenByUserId(@Param("userId") long userId);

    @Select("select " + APPLICATION_COLUMNS + " from profile_enterprise_application"
        + " where enterprise_application_id = #{applicationId} and del_flag = '0' for update")
    EnterpriseApplicationRow lockApplicationById(@Param("applicationId") long applicationId);

    @Select("""
        select document_type_code, number_pattern, validity_required
          from profile_document_type
         where document_type_code = #{code} and status = '0' and del_flag = '0'
        """)
    EnterpriseDocumentTypeRow selectDocumentType(@Param("code") String documentTypeCode);

    @Select("""
        select enterprise_profile_id from profile_enterprise
         where upper(trim(unified_credit_code)) = #{identityKey}
           and status = 'ACTIVE' and del_flag = '0'
        """)
    Long selectActiveProfileIdByIdentity(@Param("identityKey") String identityKey);

    @Select("""
        select b.enterprise_profile_id
          from profile_enterprise_binding b
          join profile_enterprise p on p.enterprise_profile_id = b.enterprise_profile_id
         where b.user_id = #{userId} and b.status in ('ACTIVE','SUSPENDED') and b.del_flag = '0'
           and p.status = 'ACTIVE' and p.del_flag = '0'
        """)
    Long selectEffectiveProfileIdByUser(@Param("userId") long userId);

    @Select("""
        select case
          when exists (
            select 1 from profile_enterprise_application
             where identity_key = #{identityKey} and status in ('DRAFT','BACK','CANCEL','WAITING')
               and del_flag = '0'
          ) then 'IN_PROGRESS'
          when exists (
            select 1 from profile_enterprise p
              join profile_enterprise_binding b on b.enterprise_profile_id = p.enterprise_profile_id
             where upper(trim(p.unified_credit_code)) = #{identityKey} and p.status = 'ACTIVE'
               and p.del_flag = '0' and b.status in ('ACTIVE','SUSPENDED') and b.del_flag = '0'
          ) then 'BOUND'
          when exists (
            select 1 from profile_enterprise
             where upper(trim(unified_credit_code)) = #{identityKey}
               and status = 'ACTIVE' and del_flag = '0'
          ) then 'UNBOUND'
          else 'AVAILABLE'
        end
        """)
    String selectProbeStatus(@Param("identityKey") String identityKey);

    @Insert("""
        insert into profile_enterprise_application (
            enterprise_application_id, applicant_user_id, target_profile_id, status,
            enterprise_name, unified_credit_code, identity_key, enterprise_type,
            legal_representative_name, legal_document_type_code, legal_document_number,
            handler_is_legal_representative, established_date, business_term_from, business_term_until,
            registered_address, business_scope, contact_name, contact_phone, email, registered_capital,
            industry_code, website, provider_code, submission_seq, decision_version, version,
            create_dept, create_time, create_by, update_time, update_by, del_flag
        ) values (
            #{enterpriseApplicationId}, #{applicantUserId}, #{targetProfileId}, 'DRAFT',
            #{enterpriseName}, #{unifiedCreditCode}, #{identityKey}, #{enterpriseType},
            #{legalRepresentativeName}, #{legalDocumentTypeCode}, #{legalDocumentNumber},
            #{handlerIsLegalRepresentative}, #{establishedDate}, #{businessTermFrom}, #{businessTermUntil},
            #{registeredAddress}, #{businessScope}, #{contactName}, #{contactPhone}, #{email},
            #{registeredCapital}, #{industryCode}, #{website}, #{providerCode}, 0, 0, 0,
            -1, current_timestamp, #{applicantUserId}, current_timestamp, #{applicantUserId}, '0'
        )
        """)
    int insertApplication(EnterpriseApplicationRow row);

    @Update("""
        update profile_enterprise_application
           set target_profile_id = #{targetProfileId}, status = 'DRAFT',
               enterprise_name = #{enterpriseName}, unified_credit_code = #{unifiedCreditCode},
               identity_key = #{identityKey}, enterprise_type = #{enterpriseType},
               legal_representative_name = #{legalRepresentativeName},
               legal_document_type_code = #{legalDocumentTypeCode},
               legal_document_number = #{legalDocumentNumber},
               handler_is_legal_representative = #{handlerIsLegalRepresentative},
               established_date = #{establishedDate}, business_term_from = #{businessTermFrom},
               business_term_until = #{businessTermUntil}, registered_address = #{registeredAddress},
               business_scope = #{businessScope}, contact_name = #{contactName},
               contact_phone = #{contactPhone}, email = #{email}, registered_capital = #{registeredCapital},
               industry_code = #{industryCode}, website = #{website}, version = version + 1,
               update_time = current_timestamp, update_by = #{applicantUserId}
         where enterprise_application_id = #{enterpriseApplicationId}
           and applicant_user_id = #{applicantUserId} and status in ('DRAFT','BACK','CANCEL')
           and version = #{version} and del_flag = '0'
        """)
    int updateDraft(EnterpriseApplicationRow row);

    @Insert("""
        insert into profile_enterprise_submission (
            enterprise_submission_id, enterprise_application_id, submission_seq,
            enterprise_name, unified_credit_code, identity_key, enterprise_type,
            legal_representative_name, legal_document_type_code, legal_document_number,
            handler_is_legal_representative, established_date, business_term_from, business_term_until,
            registered_address, business_scope, contact_name, contact_phone, email, registered_capital,
            industry_code, website, provider_code, field_snapshot_json, submitted_time, version,
            create_dept, create_time, create_by, update_time, update_by, del_flag
        ) values (
            #{enterpriseSubmissionId}, #{enterpriseApplicationId}, #{submissionSeq},
            #{enterpriseName}, #{unifiedCreditCode}, #{identityKey}, #{enterpriseType},
            #{legalRepresentativeName}, #{legalDocumentTypeCode}, #{legalDocumentNumber},
            #{handlerIsLegalRepresentative}, #{establishedDate}, #{businessTermFrom}, #{businessTermUntil},
            #{registeredAddress}, #{businessScope}, #{contactName}, #{contactPhone}, #{email},
            #{registeredCapital}, #{industryCode}, #{website}, #{providerCode}, #{fieldSnapshotJson},
            #{submittedTime}, 0, -1, current_timestamp, #{applicantUserId},
            current_timestamp, #{applicantUserId}, '0'
        )
        """)
    int insertSubmission(EnterpriseSubmissionRow row);

    @Select("""
        select s.enterprise_submission_id, s.enterprise_application_id, s.submission_seq,
               a.applicant_user_id, a.target_profile_id, s.enterprise_name, s.unified_credit_code,
               s.identity_key, s.enterprise_type, s.legal_representative_name,
               s.legal_document_type_code, s.legal_document_number,
               s.handler_is_legal_representative, s.established_date, s.business_term_from,
               s.business_term_until, s.registered_address, s.business_scope, s.contact_name,
               s.contact_phone, s.email, s.registered_capital, s.industry_code, s.website,
               s.provider_code, s.field_snapshot_json, s.submitted_time
          from profile_enterprise_submission s
          join profile_enterprise_application a on a.enterprise_application_id = s.enterprise_application_id
         where s.enterprise_application_id = #{applicationId} and s.submission_seq = #{submissionSeq}
           and s.del_flag = '0' and a.del_flag = '0'
        """)
    EnterpriseSubmissionRow selectSubmission(@Param("applicationId") long applicationId,
                                             @Param("submissionSeq") int submissionSeq);

    @Update("""
        update profile_enterprise_application
           set status = 'WAITING', submission_seq = #{submissionSeq}, submitted_time = #{submittedTime},
               finished_time = null, decision_source = null, decision_result = null, decision_reason = null,
               version = version + 1, update_time = current_timestamp, update_by = applicant_user_id
         where enterprise_application_id = #{applicationId} and submission_seq = #{submissionSeq} - 1
           and status in ('DRAFT','BACK','CANCEL') and version = #{expectedVersion} and del_flag = '0'
        """)
    int markWaiting(@Param("applicationId") long applicationId,
                    @Param("submissionSeq") int submissionSeq,
                    @Param("expectedVersion") int expectedVersion,
                    @Param("submittedTime") Instant submittedTime);

    @Select("select " + PROFILE_COLUMNS + " from profile_enterprise"
        + " where upper(trim(unified_credit_code)) = #{identityKey}"
        + " and status = 'ACTIVE' and del_flag = '0' for update")
    EnterpriseProfileRow lockActiveProfileByIdentity(@Param("identityKey") String identityKey);

    @Select("select " + PROFILE_COLUMNS + " from profile_enterprise"
        + " where upper(trim(unified_credit_code)) = #{identityKey}"
        + " and status = 'REVOKED' and del_flag = '0'"
        + " order by revoked_time desc, enterprise_profile_id desc limit 1 for update")
    EnterpriseProfileRow lockLatestRevokedProfileByIdentity(@Param("identityKey") String identityKey);

    @Insert("""
        insert into profile_enterprise (
            enterprise_profile_id, previous_profile_id, current_version_id,
            enterprise_name, unified_credit_code, enterprise_type, legal_representative_name,
            legal_document_type_code, legal_document_number, established_date, business_term_from,
            business_term_until, registered_address, business_scope, contact_name, contact_phone,
            email, registered_capital, industry_code, website, status, version,
            create_dept, create_time, create_by, update_time, update_by, del_flag
        ) values (
            #{enterpriseProfileId}, #{previousProfileId}, null,
            #{enterpriseName}, #{unifiedCreditCode}, #{enterpriseType}, #{legalRepresentativeName},
            #{legalDocumentTypeCode}, #{legalDocumentNumber}, #{establishedDate}, #{businessTermFrom},
            #{businessTermUntil}, #{registeredAddress}, #{businessScope}, #{contactName}, #{contactPhone},
            #{email}, #{registeredCapital}, #{industryCode}, #{website}, 'ACTIVE', 0,
            -1, current_timestamp, -1, current_timestamp, -1, '0'
        )
        """)
    int insertProfile(EnterpriseProfileRow row);

    @Select("""
        select enterprise_version_id, enterprise_profile_id, version_no, source_type, source_id,
               enterprise_name, unified_credit_code, enterprise_type, legal_representative_name,
               legal_document_type_code, legal_document_number, established_date, business_term_from,
               business_term_until, registered_address, business_scope, contact_name, contact_phone,
               email, registered_capital, industry_code, website, status, published_time
          from profile_enterprise_version
         where enterprise_profile_id = #{profileId} and status = 'CURRENT' and del_flag = '0'
         for update
        """)
    EnterpriseVersionRow selectCurrentVersionForUpdate(@Param("profileId") long profileId);

    @Update("""
        update profile_enterprise_version set status = 'SUPERSEDED', version = version + 1,
               update_time = current_timestamp, update_by = -1
         where enterprise_version_id = #{versionId} and status = 'CURRENT' and del_flag = '0'
        """)
    int supersedeVersion(@Param("versionId") long enterpriseVersionId);

    @Insert("""
        insert into profile_enterprise_version (
            enterprise_version_id, enterprise_profile_id, version_no, source_type, source_id,
            enterprise_name, unified_credit_code, enterprise_type, legal_representative_name,
            legal_document_type_code, legal_document_number, established_date, business_term_from,
            business_term_until, registered_address, business_scope, contact_name, contact_phone,
            email, registered_capital, industry_code, website, status, published_time, version,
            create_dept, create_time, create_by, update_time, update_by, del_flag
        ) values (
            #{enterpriseVersionId}, #{enterpriseProfileId}, #{versionNo}, #{sourceType}, #{sourceId},
            #{enterpriseName}, #{unifiedCreditCode}, #{enterpriseType}, #{legalRepresentativeName},
            #{legalDocumentTypeCode}, #{legalDocumentNumber}, #{establishedDate}, #{businessTermFrom},
            #{businessTermUntil}, #{registeredAddress}, #{businessScope}, #{contactName}, #{contactPhone},
            #{email}, #{registeredCapital}, #{industryCode}, #{website}, 'CURRENT', #{publishedTime}, 0,
            -1, current_timestamp, -1, current_timestamp, -1, '0'
        )
        """)
    int insertVersion(EnterpriseVersionRow row);

    @Update("""
        update profile_enterprise
           set current_version_id = #{currentVersionId}, enterprise_name = #{enterpriseName},
               unified_credit_code = #{unifiedCreditCode}, enterprise_type = #{enterpriseType},
               legal_representative_name = #{legalRepresentativeName},
               legal_document_type_code = #{legalDocumentTypeCode},
               legal_document_number = #{legalDocumentNumber}, established_date = #{establishedDate},
               business_term_from = #{businessTermFrom}, business_term_until = #{businessTermUntil},
               registered_address = #{registeredAddress}, business_scope = #{businessScope},
               contact_name = #{contactName}, contact_phone = #{contactPhone}, email = #{email},
               registered_capital = #{registeredCapital}, industry_code = #{industryCode},
               website = #{website}, status = 'ACTIVE', version = version + 1,
               update_time = current_timestamp, update_by = -1
         where enterprise_profile_id = #{enterpriseProfileId} and status = 'ACTIVE'
           and version = #{version} and del_flag = '0'
        """)
    int updateProfile(EnterpriseProfileRow row);

    @Select("""
        select enterprise_binding_id, enterprise_profile_id, user_id, status, binding_version,
               source_type, source_id, bound_time
          from profile_enterprise_binding
         where user_id = #{userId} and status in ('ACTIVE','SUSPENDED') and del_flag = '0'
         for update
        """)
    EnterpriseBindingRow lockEffectiveBindingByUser(@Param("userId") long userId);

    @Select("""
        select enterprise_binding_id, enterprise_profile_id, user_id, status, binding_version,
               source_type, source_id, bound_time
          from profile_enterprise_binding
         where enterprise_profile_id = #{profileId}
           and status in ('ACTIVE','SUSPENDED') and del_flag = '0' for update
        """)
    EnterpriseBindingRow lockEffectiveBindingByProfile(@Param("profileId") long enterpriseProfileId);

    @Insert("""
        insert into profile_enterprise_binding (
            enterprise_binding_id, enterprise_profile_id, user_id, status, binding_version,
            source_type, source_id, bound_time, version,
            create_dept, create_time, create_by, update_time, update_by, del_flag
        ) values (
            #{enterpriseBindingId}, #{enterpriseProfileId}, #{userId}, 'ACTIVE', #{bindingVersion},
            #{sourceType}, #{sourceId}, #{boundTime}, 0,
            -1, current_timestamp, -1, current_timestamp, -1, '0'
        )
        """)
    int insertBinding(EnterpriseBindingRow row);

    @Insert("""
        insert into profile_enterprise_binding_event (
            enterprise_binding_event_id, enterprise_binding_id, enterprise_profile_id, user_id,
            event_type, binding_version, source_type, source_id, reason, occurred_time,
            version, create_dept, create_time, create_by, update_time, update_by, del_flag
        ) values (
            #{enterpriseBindingEventId}, #{enterpriseBindingId}, #{enterpriseProfileId}, #{userId},
            #{eventType}, #{bindingVersion}, #{sourceType}, #{sourceId}, #{reason}, #{occurredTime},
            0, -1, current_timestamp, -1, current_timestamp, -1, '0'
        )
        """)
    int insertBindingEvent(EnterpriseBindingEventRow row);

    @Update("""
        update profile_enterprise_application
           set status = 'FINISH', decision_version = decision_version + 1,
               decision_source = 'WORKFLOW', decision_result = 'APPROVED', finished_time = #{finishedTime},
               version = version + 1, update_time = current_timestamp, update_by = -1
         where enterprise_application_id = #{applicationId} and submission_seq = #{snapshotVersion}
           and status = 'WAITING' and decision_version = #{decisionVersion}
           and version = #{expectedVersion} and del_flag = '0'
        """)
    int finishApplication(@Param("applicationId") long applicationId,
                          @Param("snapshotVersion") int snapshotVersion,
                          @Param("decisionVersion") int decisionVersion,
                          @Param("expectedVersion") int expectedVersion,
                          @Param("finishedTime") Instant finishedTime);

    @Update("""
        update profile_enterprise_application
           set status = #{status}, decision_version = decision_version + 1,
               decision_source = 'WORKFLOW', decision_result = #{status},
               finished_time = case when #{status} in ('INVALID','TERMINATION') then #{occurredTime} else null end,
               version = version + 1, update_time = current_timestamp, update_by = -1
         where enterprise_application_id = #{applicationId} and submission_seq = #{snapshotVersion}
           and status = 'WAITING' and version = #{expectedVersion} and del_flag = '0'
        """)
    int updateWorkflowStatus(@Param("applicationId") long applicationId,
                             @Param("snapshotVersion") int snapshotVersion,
                             @Param("status") String status,
                             @Param("expectedVersion") int expectedVersion,
                             @Param("occurredTime") Instant occurredTime);

    @Select("""
        <script>
        select b.user_id, b.enterprise_profile_id, v.published_time as verified_at
          from profile_enterprise_binding b
          join profile_enterprise p on p.enterprise_profile_id = b.enterprise_profile_id
          join profile_enterprise_version v on v.enterprise_version_id = p.current_version_id
         where b.status = 'ACTIVE' and b.del_flag = '0'
           and p.status = 'ACTIVE' and p.del_flag = '0'
           and v.status = 'CURRENT' and v.del_flag = '0'
           and b.user_id in
           <foreach collection="userIds" item="userId" open="(" separator="," close=")">#{userId}</foreach>
         order by b.user_id
        </script>
        """)
    List<EnterpriseActiveProjectionRow> selectActiveProjections(@Param("userIds") Set<Long> userIds);
}

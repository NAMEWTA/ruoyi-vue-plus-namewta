package org.dromara.profile.person.admin;

import org.apache.ibatis.annotations.*;
import org.dromara.profile.person.admin.PersonAdminRows.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface PersonAdminMapper {

    @Select("""
        <script>
        select count(*) from profile_person p
         where p.del_flag = '0'
        <if test="status == null or status == ''">and p.status != 'REVOKED'</if>
        <if test="status != null and status != ''">and p.status = #{status}</if>
        <if test="fullName != null and fullName != ''">and p.full_name like concat('%', #{fullName}, '%')</if>
        <if test="documentNumber != null and documentNumber != ''">and p.document_number = #{documentNumber}</if>
        </script>
        """)
    long countProfiles(@Param("fullName") String fullName, @Param("documentNumber") String documentNumber,
                       @Param("status") String status);

    @Select("""
        <script>
        select p.person_profile_id profile_id, p.previous_profile_id, p.current_version_id, p.full_name,
               p.document_type_code, p.document_number, p.identity_key, p.gender, p.birth_date,
               p.valid_from, p.valid_until, p.status, p.version, p.create_time,
               b.user_id binding_user_id, b.status binding_status
          from profile_person p
          left join profile_person_binding b on b.person_profile_id = p.person_profile_id
                 and b.status in ('ACTIVE','SUSPENDED') and b.del_flag = '0'
         where p.del_flag = '0'
        <if test="status == null or status == ''">and p.status != 'REVOKED'</if>
        <if test="status != null and status != ''">and p.status = #{status}</if>
        <if test="fullName != null and fullName != ''">and p.full_name like concat('%', #{fullName}, '%')</if>
        <if test="documentNumber != null and documentNumber != ''">and p.document_number = #{documentNumber}</if>
         order by p.create_time desc, p.person_profile_id desc limit #{limit} offset #{offset}
        </script>
        """)
    List<ProfileRow> selectProfiles(@Param("fullName") String fullName,
                                    @Param("documentNumber") String documentNumber,
                                    @Param("status") String status,
                                    @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
        select p.person_profile_id profile_id, p.previous_profile_id, p.current_version_id, p.full_name,
               p.document_type_code, p.document_number, p.identity_key, p.gender, p.birth_date,
               p.valid_from, p.valid_until, p.status, p.version, p.create_time,
               b.user_id binding_user_id, b.status binding_status
          from profile_person p
          left join profile_person_binding b on b.person_profile_id = p.person_profile_id
                 and b.status in ('ACTIVE','SUSPENDED') and b.del_flag = '0'
         where p.person_profile_id = #{profileId} and p.del_flag = '0'
        """)
    ProfileRow selectProfile(@Param("profileId") long profileId);

    @Select("""
        select p.person_profile_id profile_id, p.previous_profile_id, p.current_version_id, p.full_name,
               p.document_type_code, p.document_number, p.identity_key, p.gender, p.birth_date,
               p.valid_from, p.valid_until, p.status, p.version, p.create_time
          from profile_person p where p.person_profile_id = #{profileId} and p.del_flag = '0' for update
        """)
    ProfileRow lockProfile(@Param("profileId") long profileId);

    @Select("""
        select person_version_id version_id, person_profile_id profile_id, version_no, source_type, source_id,
               full_name, document_type_code, document_number, identity_key, gender, birth_date,
               valid_from, valid_until, status, published_time
          from profile_person_version where person_profile_id = #{profileId} and del_flag = '0'
         order by version_no desc
        """)
    List<VersionRow> selectVersions(@Param("profileId") long profileId);

    @Select("""
        select person_version_id version_id, person_profile_id profile_id, version_no, source_type, source_id,
               full_name, document_type_code, document_number, identity_key, gender, birth_date,
               valid_from, valid_until, status, published_time
          from profile_person_version where person_profile_id = #{profileId} and status = 'CURRENT'
           and del_flag = '0' for update
        """)
    VersionRow lockCurrentVersion(@Param("profileId") long profileId);

    @Select("""
        select person_binding_id binding_id, person_profile_id profile_id, user_id, status, binding_version,
               source_type, source_id, bound_time, unbound_time
          from profile_person_binding where person_profile_id = #{profileId} and del_flag = '0'
         order by create_time desc, person_binding_id desc
        """)
    List<BindingRow> selectBindings(@Param("profileId") long profileId);

    @Select("""
        select person_binding_id binding_id, person_profile_id profile_id, user_id, status, binding_version,
               source_type, source_id, bound_time, unbound_time
          from profile_person_binding where person_profile_id = #{profileId}
           and status in ('ACTIVE','SUSPENDED') and del_flag = '0' for update
        """)
    BindingRow lockEffectiveBinding(@Param("profileId") long profileId);

    @Select("""
        select count(*) from profile_person_binding where user_id = #{userId}
         and status in ('ACTIVE','SUSPENDED') and del_flag = '0'
        """)
    int countEffectiveBindingByUser(@Param("userId") long userId);

    @Select("""
        select person_source_id source_id, source_type, operator_user_id, operation_reason reason,
               field_snapshot_json, occurred_time from profile_person_source
         where person_profile_id = #{profileId} and del_flag = '0' order by occurred_time desc
        """)
    List<SourceRow> selectSources(@Param("profileId") long profileId);

    @Select("""
        select operation_audit_id audit_id, operation_type, operator_user_id, capability, reason,
               before_status, after_status, result, failure_category, occurred_time
          from profile_operation_audit where profile_type = 'PERSON' and profile_id = #{profileId}
           and del_flag = '0' order by occurred_time desc
        """)
    List<AuditRow> selectAudits(@Param("profileId") long profileId);

    @Select("""
        select a.person_application_id application_id, a.applicant_user_id, a.status, a.submission_seq,
               a.decision_version, a.version, s.person_submission_id submission_id,
               s.field_snapshot_json, s.submitted_time
          from profile_person_application a join profile_person_submission s
            on s.person_application_id = a.person_application_id and s.submission_seq = a.submission_seq
           and s.del_flag = '0'
         where a.person_application_id = #{applicationId} and a.submission_seq > 0 and a.del_flag = '0'
        """)
    ReviewRow selectReview(@Param("applicationId") long applicationId);

    @Select("""
        select a.person_application_id application_id, a.applicant_user_id, a.status, a.submission_seq,
               a.decision_version, a.version, s.person_submission_id submission_id,
               s.field_snapshot_json, s.submitted_time
          from profile_person_application a join profile_person_submission s
            on s.person_application_id = a.person_application_id and s.submission_seq = a.submission_seq
           and s.del_flag = '0'
         where a.person_application_id = #{applicationId} and a.status = 'WAITING' and a.del_flag = '0'
         for update
        """)
    ReviewRow lockWaitingApplication(@Param("applicationId") long applicationId);

    @Update("""
        update profile_person_application set status = 'OVERRIDE_PENDING', decision_version = decision_version + 1,
               decision_source = 'ADMIN_OVERRIDE', decision_result = #{decision}, decision_reason = #{reason},
               version = version + 1, update_time = #{now}, update_by = #{operatorId}
         where person_application_id = #{applicationId} and status = 'WAITING'
           and decision_version = #{decisionVersion} and version = #{version} and del_flag = '0'
        """)
    int markOverridePending(@Param("applicationId") long applicationId, @Param("decision") String decision,
                            @Param("reason") String reason, @Param("decisionVersion") int decisionVersion,
                            @Param("version") int version, @Param("operatorId") long operatorId,
                            @Param("now") Instant now);

    @Insert("""
        insert into profile_decision_record(decision_record_id, profile_type, application_id, submission_id,
            decision_version, decision_source, decision_result, decision_status, operator_user_id, reason,
            occurred_time, version, create_dept, create_time, create_by, update_time, update_by, del_flag)
        values(#{id}, 'PERSON', #{applicationId}, #{submissionId}, #{decisionVersion}, 'ADMIN_OVERRIDE',
            #{decision}, 'PENDING', #{operatorId}, #{reason}, #{now}, 0, -1, #{now}, #{operatorId},
            #{now}, #{operatorId}, '0')
        """)
    int insertDecision(@Param("id") long id, @Param("applicationId") long applicationId,
                       @Param("submissionId") long submissionId, @Param("decisionVersion") int decisionVersion,
                       @Param("decision") String decision, @Param("operatorId") long operatorId,
                       @Param("reason") String reason, @Param("now") Instant now);

    @Update("""
        update profile_person_application set status = 'WAITING', version = version + 1,
               update_time = current_timestamp, update_by = #{operatorId}
         where person_application_id = #{applicationId} and status = 'OVERRIDE_PENDING'
           and decision_version = #{decisionVersion} and del_flag = '0'
        """)
    int resumeWaiting(@Param("applicationId") long applicationId,
                      @Param("decisionVersion") int decisionVersion, @Param("operatorId") long operatorId);

    @Update("""
        update profile_person_application set decision_source = 'ADMIN_OVERRIDE', decision_result = 'APPROVED',
               decision_reason = #{reason}, version = version + 1, update_time = #{now}, update_by = #{operatorId}
         where person_application_id = #{applicationId} and status = 'FINISH' and del_flag = '0'
        """)
    int markApproved(@Param("applicationId") long applicationId, @Param("operatorId") long operatorId,
                     @Param("reason") String reason, @Param("now") Instant now);

    @Update("""
        update profile_person_application set status = 'INVALID', decision_source = 'ADMIN_OVERRIDE',
               decision_result = 'REJECTED', decision_reason = #{reason}, finished_time = #{now},
               version = version + 1, update_time = #{now}, update_by = #{operatorId}
         where person_application_id = #{applicationId} and status = 'OVERRIDE_PENDING'
           and decision_version = #{decisionVersion} and del_flag = '0'
        """)
    int markRejected(@Param("applicationId") long applicationId, @Param("decisionVersion") int decisionVersion,
                     @Param("operatorId") long operatorId, @Param("reason") String reason,
                     @Param("now") Instant now);

    @Update("""
        update profile_decision_record set decision_status = 'FINAL', occurred_time = #{now},
               update_time = #{now}, update_by = #{operatorId}, version = version + 1
         where profile_type = 'PERSON' and application_id = #{applicationId}
           and decision_version = #{decisionVersion} and decision_source = 'ADMIN_OVERRIDE'
           and decision_status = 'PENDING' and del_flag = '0'
        """)
    int finalizeDecision(@Param("applicationId") long applicationId,
                         @Param("decisionVersion") int decisionVersion,
                         @Param("operatorId") long operatorId, @Param("now") Instant now);

    @Insert("""
        insert into profile_person(person_profile_id, previous_profile_id, current_version_id, full_name,
            document_type_code, document_number, identity_key, gender, birth_date, valid_from, valid_until,
            status, version, create_dept, create_time, create_by, update_time, update_by, del_flag)
        values(#{profileId}, null, null, #{fullName}, #{documentType}, #{documentNumber}, #{identityKey},
            #{gender}, #{birthDate}, #{validFrom}, #{validUntil}, 'ACTIVE', 0, -1, #{now}, #{operatorId},
            #{now}, #{operatorId}, '0')
        """)
    int insertProfile(@Param("profileId") long profileId, @Param("fullName") String fullName,
                      @Param("documentType") String documentType, @Param("documentNumber") String documentNumber,
                      @Param("identityKey") String identityKey, @Param("gender") String gender,
                      @Param("birthDate") LocalDate birthDate, @Param("validFrom") LocalDate validFrom,
                      @Param("validUntil") LocalDate validUntil, @Param("operatorId") long operatorId,
                      @Param("now") Instant now);

    @Insert("""
        insert into profile_person_source(person_source_id, person_profile_id, source_type, operator_user_id,
            operation_reason, full_name, document_type_code, document_number, identity_key, gender, birth_date,
            valid_from, valid_until, field_snapshot_json, occurred_time, version, create_dept, create_time,
            create_by, update_time, update_by, del_flag)
        values(#{sourceId}, #{profileId}, #{sourceType}, #{operatorId}, #{reason}, #{fullName}, #{documentType},
            #{documentNumber}, #{identityKey}, #{gender}, #{birthDate}, #{validFrom}, #{validUntil}, #{json},
            #{now}, 0, -1, #{now}, #{operatorId}, #{now}, #{operatorId}, '0')
        """)
    int insertSource(@Param("sourceId") long sourceId, @Param("profileId") long profileId,
                     @Param("sourceType") String sourceType, @Param("operatorId") long operatorId,
                     @Param("reason") String reason, @Param("fullName") String fullName,
                     @Param("documentType") String documentType, @Param("documentNumber") String documentNumber,
                     @Param("identityKey") String identityKey, @Param("gender") String gender,
                     @Param("birthDate") LocalDate birthDate, @Param("validFrom") LocalDate validFrom,
                     @Param("validUntil") LocalDate validUntil, @Param("json") String json,
                     @Param("now") Instant now);

    @Insert("""
        insert into profile_person_version(person_version_id, person_profile_id, version_no, source_type, source_id,
            full_name, document_type_code, document_number, identity_key, gender, birth_date, valid_from,
            valid_until, status, published_time, version, create_dept, create_time, create_by, update_time,
            update_by, del_flag)
        values(#{versionId}, #{profileId}, #{versionNo}, #{sourceType}, #{sourceId}, #{fullName}, #{documentType},
            #{documentNumber}, #{identityKey}, #{gender}, #{birthDate}, #{validFrom}, #{validUntil}, 'CURRENT',
            #{now}, 0, -1, #{now}, #{operatorId}, #{now}, #{operatorId}, '0')
        """)
    int insertVersion(@Param("versionId") long versionId, @Param("profileId") long profileId,
                      @Param("versionNo") int versionNo, @Param("sourceType") String sourceType,
                      @Param("sourceId") long sourceId, @Param("fullName") String fullName,
                      @Param("documentType") String documentType, @Param("documentNumber") String documentNumber,
                      @Param("identityKey") String identityKey, @Param("gender") String gender,
                      @Param("birthDate") LocalDate birthDate, @Param("validFrom") LocalDate validFrom,
                      @Param("validUntil") LocalDate validUntil, @Param("operatorId") long operatorId,
                      @Param("now") Instant now);

    @Update("""
        update profile_person_version set status = 'SUPERSEDED', version = version + 1,
               update_time = #{now}, update_by = #{operatorId}
         where person_version_id = #{versionId} and status = 'CURRENT' and del_flag = '0'
        """)
    int supersedeVersion(@Param("versionId") long versionId, @Param("operatorId") long operatorId,
                         @Param("now") Instant now);

    @Update("""
        update profile_person set current_version_id = #{versionId}, full_name = #{fullName},
               document_type_code = #{documentType}, document_number = #{documentNumber}, identity_key = #{identityKey},
               gender = #{gender}, birth_date = #{birthDate}, valid_from = #{validFrom}, valid_until = #{validUntil},
               version = version + 1, update_time = #{now}, update_by = #{operatorId}
         where person_profile_id = #{profileId} and status = 'ACTIVE' and version = #{expectedVersion}
           and del_flag = '0'
        """)
    int updateProfileVersion(@Param("profileId") long profileId, @Param("versionId") long versionId,
                             @Param("fullName") String fullName, @Param("documentType") String documentType,
                             @Param("documentNumber") String documentNumber, @Param("identityKey") String identityKey,
                             @Param("gender") String gender, @Param("birthDate") LocalDate birthDate,
                             @Param("validFrom") LocalDate validFrom, @Param("validUntil") LocalDate validUntil,
                             @Param("expectedVersion") int expectedVersion, @Param("operatorId") long operatorId,
                             @Param("now") Instant now);

    @Insert("""
        insert into profile_person_binding(person_binding_id, person_profile_id, user_id, status, binding_version,
            source_type, source_id, bound_time, version, create_dept, create_time, create_by, update_time,
            update_by, del_flag)
        values(#{bindingId}, #{profileId}, #{userId}, 'ACTIVE', 1, #{sourceType}, #{sourceId}, #{now}, 0,
            -1, #{now}, #{operatorId}, #{now}, #{operatorId}, '0')
        """)
    int insertBinding(@Param("bindingId") long bindingId, @Param("profileId") long profileId,
                      @Param("userId") long userId, @Param("sourceType") String sourceType,
                      @Param("sourceId") Long sourceId, @Param("operatorId") long operatorId,
                      @Param("now") Instant now);

    @Update("""
        update profile_person_binding set status = #{targetStatus}, binding_version = binding_version + 1,
               unbound_time = case when #{targetStatus} = 'UNBOUND' then #{now} else unbound_time end,
               update_time = #{now}, update_by = #{operatorId}
         where person_binding_id = #{bindingId} and binding_version = #{expectedVersion}
           and status = #{sourceStatus} and del_flag = '0'
        """)
    int updateBinding(@Param("bindingId") long bindingId, @Param("sourceStatus") String sourceStatus,
                      @Param("targetStatus") String targetStatus, @Param("expectedVersion") int expectedVersion,
                      @Param("operatorId") long operatorId, @Param("now") Instant now);

    @Insert("""
        insert into profile_person_binding_event(person_binding_event_id, person_binding_id, person_profile_id,
            user_id, event_type, binding_version, source_type, source_id, reason, occurred_time, version,
            create_dept, create_time, create_by, update_time, update_by, del_flag)
        values(#{eventId}, #{bindingId}, #{profileId}, #{userId}, #{eventType}, #{bindingVersion},
            'ADMIN_OVERRIDE', #{sourceId}, #{reason}, #{now}, 0, -1, #{now}, #{operatorId},
            #{now}, #{operatorId}, '0')
        """)
    int insertBindingEvent(@Param("eventId") long eventId, @Param("bindingId") long bindingId,
                           @Param("profileId") long profileId, @Param("userId") long userId,
                           @Param("eventType") String eventType, @Param("bindingVersion") int bindingVersion,
                           @Param("sourceId") Long sourceId, @Param("reason") String reason,
                           @Param("operatorId") long operatorId, @Param("now") Instant now);

    @Insert("""
        insert into profile_material_ref(material_ref_id, owner_type, owner_id, profile_type, oss_id,
            material_node_id, material_tag_code, material_tag_name, file_name, file_size, file_extension,
            mime_type, status, immutable_flag, attached_time, detached_time, version, create_dept, create_time,
            create_by, update_time, update_by, del_flag)
        select #{newIdBase} + row_number() over(order by material_ref_id), 'SOURCE', #{sourceId}, profile_type,
               oss_id, material_node_id, material_tag_code, material_tag_name, file_name, file_size,
               file_extension, mime_type, 'ATTACHED', 'Y', #{now}, null, 0, -1, #{now}, #{operatorId},
               #{now}, #{operatorId}, '0' from profile_material_ref
         where profile_type = 'PERSON' and owner_type = 'VERSION' and owner_id = #{versionId}
           and status = 'ATTACHED' and del_flag = '0'
        """)
    int cloneVersionMaterials(@Param("versionId") long versionId, @Param("sourceId") long sourceId,
                              @Param("newIdBase") long newIdBase, @Param("operatorId") long operatorId,
                              @Param("now") Instant now);

    @Update("""
        update profile_person set status = 'REVOKED', revoked_time = #{now}, revoked_reason = #{reason},
               version = version + 1, update_time = #{now}, update_by = #{operatorId}
         where person_profile_id = #{profileId} and status = 'ACTIVE' and version = #{expectedVersion}
           and del_flag = '0'
        """)
    int revokeProfile(@Param("profileId") long profileId, @Param("expectedVersion") int expectedVersion,
                      @Param("reason") String reason, @Param("operatorId") long operatorId,
                      @Param("now") Instant now);

    @Insert("""
        insert into profile_operation_audit(operation_audit_id, profile_type, profile_id, application_id,
            binding_id, operation_type, operator_user_id, capability, reason, before_status, after_status,
            result, failure_category, occurred_time, version, create_dept, create_time, create_by,
            update_time, update_by, del_flag)
        values(#{auditId}, 'PERSON', #{profileId}, #{applicationId}, #{bindingId}, #{operationType},
            #{operatorId}, #{capability}, #{reason}, #{beforeStatus}, #{afterStatus}, 'SUCCESS', null,
            #{now}, 0, -1, #{now}, #{operatorId}, #{now}, #{operatorId}, '0')
        """)
    int insertAudit(@Param("auditId") long auditId, @Param("profileId") Long profileId,
                    @Param("applicationId") Long applicationId, @Param("bindingId") Long bindingId,
                    @Param("operationType") String operationType, @Param("operatorId") long operatorId,
                    @Param("capability") String capability, @Param("reason") String reason,
                    @Param("beforeStatus") String beforeStatus, @Param("afterStatus") String afterStatus,
                    @Param("now") Instant now);
}

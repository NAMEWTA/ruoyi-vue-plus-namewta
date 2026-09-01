package org.dromara.profile.person.persistence.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.profile.person.persistence.row.PersonActiveProjectionRow;
import org.dromara.profile.person.persistence.row.PersonApplicationRow;
import org.dromara.profile.person.persistence.row.PersonBindingEventRow;
import org.dromara.profile.person.persistence.row.PersonBindingRow;
import org.dromara.profile.person.persistence.row.PersonDocumentTypeRow;
import org.dromara.profile.person.persistence.row.PersonProfileRow;
import org.dromara.profile.person.persistence.row.PersonSubmissionRow;
import org.dromara.profile.person.persistence.row.PersonVersionRow;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface PersonApplicationMapper {

    String APPLICATION_COLUMNS = """
        person_application_id, applicant_user_id, target_profile_id, status, full_name,
        document_type_code, document_number, identity_key, gender, birth_date, valid_from,
        valid_until, provider_code, submission_seq, rebind_intent, expected_binding_id,
        expected_binding_version, decision_version, version, submitted_time, finished_time
        """;

    String PROFILE_COLUMNS = """
        person_profile_id, previous_profile_id, current_version_id, full_name,
        document_type_code, document_number, identity_key, gender, birth_date,
        valid_from, valid_until, status, version
        """;

    @Select("select " + APPLICATION_COLUMNS + " from profile_person_application"
        + " where applicant_user_id = #{userId} and status in ('DRAFT','BACK','CANCEL','WAITING')"
        + " and del_flag = '0'")
    PersonApplicationRow selectOpenByUserId(@Param("userId") long userId);

    @Select("select " + APPLICATION_COLUMNS + " from profile_person_application"
        + " where applicant_user_id = #{userId} and status in ('DRAFT','BACK','CANCEL','WAITING')"
        + " and del_flag = '0' for update")
    PersonApplicationRow lockOpenByUserId(@Param("userId") long userId);

    @Select("select " + APPLICATION_COLUMNS + " from profile_person_application"
        + " where person_application_id = #{applicationId} and del_flag = '0' for update")
    PersonApplicationRow lockApplicationById(@Param("applicationId") long applicationId);

    @Select("""
        select document_type_code, number_pattern, validity_required
          from profile_document_type
         where document_type_code = #{code} and status = '0' and del_flag = '0'
        """)
    PersonDocumentTypeRow selectDocumentType(@Param("code") String documentTypeCode);

    @Select("""
        select person_profile_id
          from profile_person
         where identity_key = #{identityKey} and status = 'ACTIVE' and del_flag = '0'
        """)
    Long selectActiveProfileIdByIdentity(@Param("identityKey") String identityKey);

    @Select("""
        select b.person_profile_id
          from profile_person_binding b
          join profile_person p on p.person_profile_id = b.person_profile_id
         where b.user_id = #{userId} and b.status in ('ACTIVE','SUSPENDED') and b.del_flag = '0'
           and p.status = 'ACTIVE' and p.del_flag = '0'
        """)
    Long selectEffectiveProfileIdByUser(@Param("userId") long userId);

    @Insert("""
        insert into profile_person_application (
            person_application_id, applicant_user_id, target_profile_id, status, full_name,
            document_type_code, document_number, identity_key, gender, birth_date, valid_from,
            valid_until, provider_code, submission_seq, rebind_intent, decision_version, version,
            create_dept, create_time, create_by, update_time, update_by, del_flag
        ) values (
            #{personApplicationId}, #{applicantUserId}, #{targetProfileId}, 'DRAFT', #{fullName},
            #{documentTypeCode}, #{documentNumber}, #{identityKey}, #{gender}, #{birthDate}, #{validFrom},
            #{validUntil}, #{providerCode}, 0, 'N', 0, 0,
            -1, current_timestamp, #{applicantUserId}, current_timestamp, #{applicantUserId}, '0'
        )
        """)
    int insertApplication(PersonApplicationRow row);

    @Update("""
        update profile_person_application
           set target_profile_id = #{targetProfileId}, status = 'DRAFT', full_name = #{fullName},
               document_type_code = #{documentTypeCode}, document_number = #{documentNumber},
               identity_key = #{identityKey}, gender = #{gender}, birth_date = #{birthDate},
               valid_from = #{validFrom}, valid_until = #{validUntil}, version = version + 1,
               update_time = current_timestamp, update_by = #{applicantUserId}
         where person_application_id = #{personApplicationId} and applicant_user_id = #{applicantUserId}
           and status in ('DRAFT','BACK','CANCEL') and version = #{version} and del_flag = '0'
        """)
    int updateDraft(PersonApplicationRow row);

    @Insert("""
        insert into profile_person_submission (
            person_submission_id, person_application_id, submission_seq, full_name,
            document_type_code, document_number, identity_key, gender, birth_date, valid_from,
            valid_until, provider_code, rebind_intent, target_profile_id, expected_binding_id,
            expected_binding_version, field_snapshot_json, submitted_time, version,
            create_dept, create_time, create_by, update_time, update_by, del_flag
        ) values (
            #{personSubmissionId}, #{personApplicationId}, #{submissionSeq}, #{fullName},
            #{documentTypeCode}, #{documentNumber}, #{identityKey}, #{gender}, #{birthDate}, #{validFrom},
            #{validUntil}, #{providerCode}, #{rebindIntent}, #{targetProfileId}, #{expectedBindingId},
            #{expectedBindingVersion}, #{fieldSnapshotJson}, #{submittedTime}, 0,
            -1, current_timestamp, #{applicantUserId}, current_timestamp, #{applicantUserId}, '0'
        )
        """)
    int insertSubmission(PersonSubmissionRow row);

    @Select("""
        select s.person_submission_id, s.person_application_id, s.submission_seq,
               a.applicant_user_id, s.full_name, s.document_type_code, s.document_number,
               s.identity_key, s.gender, s.birth_date, s.valid_from, s.valid_until,
               s.provider_code, s.rebind_intent, s.target_profile_id, s.expected_binding_id,
               s.expected_binding_version, s.field_snapshot_json, s.submitted_time
          from profile_person_submission s
          join profile_person_application a on a.person_application_id = s.person_application_id
         where s.person_application_id = #{applicationId} and s.submission_seq = #{submissionSeq}
           and s.del_flag = '0' and a.del_flag = '0'
        """)
    PersonSubmissionRow selectSubmission(@Param("applicationId") long applicationId,
                                         @Param("submissionSeq") int submissionSeq);

    @Update("""
        update profile_person_application
           set status = 'WAITING', submission_seq = #{submissionSeq}, submitted_time = #{submittedTime},
               finished_time = null, decision_source = null, decision_result = null, decision_reason = null,
               version = version + 1, update_time = current_timestamp, update_by = applicant_user_id
         where person_application_id = #{applicationId} and submission_seq = #{submissionSeq} - 1
           and status in ('DRAFT','BACK','CANCEL') and version = #{expectedVersion} and del_flag = '0'
        """)
    int markWaiting(@Param("applicationId") long applicationId,
                    @Param("submissionSeq") int submissionSeq,
                    @Param("expectedVersion") int expectedVersion,
                    @Param("submittedTime") Instant submittedTime);

    @Select("select " + PROFILE_COLUMNS + " from profile_person"
        + " where identity_key = #{identityKey} and status = 'ACTIVE' and del_flag = '0' for update")
    PersonProfileRow lockActiveProfileByIdentity(@Param("identityKey") String identityKey);

    @Select("select " + PROFILE_COLUMNS + " from profile_person"
        + " where person_profile_id = #{profileId} and status = 'ACTIVE' and del_flag = '0' for update")
    PersonProfileRow lockActiveProfileById(@Param("profileId") long profileId);

    @Update("""
        update profile_identity_guard
           set identity_key = #{identityKey}, version = version + 1,
               update_time = current_timestamp, update_by = -1
         where profile_type = 'PERSON' and owner_type = 'PROFILE' and owner_id = #{profileId}
           and status = 'ACTIVE' and del_flag = '0'
        """)
    int updateIdentityGuard(@Param("profileId") long profileId,
                            @Param("identityKey") String identityKey);

    @Select("select " + PROFILE_COLUMNS + " from profile_person"
        + " where identity_key = #{identityKey} and status = 'REVOKED' and del_flag = '0'"
        + " order by revoked_time desc, person_profile_id desc limit 1 for update")
    PersonProfileRow lockLatestRevokedProfileByIdentity(@Param("identityKey") String identityKey);

    @Insert("""
        insert into profile_identity_guard (
            identity_guard_id, profile_type, identity_key, owner_type, owner_id, status,
            version, create_dept, create_time, create_by, update_time, update_by, del_flag
        ) values (
            #{guardId}, 'PERSON', #{identityKey}, 'PROFILE', #{profileId}, 'ACTIVE',
            0, -1, current_timestamp, -1, current_timestamp, -1, '0'
        )
        """)
    int insertIdentityGuard(@Param("guardId") long guardId,
                            @Param("identityKey") String identityKey,
                            @Param("profileId") long profileId);

    @Insert("""
        insert into profile_person (
            person_profile_id, previous_profile_id, current_version_id, full_name,
            document_type_code, document_number, identity_key, gender, birth_date, valid_from,
            valid_until, status, version, create_dept, create_time, create_by,
            update_time, update_by, del_flag
        ) values (
            #{personProfileId}, #{previousProfileId}, null, #{fullName},
            #{documentTypeCode}, #{documentNumber}, #{identityKey}, #{gender}, #{birthDate}, #{validFrom},
            #{validUntil}, 'ACTIVE', 0, -1, current_timestamp, -1,
            current_timestamp, -1, '0'
        )
        """)
    int insertProfile(PersonProfileRow row);

    @Select("""
        select person_version_id, person_profile_id, version_no, source_type, source_id,
               full_name, document_type_code, document_number, identity_key, gender,
               birth_date, valid_from, valid_until, status, published_time
          from profile_person_version
         where person_profile_id = #{profileId} and status = 'CURRENT' and del_flag = '0'
         for update
        """)
    PersonVersionRow selectCurrentVersionForUpdate(@Param("profileId") long profileId);

    @Update("""
        update profile_person_version
           set status = 'SUPERSEDED', version = version + 1,
               update_time = current_timestamp, update_by = -1
         where person_version_id = #{versionId} and status = 'CURRENT' and del_flag = '0'
        """)
    int supersedeVersion(@Param("versionId") long personVersionId);

    @Insert("""
        insert into profile_person_version (
            person_version_id, person_profile_id, version_no, source_type, source_id,
            full_name, document_type_code, document_number, identity_key, gender, birth_date,
            valid_from, valid_until, status, published_time, version,
            create_dept, create_time, create_by, update_time, update_by, del_flag
        ) values (
            #{personVersionId}, #{personProfileId}, #{versionNo}, #{sourceType}, #{sourceId},
            #{fullName}, #{documentTypeCode}, #{documentNumber}, #{identityKey}, #{gender}, #{birthDate},
            #{validFrom}, #{validUntil}, 'CURRENT', #{publishedTime}, 0,
            -1, current_timestamp, -1, current_timestamp, -1, '0'
        )
        """)
    int insertVersion(PersonVersionRow row);

    @Update("""
        update profile_person
           set current_version_id = #{currentVersionId}, full_name = #{fullName},
               document_type_code = #{documentTypeCode}, document_number = #{documentNumber},
               identity_key = #{identityKey}, gender = #{gender}, birth_date = #{birthDate},
               valid_from = #{validFrom}, valid_until = #{validUntil}, status = 'ACTIVE',
               version = version + 1, update_time = current_timestamp, update_by = -1
         where person_profile_id = #{personProfileId} and status = 'ACTIVE'
           and version = #{version} and del_flag = '0'
        """)
    int updateProfile(PersonProfileRow row);

    @Select("""
        select person_binding_id, person_profile_id, user_id, status, binding_version,
               source_type, source_id, bound_time
          from profile_person_binding
         where user_id = #{userId} and status in ('ACTIVE','SUSPENDED') and del_flag = '0'
         for update
        """)
    PersonBindingRow lockEffectiveBindingByUser(@Param("userId") long userId);

    @Select("""
        select person_binding_id, person_profile_id, user_id, status, binding_version,
               source_type, source_id, bound_time
          from profile_person_binding
         where person_profile_id = #{profileId} and status in ('ACTIVE','SUSPENDED') and del_flag = '0'
         for update
        """)
    PersonBindingRow lockEffectiveBindingByProfile(@Param("profileId") long personProfileId);

    @Insert("""
        insert into profile_person_binding (
            person_binding_id, person_profile_id, user_id, status, binding_version,
            source_type, source_id, bound_time, version,
            create_dept, create_time, create_by, update_time, update_by, del_flag
        ) values (
            #{personBindingId}, #{personProfileId}, #{userId}, 'ACTIVE', #{bindingVersion},
            #{sourceType}, #{sourceId}, #{boundTime}, 0,
            -1, current_timestamp, -1, current_timestamp, -1, '0'
        )
        """)
    int insertBinding(PersonBindingRow row);

    @Insert("""
        insert into profile_person_binding_event (
            person_binding_event_id, person_binding_id, person_profile_id, user_id,
            event_type, binding_version, source_type, source_id, reason, occurred_time,
            version, create_dept, create_time, create_by, update_time, update_by, del_flag
        ) values (
            #{personBindingEventId}, #{personBindingId}, #{personProfileId}, #{userId},
            #{eventType}, #{bindingVersion}, #{sourceType}, #{sourceId}, #{reason}, #{occurredTime},
            0, -1, current_timestamp, -1, current_timestamp, -1, '0'
        )
        """)
    int insertBindingEvent(PersonBindingEventRow row);

    @Update("""
        update profile_person_application
           set status = 'FINISH', decision_version = decision_version + 1,
               decision_source = 'WORKFLOW', decision_result = 'APPROVED', finished_time = #{finishedTime},
               version = version + 1, update_time = current_timestamp, update_by = -1
         where person_application_id = #{applicationId} and submission_seq = #{snapshotVersion}
           and status = 'WAITING' and decision_version = #{decisionVersion}
           and version = #{expectedVersion} and del_flag = '0'
        """)
    int finishApplication(@Param("applicationId") long applicationId,
                          @Param("snapshotVersion") int snapshotVersion,
                          @Param("decisionVersion") int decisionVersion,
                          @Param("expectedVersion") int expectedVersion,
                          @Param("finishedTime") Instant finishedTime);

    @Update("""
        update profile_person_application
           set status = #{status}, decision_version = decision_version + 1,
               decision_source = 'WORKFLOW', decision_result = #{status},
               finished_time = case when #{status} in ('INVALID','TERMINATION') then #{occurredTime} else null end,
               version = version + 1, update_time = current_timestamp, update_by = -1
         where person_application_id = #{applicationId} and submission_seq = #{snapshotVersion}
           and status = 'WAITING' and version = #{expectedVersion} and del_flag = '0'
        """)
    int updateWorkflowStatus(@Param("applicationId") long applicationId,
                             @Param("snapshotVersion") int snapshotVersion,
                             @Param("status") String status,
                             @Param("expectedVersion") int expectedVersion,
                             @Param("occurredTime") Instant occurredTime);

    @Select("""
        <script>
        select b.user_id, b.person_profile_id, v.published_time as verified_at
          from profile_person_binding b
          join profile_person p on p.person_profile_id = b.person_profile_id
          join profile_person_version v on v.person_version_id = p.current_version_id
         where b.status = 'ACTIVE' and b.del_flag = '0'
           and p.status = 'ACTIVE' and p.del_flag = '0'
           and v.status = 'CURRENT' and v.del_flag = '0'
           and b.user_id in
           <foreach collection="userIds" item="userId" open="(" separator="," close=")">#{userId}</foreach>
         order by b.user_id
        </script>
        """)
    List<PersonActiveProjectionRow> selectActiveProjections(@Param("userIds") Set<Long> userIds);
}

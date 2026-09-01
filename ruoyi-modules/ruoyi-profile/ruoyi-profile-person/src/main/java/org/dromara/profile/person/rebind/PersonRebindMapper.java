package org.dromara.profile.person.rebind;

import lombok.Data;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.profile.person.persistence.row.PersonApplicationRow;
import org.dromara.profile.person.persistence.row.PersonBindingEventRow;
import org.dromara.profile.person.persistence.row.PersonBindingRow;
import org.dromara.profile.person.persistence.row.PersonProfileRow;
import org.dromara.profile.person.persistence.row.PersonSubmissionRow;
import org.dromara.profile.person.persistence.row.PersonVersionRow;

import java.time.Instant;
import java.time.LocalDate;

public interface PersonRebindMapper {

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

    @Select("""
        select case
                 when exists (
                     select 1 from profile_person p
                     join profile_person_binding b on b.person_profile_id = p.person_profile_id
                      where p.identity_key = #{identityKey} and p.status = 'ACTIVE' and p.del_flag = '0'
                        and b.status in ('ACTIVE','SUSPENDED') and b.del_flag = '0'
                 ) then 'BOUND'
                 when exists (
                     select 1 from profile_person_application a
                      where a.identity_key = #{identityKey}
                        and a.status in ('DRAFT','BACK','CANCEL','WAITING') and a.del_flag = '0'
                 ) then 'IN_PROGRESS'
                 when exists (
                     select 1 from profile_person p
                      where p.identity_key = #{identityKey} and p.status = 'ACTIVE' and p.del_flag = '0'
                 ) then 'UNBOUND'
                 else 'AVAILABLE'
               end
        """)
    String selectProbeStatus(@Param("identityKey") String identityKey);

    @Select("""
        select p.person_profile_id, p.full_name, p.document_type_code, p.document_number,
               p.identity_key, p.gender, p.birth_date, p.valid_from, p.valid_until,
               b.person_binding_id, b.user_id as old_user_id, b.binding_version
          from profile_person p
          join profile_person_binding b on b.person_profile_id = p.person_profile_id
         where p.identity_key = #{identityKey} and p.status = 'ACTIVE' and p.del_flag = '0'
           and p.full_name = #{fullName} and p.document_type_code = #{documentTypeCode}
           and p.document_number = #{documentNumber} and p.gender = #{gender}
           and p.birth_date = #{birthDate} and p.valid_from <=> #{validFrom}
           and p.valid_until <=> #{validUntil}
           and b.status = 'ACTIVE' and b.del_flag = '0'
        """)
    RebindCandidateRow selectExactCandidate(@Param("fullName") String fullName,
                                            @Param("documentTypeCode") String documentTypeCode,
                                            @Param("documentNumber") String documentNumber,
                                            @Param("identityKey") String identityKey,
                                            @Param("gender") String gender,
                                            @Param("birthDate") LocalDate birthDate,
                                            @Param("validFrom") LocalDate validFrom,
                                            @Param("validUntil") LocalDate validUntil);

    @Select("select " + APPLICATION_COLUMNS + " from profile_person_application"
        + " where applicant_user_id = #{userId} and status in ('DRAFT','BACK','CANCEL','WAITING')"
        + " and del_flag = '0' for update")
    PersonApplicationRow lockOpenApplication(@Param("userId") long userId);

    @Select("select " + APPLICATION_COLUMNS + " from profile_person_application"
        + " where person_application_id = #{applicationId} and del_flag = '0' for update")
    PersonApplicationRow lockApplication(@Param("applicationId") long applicationId);

    @Select("""
        select person_binding_id, person_profile_id, user_id, status, binding_version,
               source_type, source_id, bound_time
          from profile_person_binding
         where user_id = #{userId} and status in ('ACTIVE','SUSPENDED') and del_flag = '0'
         for update
        """)
    PersonBindingRow lockEffectiveBindingByUser(@Param("userId") long userId);

    @Select("""
        select p.person_profile_id, p.full_name, p.document_type_code, p.document_number,
               p.identity_key, p.gender, p.birth_date, p.valid_from, p.valid_until,
               b.person_binding_id, b.user_id as old_user_id, b.binding_version
          from profile_person p
          join profile_person_binding b on b.person_profile_id = p.person_profile_id
         where p.person_profile_id = #{profileId} and p.status = 'ACTIVE' and p.del_flag = '0'
           and b.person_binding_id = #{bindingId} and b.binding_version = #{bindingVersion}
           and b.status = 'ACTIVE' and b.del_flag = '0'
         for update
        """)
    RebindCandidateRow lockFrozenCandidate(@Param("profileId") long profileId,
                                           @Param("bindingId") long bindingId,
                                           @Param("bindingVersion") int bindingVersion);

    @Update("""
        update profile_person_application
           set target_profile_id = #{profileId}, rebind_intent = 'Y',
               expected_binding_id = #{bindingId}, expected_binding_version = #{bindingVersion},
               version = version + 1, update_time = current_timestamp, update_by = #{userId}
         where person_application_id = #{applicationId} and applicant_user_id = #{userId}
           and status in ('DRAFT','BACK','CANCEL') and version = #{expectedVersion} and del_flag = '0'
        """)
    int confirmIntent(@Param("applicationId") long applicationId,
                      @Param("userId") long userId,
                      @Param("profileId") long profileId,
                      @Param("bindingId") long bindingId,
                      @Param("bindingVersion") int bindingVersion,
                      @Param("expectedVersion") int expectedVersion);

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
         for update
        """)
    PersonSubmissionRow lockSubmission(@Param("applicationId") long applicationId,
                                        @Param("submissionSeq") int submissionSeq);

    @Select("select " + PROFILE_COLUMNS + " from profile_person"
        + " where person_profile_id = #{profileId} and status = 'ACTIVE' and del_flag = '0' for update")
    PersonProfileRow lockProfile(@Param("profileId") long profileId);

    @Select("""
        select person_binding_id, person_profile_id, user_id, status, binding_version,
               source_type, source_id, bound_time
          from profile_person_binding
         where person_binding_id = #{bindingId} and person_profile_id = #{profileId}
           and binding_version = #{bindingVersion} and status = 'ACTIVE' and del_flag = '0'
         for update
        """)
    PersonBindingRow lockExpectedBinding(@Param("bindingId") long bindingId,
                                         @Param("profileId") long profileId,
                                         @Param("bindingVersion") int bindingVersion);

    @Select("""
        select person_version_id, person_profile_id, version_no, source_type, source_id,
               full_name, document_type_code, document_number, identity_key, gender,
               birth_date, valid_from, valid_until, status, published_time
          from profile_person_version
         where person_profile_id = #{profileId} and status = 'CURRENT' and del_flag = '0'
         for update
        """)
    PersonVersionRow lockCurrentVersion(@Param("profileId") long profileId);

    @Update("""
        update profile_person_version
           set status = 'SUPERSEDED', version = version + 1,
               update_time = current_timestamp, update_by = -1
         where person_version_id = #{versionId} and status = 'CURRENT' and del_flag = '0'
        """)
    int supersedeVersion(@Param("versionId") long versionId);

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
               valid_from = #{validFrom}, valid_until = #{validUntil}, version = version + 1,
               update_time = current_timestamp, update_by = -1
         where person_profile_id = #{personProfileId} and status = 'ACTIVE'
           and version = #{version} and del_flag = '0'
        """)
    int updateProfile(PersonProfileRow row);

    @Update("""
        update profile_person_binding
           set status = 'UNBOUND', binding_version = binding_version + 1,
               unbound_time = #{occurredTime}, version = version + 1,
               update_time = current_timestamp, update_by = #{actorUserId}
         where person_binding_id = #{bindingId} and binding_version = #{bindingVersion}
           and status in ('ACTIVE','SUSPENDED') and del_flag = '0'
        """)
    int unbind(@Param("bindingId") long bindingId,
               @Param("bindingVersion") int bindingVersion,
               @Param("actorUserId") long actorUserId,
               @Param("occurredTime") Instant occurredTime);

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
           and version = #{expectedVersion} and rebind_intent = 'Y' and del_flag = '0'
        """)
    int finishApplication(@Param("applicationId") long applicationId,
                          @Param("snapshotVersion") int snapshotVersion,
                          @Param("decisionVersion") int decisionVersion,
                          @Param("expectedVersion") int expectedVersion,
                          @Param("finishedTime") Instant finishedTime);

    @Data
    class RebindCandidateRow {
        private Long personProfileId;
        private String fullName;
        private String documentTypeCode;
        private String documentNumber;
        private String identityKey;
        private String gender;
        private LocalDate birthDate;
        private LocalDate validFrom;
        private LocalDate validUntil;
        private Long personBindingId;
        private Long oldUserId;
        private Integer bindingVersion;
    }
}

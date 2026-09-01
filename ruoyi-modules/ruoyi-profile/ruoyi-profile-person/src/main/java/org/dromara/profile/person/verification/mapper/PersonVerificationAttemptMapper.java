package org.dromara.profile.person.verification.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.profile.person.verification.persistence.PersonVerificationApplicationRow;
import org.dromara.profile.person.verification.persistence.PersonVerificationAttemptRow;

import java.time.Instant;

public interface PersonVerificationAttemptMapper {

    @Select("""
        select a.person_application_id as application_id,
               s.person_submission_id as submission_id,
               a.provider_code,
               a.status
          from profile_person_application a
          join profile_person_submission s
            on s.person_application_id = a.person_application_id
           and s.submission_seq = a.submission_seq
           and s.del_flag = '0'
         where a.person_application_id = #{applicationId}
           and a.del_flag = '0'
         for update
        """)
    PersonVerificationApplicationRow lockApplication(@Param("applicationId") long applicationId);

    @Select("""
        select coalesce(max(attempt_no), 0) + 1
          from profile_verification_attempt
         where profile_type = 'PERSON'
           and application_id = #{applicationId}
           and del_flag = '0'
        """)
    Integer nextAttemptNo(@Param("applicationId") long applicationId);

    @Insert("""
        insert into profile_verification_attempt (
            verification_attempt_id, profile_type, application_id, submission_id,
            provider_code, provider_request_id, request_fingerprint, attempt_no,
            status, normalized_result_json, provider_evidence_json, error_code,
            completed_time, version, create_dept, create_time, create_by,
            update_time, update_by, del_flag
        ) values (
            #{verificationAttemptId}, 'PERSON', #{applicationId}, #{submissionId},
            #{providerCode}, #{providerRequestId}, #{requestFingerprint}, #{attemptNo},
            #{status}, #{normalizedResultJson}, #{providerEvidenceJson}, #{errorCode},
            #{completedTime}, 0, -1, current_timestamp, -1,
            current_timestamp, -1, '0'
        )
        """)
    int insertAttempt(PersonVerificationAttemptRow row);

    @Select("""
        select verification_attempt_id, application_id, submission_id, provider_code,
               provider_request_id, request_fingerprint, attempt_no, status,
               normalized_result_json, provider_evidence_json, error_code, completed_time
          from profile_verification_attempt
         where profile_type = 'PERSON'
           and provider_code = #{providerCode}
           and provider_request_id = #{providerRequestId}
           and del_flag = '0'
         for update
        """)
    PersonVerificationAttemptRow lockByProviderRequest(@Param("providerCode") String providerCode,
                                                        @Param("providerRequestId") String providerRequestId);

    @Update("""
        update profile_verification_attempt
           set status = #{status},
               normalized_result_json = #{normalizedResultJson},
               provider_evidence_json = #{providerEvidenceJson},
               error_code = #{errorCode},
               completed_time = #{completedTime},
               version = version + 1,
               update_time = current_timestamp,
               update_by = -1
         where verification_attempt_id = #{verificationAttemptId}
           and profile_type = 'PERSON'
           and status = 'PENDING'
           and del_flag = '0'
        """)
    int completeAttempt(@Param("verificationAttemptId") long verificationAttemptId,
                        @Param("status") String status,
                        @Param("normalizedResultJson") String normalizedResultJson,
                        @Param("providerEvidenceJson") String providerEvidenceJson,
                        @Param("errorCode") String errorCode,
                        @Param("completedTime") Instant completedTime);

    @Insert("""
        insert into profile_operation_audit (
            operation_audit_id, profile_type, application_id, operation_type,
            operator_user_id, capability, result, failure_category, occurred_time,
            version, create_dept, create_time, create_by, update_time, update_by, del_flag
        ) values (
            #{auditId}, 'PERSON', #{applicationId}, 'PROVIDER_CALLBACK',
            0, 'profile:person:providerCallback', #{result}, #{failureCategory}, #{occurredTime},
            0, -1, current_timestamp, -1, current_timestamp, -1, '0'
        )
        """)
    int insertSecurityAudit(@Param("auditId") long auditId,
                            @Param("applicationId") Long applicationId,
                            @Param("result") String result,
                            @Param("failureCategory") String failureCategory,
                            @Param("occurredTime") Instant occurredTime);
}

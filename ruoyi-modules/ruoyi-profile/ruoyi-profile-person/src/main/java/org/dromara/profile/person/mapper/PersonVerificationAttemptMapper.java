package org.dromara.profile.person.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.profile.person.domain.ProfileVerificationAttempt;
import org.apache.ibatis.annotations.Param;
import org.dromara.profile.person.domain.vo.PersonVerificationApplicationRow;
import org.dromara.profile.person.domain.vo.PersonVerificationAttemptRow;

import java.time.Instant;

public interface PersonVerificationAttemptMapper extends BaseMapperPlus<ProfileVerificationAttempt, ProfileVerificationAttempt> {

    PersonVerificationApplicationRow lockApplication(@Param("applicationId") long applicationId);

    Integer nextAttemptNo(@Param("applicationId") long applicationId);

    int insertAttempt(PersonVerificationAttemptRow row);

    PersonVerificationAttemptRow lockByProviderRequest(@Param("providerCode") String providerCode,
                                                        @Param("providerRequestId") String providerRequestId);

    int completeAttempt(@Param("verificationAttemptId") long verificationAttemptId,
                        @Param("status") String status,
                        @Param("normalizedResultJson") String normalizedResultJson,
                        @Param("providerEvidenceJson") String providerEvidenceJson,
                        @Param("errorCode") String errorCode,
                        @Param("completedTime") Instant completedTime);

    int insertSecurityAudit(@Param("auditId") long auditId,
                            @Param("applicationId") Long applicationId,
                            @Param("result") String result,
                            @Param("failureCategory") String failureCategory,
                            @Param("occurredTime") Instant occurredTime);
}

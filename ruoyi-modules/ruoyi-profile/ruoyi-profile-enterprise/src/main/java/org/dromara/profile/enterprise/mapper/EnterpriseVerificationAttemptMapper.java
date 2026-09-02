package org.dromara.profile.enterprise.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.profile.enterprise.domain.ProfileVerificationAttempt;
import org.dromara.profile.enterprise.domain.verification.EnterpriseVerificationAttempt;

import org.apache.ibatis.annotations.Param;
import org.dromara.profile.enterprise.domain.vo.EnterpriseVerificationApplicationRow;
import org.dromara.profile.enterprise.domain.vo.EnterpriseVerificationAttemptRow;

import java.time.Instant;

public interface EnterpriseVerificationAttemptMapper extends BaseMapperPlus<ProfileVerificationAttempt, EnterpriseVerificationAttempt> {

    EnterpriseVerificationApplicationRow lockApplication(@Param("applicationId") long applicationId);

    Integer nextAttemptNo(@Param("applicationId") long applicationId);

    int insertAttempt(EnterpriseVerificationAttemptRow row);

    EnterpriseVerificationAttemptRow lockByProviderRequest(@Param("providerCode") String providerCode,
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

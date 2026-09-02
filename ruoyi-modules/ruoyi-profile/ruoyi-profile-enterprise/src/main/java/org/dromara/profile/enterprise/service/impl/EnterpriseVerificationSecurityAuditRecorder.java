package org.dromara.profile.enterprise.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.dromara.profile.enterprise.domain.exception.EnterpriseVerificationException;
import org.dromara.profile.enterprise.domain.verification.EnterpriseVerificationFailureCategory;
import org.dromara.profile.enterprise.mapper.EnterpriseVerificationAttemptMapper;
import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import com.baomidou.dynamic.datasource.tx.DsPropagation;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class EnterpriseVerificationSecurityAuditRecorder {

    private final EnterpriseVerificationAttemptMapper mapper;

    public EnterpriseVerificationSecurityAuditRecorder(EnterpriseVerificationAttemptMapper mapper) {
        this.mapper = mapper;
    }

    @DSTransactional(propagation = DsPropagation.REQUIRES_NEW)
    public void record(Long applicationId, EnterpriseVerificationFailureCategory category, Instant occurredAt) {
        String result = category == EnterpriseVerificationFailureCategory.LATE_CALLBACK ? "IGNORED" : "FAILED";
        if (mapper.insertSecurityAudit(IdWorker.getId(), applicationId, result, category.name(), occurredAt) != 1) {
            throw new EnterpriseVerificationException(
                EnterpriseVerificationFailureCategory.PROVIDER_FAILURE,
                "Enterprise verification security audit could not be appended");
        }
    }
}

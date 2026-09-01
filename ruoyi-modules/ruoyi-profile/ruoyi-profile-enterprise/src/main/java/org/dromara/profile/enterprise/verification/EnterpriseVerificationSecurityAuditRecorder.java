package org.dromara.profile.enterprise.verification;

import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import com.baomidou.dynamic.datasource.tx.DsPropagation;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class EnterpriseVerificationSecurityAuditRecorder {

    private final EnterpriseVerificationAttemptRepository repository;

    public EnterpriseVerificationSecurityAuditRecorder(EnterpriseVerificationAttemptRepository repository) {
        this.repository = repository;
    }

    @DSTransactional(propagation = DsPropagation.REQUIRES_NEW)
    public void record(Long applicationId, EnterpriseVerificationFailureCategory category, Instant occurredAt) {
        repository.appendSecurityAudit(
            new EnterpriseVerificationSecurityAudit(applicationId, category, occurredAt));
    }
}

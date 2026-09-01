package org.dromara.profile.person.verification;

import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import com.baomidou.dynamic.datasource.tx.DsPropagation;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class PersonVerificationSecurityAuditRecorder {

    private final PersonVerificationAttemptRepository repository;

    public PersonVerificationSecurityAuditRecorder(PersonVerificationAttemptRepository repository) {
        this.repository = repository;
    }

    @DSTransactional(propagation = DsPropagation.REQUIRES_NEW)
    public void record(Long applicationId, PersonVerificationFailureCategory category, Instant occurredAt) {
        repository.appendSecurityAudit(new PersonVerificationSecurityAudit(applicationId, category, occurredAt));
    }
}

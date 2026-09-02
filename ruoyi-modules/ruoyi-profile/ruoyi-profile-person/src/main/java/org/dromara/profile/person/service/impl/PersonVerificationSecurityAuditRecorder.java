package org.dromara.profile.person.service.impl;

import org.dromara.profile.person.domain.exception.PersonVerificationException;
import org.dromara.profile.person.domain.verification.PersonVerificationFailureCategory;
import org.dromara.profile.person.mapper.PersonVerificationAttemptMapper;
import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import com.baomidou.dynamic.datasource.tx.DsPropagation;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class PersonVerificationSecurityAuditRecorder {

    private final PersonVerificationAttemptMapper mapper;

    public PersonVerificationSecurityAuditRecorder(PersonVerificationAttemptMapper mapper) {
        this.mapper = mapper;
    }

    @DSTransactional(propagation = DsPropagation.REQUIRES_NEW)
    public void record(Long applicationId, PersonVerificationFailureCategory category, Instant occurredAt) {
        String result = category == PersonVerificationFailureCategory.LATE_CALLBACK ? "IGNORED" : "FAILED";
        if (mapper.insertSecurityAudit(
            IdWorker.getId(), applicationId, result, category.name(), occurredAt) != 1) {
            throw new PersonVerificationException(
                PersonVerificationFailureCategory.PROVIDER_FAILURE,
                "Person verification security audit could not be appended");
        }
    }
}

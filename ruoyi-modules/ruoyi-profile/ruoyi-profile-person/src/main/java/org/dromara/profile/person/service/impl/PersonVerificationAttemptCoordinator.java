package org.dromara.profile.person.service.impl;

import org.dromara.profile.person.domain.exception.PersonVerificationException;
import org.dromara.profile.person.domain.verification.PersonApplicationVerificationState;
import org.dromara.profile.person.domain.verification.PersonProviderAttemptStatus;
import org.dromara.profile.person.domain.verification.PersonProviderCallbackEnvelope;
import org.dromara.profile.person.domain.verification.PersonProviderStartCommand;
import org.dromara.profile.person.domain.verification.PersonProviderStartResult;
import org.dromara.profile.person.domain.verification.PersonVerificationAttempt;
import org.dromara.profile.person.domain.verification.PersonVerificationCallbackOutcome;
import org.dromara.profile.person.domain.verification.PersonVerificationFailureCategory;
import org.dromara.profile.person.domain.verification.PersonVerificationStartAttemptCommand;
import org.dromara.profile.person.domain.verification.PersonVerifiedCallback;
import org.dromara.profile.person.domain.vo.PersonVerificationApplicationRow;
import org.dromara.profile.person.domain.vo.PersonVerificationAttemptRow;
import org.dromara.profile.person.mapper.PersonVerificationAttemptMapper;
import org.dromara.profile.person.service.PersonVerificationProvider;
import org.dromara.profile.person.service.PersonVerificationTimeSource;
import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PersonVerificationAttemptCoordinator {

    private final PersonVerificationProviderRegistry providerRegistry;
    private final PersonVerificationAttemptMapper mapper;
    private final PersonVerificationEvidenceCodec evidenceCodec;
    private final PersonVerificationSecurityAuditRecorder auditRecorder;

    public PersonVerificationAttemptCoordinator(PersonVerificationProviderRegistry providerRegistry,
                                                PersonVerificationAttemptMapper mapper,
                                                PersonVerificationEvidenceCodec evidenceCodec,
                                                PersonVerificationSecurityAuditRecorder auditRecorder) {
        this.providerRegistry = providerRegistry;
        this.mapper = mapper;
        this.evidenceCodec = evidenceCodec;
        this.auditRecorder = auditRecorder;
    }

    @DSTransactional
    public PersonVerificationAttempt startAttempt(PersonVerificationStartAttemptCommand command) {
        PersonApplicationVerificationState application = lockApplication(command.applicationId());
        if (application.submissionId() != command.submissionId()) {
            throw new PersonVerificationException(
                PersonVerificationFailureCategory.STALE_SUBMISSION,
                "Person verification submission is stale");
        }
        if (application.terminal()) {
            throw new PersonVerificationException(
                PersonVerificationFailureCategory.APPLICATION_TERMINAL,
                "Person verification application is terminal");
        }
        PersonVerificationProvider provider = providerRegistry.requireEnabled(application.providerCode());
        int attemptNo = mapper.nextAttemptNo(application.applicationId());
        PersonProviderStartResult result = provider.start(new PersonProviderStartCommand(
            application.applicationId(), application.submissionId(), attemptNo, command.requestFingerprint()));
        return append(PersonVerificationAttempt.fromStart(
            application, attemptNo, command.requestFingerprint(), result));
    }

    @DSTransactional
    public PersonVerificationCallbackOutcome handleCallback(String providerCode,
                                                            PersonProviderCallbackEnvelope envelope,
                                                            java.time.Instant receivedAt) {
        PersonVerifiedCallback callback;
        try {
            PersonVerificationProvider provider = providerRegistry.requireEnabled(providerCode);
            callback = provider.authenticate(envelope, receivedAt);
        } catch (PersonVerificationException failure) {
            auditRecorder.record(null, failure.category(), receivedAt);
            throw failure;
        }

        PersonVerificationAttempt attempt = lockByProviderRequest(providerCode, callback.providerRequestId())
            .orElseThrow(() -> auditedFailure(
                null,
                PersonVerificationFailureCategory.ATTEMPT_NOT_FOUND,
                "Person verification attempt was not found",
                receivedAt));
        PersonApplicationVerificationState application =
            lockApplication(attempt.applicationId());
        if (!application.providerCode().equals(providerCode)) {
            throw auditedFailure(
                application.applicationId(),
                PersonVerificationFailureCategory.PROVIDER_MISMATCH,
                "Person verification provider does not match the fixed application provider",
                receivedAt);
        }
        if (application.terminal()) {
            auditRecorder.record(
                application.applicationId(), PersonVerificationFailureCategory.LATE_CALLBACK, receivedAt);
            return PersonVerificationCallbackOutcome.LATE_IGNORED;
        }
        if (attempt.status() != PersonProviderAttemptStatus.PENDING) {
            if (attempt.sameCallback(callback)) {
                return PersonVerificationCallbackOutcome.IDEMPOTENT;
            }
            throw auditedFailure(
                application.applicationId(),
                PersonVerificationFailureCategory.CONFLICTING_CALLBACK,
                "Person provider callback conflicts with completed evidence",
                receivedAt);
        }
        complete(attempt.verificationAttemptId(), callback);
        return PersonVerificationCallbackOutcome.ACCEPTED;
    }

    private PersonApplicationVerificationState lockApplication(long applicationId) {
        PersonVerificationApplicationRow row = mapper.lockApplication(applicationId);
        if (row == null) {
            throw new PersonVerificationException(
                PersonVerificationFailureCategory.APPLICATION_NOT_FOUND,
                "Person verification application or current submission was not found");
        }
        return new PersonApplicationVerificationState(
            row.getApplicationId(), row.getSubmissionId(), row.getProviderCode(), row.getStatus());
    }

    private PersonVerificationAttempt append(PersonVerificationAttempt attempt) {
        PersonVerificationAttemptRow row = toRow(attempt);
        row.setVerificationAttemptId(IdWorker.getId());
        try {
            if (mapper.insertAttempt(row) != 1) {
                throw providerFailure("Person verification attempt could not be appended");
            }
        } catch (DuplicateKeyException failure) {
            throw new PersonVerificationException(
                PersonVerificationFailureCategory.PROVIDER_FAILURE,
                "Person verification attempt conflicts with an existing provider request",
                failure);
        }
        return toDomain(row);
    }

    private Optional<PersonVerificationAttempt> lockByProviderRequest(String providerCode,
                                                                       String providerRequestId) {
        return Optional.ofNullable(mapper.lockByProviderRequest(providerCode, providerRequestId))
            .map(this::toDomain);
    }

    private void complete(long verificationAttemptId, PersonVerifiedCallback callback) {
        String storedEvidence = evidenceCodec.encode(
            callback.callbackDigest(), callback.providerEvidenceJson());
        int updated = mapper.completeAttempt(
            verificationAttemptId,
            callback.status().name(),
            callback.normalizedResultJson(),
            storedEvidence,
            callback.errorCode(),
            callback.completedAt());
        if (updated != 1) {
            throw providerFailure("Person verification attempt completion lost its pending fence");
        }
    }

    private PersonVerificationAttemptRow toRow(PersonVerificationAttempt attempt) {
        PersonVerificationAttemptRow row = new PersonVerificationAttemptRow();
        row.setVerificationAttemptId(attempt.verificationAttemptId());
        row.setApplicationId(attempt.applicationId());
        row.setSubmissionId(attempt.submissionId());
        row.setProviderCode(attempt.providerCode());
        row.setProviderRequestId(attempt.providerRequestId());
        row.setRequestFingerprint(attempt.requestFingerprint());
        row.setAttemptNo(attempt.attemptNo());
        row.setStatus(attempt.status().name());
        row.setNormalizedResultJson(attempt.normalizedResultJson());
        row.setProviderEvidenceJson(evidenceCodec.encode(
            attempt.callbackDigest(), attempt.providerEvidenceJson()));
        row.setErrorCode(attempt.errorCode());
        row.setCompletedTime(attempt.completedAt());
        return row;
    }

    private PersonVerificationAttempt toDomain(PersonVerificationAttemptRow row) {
        PersonVerificationEvidenceCodec.DecodedEvidence evidence =
            evidenceCodec.decode(row.getProviderEvidenceJson());
        return new PersonVerificationAttempt(
            row.getVerificationAttemptId(), row.getApplicationId(), row.getSubmissionId(),
            row.getProviderCode(), row.getProviderRequestId(), row.getRequestFingerprint(),
            evidence.callbackDigest(), row.getAttemptNo(), PersonProviderAttemptStatus.valueOf(row.getStatus()),
            row.getNormalizedResultJson(), evidence.providerEvidenceJson(), row.getErrorCode(),
            row.getCompletedTime());
    }

    private PersonVerificationException providerFailure(String message) {
        return new PersonVerificationException(PersonVerificationFailureCategory.PROVIDER_FAILURE, message);
    }

    private PersonVerificationException auditedFailure(Long applicationId,
                                                       PersonVerificationFailureCategory category,
                                                       String message,
                                                       java.time.Instant occurredAt) {
        auditRecorder.record(applicationId, category, occurredAt);
        return new PersonVerificationException(category, message);
    }
}

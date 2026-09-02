package org.dromara.profile.enterprise.service.impl;

import org.dromara.profile.enterprise.domain.exception.EnterpriseVerificationException;
import org.dromara.profile.enterprise.domain.verification.EnterpriseApplicationVerificationState;
import org.dromara.profile.enterprise.domain.verification.EnterpriseProviderAttemptStatus;
import org.dromara.profile.enterprise.domain.verification.EnterpriseProviderCallbackEnvelope;
import org.dromara.profile.enterprise.domain.verification.EnterpriseProviderStartCommand;
import org.dromara.profile.enterprise.domain.verification.EnterpriseProviderStartResult;
import org.dromara.profile.enterprise.domain.verification.EnterpriseVerificationAttempt;
import org.dromara.profile.enterprise.domain.verification.EnterpriseVerificationCallbackOutcome;
import org.dromara.profile.enterprise.domain.verification.EnterpriseVerificationFailureCategory;
import org.dromara.profile.enterprise.domain.verification.EnterpriseVerificationStartAttemptCommand;
import org.dromara.profile.enterprise.domain.verification.EnterpriseVerifiedCallback;
import org.dromara.profile.enterprise.domain.vo.EnterpriseVerificationApplicationRow;
import org.dromara.profile.enterprise.domain.vo.EnterpriseVerificationAttemptRow;
import org.dromara.profile.enterprise.mapper.EnterpriseVerificationAttemptMapper;
import org.dromara.profile.enterprise.service.EnterpriseVerificationProvider;
import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class EnterpriseVerificationAttemptCoordinator {

    private final EnterpriseVerificationProviderRegistry providerRegistry;
    private final EnterpriseVerificationAttemptMapper mapper;
    private final EnterpriseVerificationEvidenceCodec evidenceCodec;
    private final EnterpriseVerificationSecurityAuditRecorder auditRecorder;

    public EnterpriseVerificationAttemptCoordinator(EnterpriseVerificationProviderRegistry providerRegistry,
                                                     EnterpriseVerificationAttemptMapper mapper,
                                                     EnterpriseVerificationEvidenceCodec evidenceCodec,
                                                     EnterpriseVerificationSecurityAuditRecorder auditRecorder) {
        this.providerRegistry = providerRegistry;
        this.mapper = mapper;
        this.evidenceCodec = evidenceCodec;
        this.auditRecorder = auditRecorder;
    }

    @DSTransactional
    public EnterpriseVerificationAttempt startAttempt(EnterpriseVerificationStartAttemptCommand command) {
        EnterpriseApplicationVerificationState application = lockApplication(command.applicationId());
        if (application.submissionId() != command.submissionId()) {
            throw new EnterpriseVerificationException(
                EnterpriseVerificationFailureCategory.STALE_SUBMISSION,
                "Enterprise verification submission is stale");
        }
        if (application.terminal()) {
            throw new EnterpriseVerificationException(
                EnterpriseVerificationFailureCategory.APPLICATION_TERMINAL,
                "Enterprise verification application is terminal");
        }
        EnterpriseVerificationProvider provider = providerRegistry.requireEnabled(application.providerCode());
        int attemptNo = mapper.nextAttemptNo(application.applicationId());
        EnterpriseProviderStartResult result = provider.start(new EnterpriseProviderStartCommand(
            application.applicationId(), application.submissionId(), attemptNo, command.requestFingerprint()));
        return append(EnterpriseVerificationAttempt.fromStart(
            application, attemptNo, command.requestFingerprint(), result));
    }

    @DSTransactional
    public EnterpriseVerificationCallbackOutcome handleCallback(String providerCode,
                                                                EnterpriseProviderCallbackEnvelope envelope,
                                                                java.time.Instant receivedAt) {
        EnterpriseVerifiedCallback callback;
        try {
            EnterpriseVerificationProvider provider = providerRegistry.requireEnabled(providerCode);
            callback = provider.authenticate(envelope, receivedAt);
        } catch (EnterpriseVerificationException failure) {
            auditRecorder.record(null, failure.category(), receivedAt);
            throw failure;
        }

        EnterpriseVerificationAttempt attempt = lockByProviderRequest(providerCode, callback.providerRequestId())
            .orElseThrow(() -> auditedFailure(
                null,
                EnterpriseVerificationFailureCategory.ATTEMPT_NOT_FOUND,
                "Enterprise verification attempt was not found",
                receivedAt));
        EnterpriseApplicationVerificationState application =
            lockApplication(attempt.applicationId());
        if (!application.providerCode().equals(providerCode)) {
            throw auditedFailure(
                application.applicationId(),
                EnterpriseVerificationFailureCategory.PROVIDER_MISMATCH,
                "Enterprise verification provider does not match the fixed application provider",
                receivedAt);
        }
        if (application.terminal()) {
            auditRecorder.record(
                application.applicationId(), EnterpriseVerificationFailureCategory.LATE_CALLBACK, receivedAt);
            return EnterpriseVerificationCallbackOutcome.LATE_IGNORED;
        }
        if (attempt.status() != EnterpriseProviderAttemptStatus.PENDING) {
            if (attempt.sameCallback(callback)) {
                return EnterpriseVerificationCallbackOutcome.IDEMPOTENT;
            }
            throw auditedFailure(
                application.applicationId(),
                EnterpriseVerificationFailureCategory.CONFLICTING_CALLBACK,
                "Enterprise provider callback conflicts with completed evidence",
                receivedAt);
        }
        complete(attempt.verificationAttemptId(), callback);
        return EnterpriseVerificationCallbackOutcome.ACCEPTED;
    }

    private EnterpriseApplicationVerificationState lockApplication(long applicationId) {
        EnterpriseVerificationApplicationRow row = mapper.lockApplication(applicationId);
        if (row == null) {
            throw new EnterpriseVerificationException(
                EnterpriseVerificationFailureCategory.APPLICATION_NOT_FOUND,
                "Enterprise verification application or current submission was not found");
        }
        return new EnterpriseApplicationVerificationState(
            row.getApplicationId(), row.getSubmissionId(), row.getProviderCode(), row.getStatus());
    }

    private EnterpriseVerificationAttempt append(EnterpriseVerificationAttempt attempt) {
        EnterpriseVerificationAttemptRow row = toRow(attempt);
        row.setVerificationAttemptId(IdWorker.getId());
        try {
            if (mapper.insertAttempt(row) != 1) {
                throw providerFailure("Enterprise verification attempt could not be appended");
            }
        } catch (DuplicateKeyException failure) {
            throw new EnterpriseVerificationException(
                EnterpriseVerificationFailureCategory.PROVIDER_FAILURE,
                "Enterprise verification attempt conflicts with an existing provider request",
                failure);
        }
        return toDomain(row);
    }

    private Optional<EnterpriseVerificationAttempt> lockByProviderRequest(String providerCode,
                                                                          String providerRequestId) {
        return Optional.ofNullable(mapper.lockByProviderRequest(providerCode, providerRequestId))
            .map(this::toDomain);
    }

    private void complete(long verificationAttemptId, EnterpriseVerifiedCallback callback) {
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
            throw providerFailure("Enterprise verification attempt completion lost its pending fence");
        }
    }

    private EnterpriseVerificationAttemptRow toRow(EnterpriseVerificationAttempt attempt) {
        EnterpriseVerificationAttemptRow row = new EnterpriseVerificationAttemptRow();
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

    private EnterpriseVerificationAttempt toDomain(EnterpriseVerificationAttemptRow row) {
        EnterpriseVerificationEvidenceCodec.DecodedEvidence evidence =
            evidenceCodec.decode(row.getProviderEvidenceJson());
        return new EnterpriseVerificationAttempt(
            row.getVerificationAttemptId(),
            row.getApplicationId(),
            row.getSubmissionId(),
            row.getProviderCode(),
            row.getProviderRequestId(),
            row.getRequestFingerprint(),
            evidence.callbackDigest(),
            row.getAttemptNo(),
            EnterpriseProviderAttemptStatus.valueOf(row.getStatus()),
            row.getNormalizedResultJson(),
            evidence.providerEvidenceJson(),
            row.getErrorCode(),
            row.getCompletedTime());
    }

    private EnterpriseVerificationException providerFailure(String message) {
        return new EnterpriseVerificationException(EnterpriseVerificationFailureCategory.PROVIDER_FAILURE, message);
    }

    private EnterpriseVerificationException auditedFailure(Long applicationId,
                                                           EnterpriseVerificationFailureCategory category,
                                                           String message,
                                                           java.time.Instant occurredAt) {
        auditRecorder.record(applicationId, category, occurredAt);
        return new EnterpriseVerificationException(category, message);
    }
}

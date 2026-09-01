package org.dromara.profile.enterprise.verification;

import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import org.springframework.stereotype.Service;

@Service
public class EnterpriseVerificationAttemptCoordinator {

    private final EnterpriseVerificationProviderRegistry providerRegistry;
    private final EnterpriseVerificationAttemptRepository repository;
    private final EnterpriseVerificationSecurityAuditRecorder auditRecorder;

    public EnterpriseVerificationAttemptCoordinator(EnterpriseVerificationProviderRegistry providerRegistry,
                                                    EnterpriseVerificationAttemptRepository repository,
                                                    EnterpriseVerificationSecurityAuditRecorder auditRecorder) {
        this.providerRegistry = providerRegistry;
        this.repository = repository;
        this.auditRecorder = auditRecorder;
    }

    @DSTransactional
    public EnterpriseVerificationAttempt startAttempt(EnterpriseVerificationStartAttemptCommand command) {
        EnterpriseApplicationVerificationState application = repository.lockApplication(command.applicationId());
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
        int attemptNo = repository.nextAttemptNo(application.applicationId());
        EnterpriseProviderStartResult result = provider.start(new EnterpriseProviderStartCommand(
            application.applicationId(), application.submissionId(), attemptNo, command.requestFingerprint()));
        return repository.append(EnterpriseVerificationAttempt.fromStart(
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

        EnterpriseVerificationAttempt attempt = repository
            .lockByProviderRequest(providerCode, callback.providerRequestId())
            .orElseThrow(() -> auditedFailure(
                null,
                EnterpriseVerificationFailureCategory.ATTEMPT_NOT_FOUND,
                "Enterprise verification attempt was not found",
                receivedAt));
        EnterpriseApplicationVerificationState application =
            repository.lockApplication(attempt.applicationId());
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
        repository.complete(attempt.verificationAttemptId(), callback);
        return EnterpriseVerificationCallbackOutcome.ACCEPTED;
    }

    private EnterpriseVerificationException auditedFailure(Long applicationId,
                                                           EnterpriseVerificationFailureCategory category,
                                                           String message,
                                                           java.time.Instant occurredAt) {
        auditRecorder.record(applicationId, category, occurredAt);
        return new EnterpriseVerificationException(category, message);
    }
}

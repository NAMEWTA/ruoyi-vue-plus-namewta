package org.dromara.profile.person.verification;

import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import org.springframework.stereotype.Service;

@Service
public class PersonVerificationAttemptCoordinator {

    private final PersonVerificationProviderRegistry providerRegistry;
    private final PersonVerificationAttemptRepository repository;
    private final PersonVerificationSecurityAuditRecorder auditRecorder;

    public PersonVerificationAttemptCoordinator(PersonVerificationProviderRegistry providerRegistry,
                                                PersonVerificationAttemptRepository repository,
                                                PersonVerificationSecurityAuditRecorder auditRecorder) {
        this.providerRegistry = providerRegistry;
        this.repository = repository;
        this.auditRecorder = auditRecorder;
    }

    @DSTransactional
    public PersonVerificationAttempt startAttempt(PersonVerificationStartAttemptCommand command) {
        PersonApplicationVerificationState application = repository.lockApplication(command.applicationId());
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
        int attemptNo = repository.nextAttemptNo(application.applicationId());
        PersonProviderStartResult result = provider.start(new PersonProviderStartCommand(
            application.applicationId(), application.submissionId(), attemptNo, command.requestFingerprint()));
        return repository.append(PersonVerificationAttempt.fromStart(
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

        PersonVerificationAttempt attempt = repository
            .lockByProviderRequest(providerCode, callback.providerRequestId())
            .orElseThrow(() -> auditedFailure(
                null,
                PersonVerificationFailureCategory.ATTEMPT_NOT_FOUND,
                "Person verification attempt was not found",
                receivedAt));
        PersonApplicationVerificationState application =
            repository.lockApplication(attempt.applicationId());
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
        repository.complete(attempt.verificationAttemptId(), callback);
        return PersonVerificationCallbackOutcome.ACCEPTED;
    }

    private PersonVerificationException auditedFailure(Long applicationId,
                                                       PersonVerificationFailureCategory category,
                                                       String message,
                                                       java.time.Instant occurredAt) {
        auditRecorder.record(applicationId, category, occurredAt);
        return new PersonVerificationException(category, message);
    }
}

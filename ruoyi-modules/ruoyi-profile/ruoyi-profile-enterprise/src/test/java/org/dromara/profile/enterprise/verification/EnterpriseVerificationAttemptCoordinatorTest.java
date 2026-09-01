package org.dromara.profile.enterprise.verification;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("dev")
class EnterpriseVerificationAttemptCoordinatorTest {

    @Test
    void explicitRetryAppendsAnAttemptUsingTheProviderFixedOnTheApplication() {
        InMemoryRepository repository = new InMemoryRepository(
            new EnterpriseApplicationVerificationState(71L, 801L, "manual", "WAITING"));
        EnterpriseVerificationProviderProperties properties = new EnterpriseVerificationProviderProperties();
        properties.setEnabledProviders(Set.of("manual"));
        EnterpriseVerificationAttemptCoordinator coordinator = new EnterpriseVerificationAttemptCoordinator(
            new EnterpriseVerificationProviderRegistry(
                List.of(new EnterpriseManualVerificationProvider()), properties),
            repository,
            new EnterpriseVerificationSecurityAuditRecorder(repository));

        EnterpriseVerificationAttempt first = coordinator.startAttempt(
            new EnterpriseVerificationStartAttemptCommand(71L, 801L, "fingerprint-1"));
        EnterpriseVerificationAttempt second = coordinator.startAttempt(
            new EnterpriseVerificationStartAttemptCommand(71L, 801L, "fingerprint-2"));

        assertEquals(1, first.attemptNo());
        assertEquals(2, second.attemptNo());
        assertEquals(List.of("manual", "manual"),
            repository.attempts.stream().map(EnterpriseVerificationAttempt::providerCode).toList());
    }

    @Test
    void retryRejectsAStaleSubmissionBeforeCallingAnyProvider() {
        InMemoryRepository repository = new InMemoryRepository(
            new EnterpriseApplicationVerificationState(71L, 801L, "manual", "WAITING"));
        EnterpriseVerificationProviderProperties properties = new EnterpriseVerificationProviderProperties();
        EnterpriseVerificationAttemptCoordinator coordinator = new EnterpriseVerificationAttemptCoordinator(
            new EnterpriseVerificationProviderRegistry(
                List.of(new EnterpriseManualVerificationProvider()), properties),
            repository,
            new EnterpriseVerificationSecurityAuditRecorder(repository));

        EnterpriseVerificationException failure = assertThrows(EnterpriseVerificationException.class,
            () -> coordinator.startAttempt(
                new EnterpriseVerificationStartAttemptCommand(71L, 800L, "stale-fingerprint")));

        assertEquals(EnterpriseVerificationFailureCategory.STALE_SUBMISSION, failure.category());
        assertEquals(0, repository.attempts.size());
    }

    private static final class InMemoryRepository implements EnterpriseVerificationAttemptRepository {
        private final EnterpriseApplicationVerificationState application;
        private final List<EnterpriseVerificationAttempt> attempts = new ArrayList<>();

        private InMemoryRepository(EnterpriseApplicationVerificationState application) {
            this.application = application;
        }

        @Override
        public EnterpriseApplicationVerificationState lockApplication(long applicationId) {
            if (application.applicationId() != applicationId) {
                throw new EnterpriseVerificationException(
                    EnterpriseVerificationFailureCategory.APPLICATION_NOT_FOUND,
                    "Enterprise application was not found");
            }
            return application;
        }

        @Override
        public int nextAttemptNo(long applicationId) {
            return attempts.size() + 1;
        }

        @Override
        public EnterpriseVerificationAttempt append(EnterpriseVerificationAttempt attempt) {
            attempts.add(attempt);
            return attempt;
        }

        @Override
        public Optional<EnterpriseVerificationAttempt> lockByProviderRequest(String providerCode,
                                                                             String providerRequestId) {
            return Optional.empty();
        }

        @Override
        public void complete(long verificationAttemptId, EnterpriseVerifiedCallback callback) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void appendSecurityAudit(EnterpriseVerificationSecurityAudit audit) {
            throw new UnsupportedOperationException();
        }
    }
}

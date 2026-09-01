package org.dromara.profile.person.verification;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("dev")
class PersonVerificationAttemptCoordinatorTest {

    @Test
    void explicitRetryAppendsAnAttemptUsingTheProviderFixedOnTheApplication() {
        InMemoryRepository repository = new InMemoryRepository(
            new PersonApplicationVerificationState(41L, 501L, "manual", "WAITING"));
        PersonVerificationProviderProperties properties = new PersonVerificationProviderProperties();
        properties.setEnabledProviders(Set.of("manual"));
        PersonVerificationAttemptCoordinator coordinator = new PersonVerificationAttemptCoordinator(
            new PersonVerificationProviderRegistry(List.of(new PersonManualVerificationProvider()), properties),
            repository,
            new PersonVerificationSecurityAuditRecorder(repository));

        PersonVerificationAttempt first = coordinator.startAttempt(
            new PersonVerificationStartAttemptCommand(41L, 501L, "fingerprint-1"));
        PersonVerificationAttempt second = coordinator.startAttempt(
            new PersonVerificationStartAttemptCommand(41L, 501L, "fingerprint-2"));

        assertEquals(1, first.attemptNo());
        assertEquals(2, second.attemptNo());
        assertEquals(List.of("manual", "manual"),
            repository.attempts.stream().map(PersonVerificationAttempt::providerCode).toList());
    }

    @Test
    void retryRejectsAStaleSubmissionBeforeCallingAnyProvider() {
        InMemoryRepository repository = new InMemoryRepository(
            new PersonApplicationVerificationState(41L, 501L, "manual", "WAITING"));
        PersonVerificationProviderProperties properties = new PersonVerificationProviderProperties();
        PersonVerificationAttemptCoordinator coordinator = new PersonVerificationAttemptCoordinator(
            new PersonVerificationProviderRegistry(List.of(new PersonManualVerificationProvider()), properties),
            repository,
            new PersonVerificationSecurityAuditRecorder(repository));

        PersonVerificationException failure = assertThrows(PersonVerificationException.class,
            () -> coordinator.startAttempt(
                new PersonVerificationStartAttemptCommand(41L, 500L, "stale-fingerprint")));

        assertEquals(PersonVerificationFailureCategory.STALE_SUBMISSION, failure.category());
        assertEquals(0, repository.attempts.size());
    }

    private static final class InMemoryRepository implements PersonVerificationAttemptRepository {
        private final PersonApplicationVerificationState application;
        private final List<PersonVerificationAttempt> attempts = new ArrayList<>();

        private InMemoryRepository(PersonApplicationVerificationState application) {
            this.application = application;
        }

        @Override
        public PersonApplicationVerificationState lockApplication(long applicationId) {
            if (application.applicationId() != applicationId) {
                throw new PersonVerificationException(
                    PersonVerificationFailureCategory.APPLICATION_NOT_FOUND, "Person application was not found");
            }
            return application;
        }

        @Override
        public int nextAttemptNo(long applicationId) {
            return attempts.size() + 1;
        }

        @Override
        public PersonVerificationAttempt append(PersonVerificationAttempt attempt) {
            attempts.add(attempt);
            return attempt;
        }

        @Override
        public Optional<PersonVerificationAttempt> lockByProviderRequest(String providerCode,
                                                                          String providerRequestId) {
            return Optional.empty();
        }

        @Override
        public void complete(long verificationAttemptId, PersonVerifiedCallback callback) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void appendSecurityAudit(PersonVerificationSecurityAudit audit) {
            throw new UnsupportedOperationException();
        }
    }
}

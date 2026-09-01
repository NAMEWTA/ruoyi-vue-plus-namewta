package org.dromara.profile.person.verification;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("dev")
class PersonVerificationCallbackContractTest {

    private static final Instant NOW = Instant.parse("2026-09-01T10:00:00Z");

    @Test
    void authenticatesCallbacksAndAuditsForgedOrExpiredPayloads() {
        Fixture fixture = fixture("WAITING");

        PersonVerificationException forged = assertThrows(PersonVerificationException.class,
            () -> fixture.coordinator.handleCallback("test-provider",
                new PersonProviderCallbackEnvelope("person-41-1", NOW.getEpochSecond(), "approved", "00"), NOW));
        PersonVerificationException expired = assertThrows(PersonVerificationException.class,
            () -> fixture.coordinator.handleCallback("test-provider",
                fixture.callback("approved", NOW.minusSeconds(301)), NOW));

        assertEquals(PersonVerificationFailureCategory.INVALID_SIGNATURE, forged.category());
        assertEquals(PersonVerificationFailureCategory.EXPIRED_CALLBACK, expired.category());
        assertEquals(List.of(
                PersonVerificationFailureCategory.INVALID_SIGNATURE,
                PersonVerificationFailureCategory.EXPIRED_CALLBACK),
            fixture.repository.audits.stream().map(PersonVerificationSecurityAudit::category).toList());
        assertEquals(0, fixture.repository.completeCount);
    }

    @Test
    void acceptsOnceReturnsIdempotentlyAndAuditsConflictingRedelivery() {
        Fixture fixture = fixture("WAITING");
        PersonProviderCallbackEnvelope accepted = fixture.callback("approved", NOW);

        assertEquals(PersonVerificationCallbackOutcome.ACCEPTED,
            fixture.coordinator.handleCallback("test-provider", accepted, NOW));
        assertEquals(PersonVerificationCallbackOutcome.IDEMPOTENT,
            fixture.coordinator.handleCallback("test-provider", accepted, NOW.plusSeconds(1)));
        PersonVerificationException conflict = assertThrows(PersonVerificationException.class,
            () -> fixture.coordinator.handleCallback(
                "test-provider", fixture.callback("rejected", NOW.plusSeconds(2)), NOW.plusSeconds(2)));

        assertEquals(PersonVerificationFailureCategory.CONFLICTING_CALLBACK, conflict.category());
        assertEquals(1, fixture.repository.completeCount);
        assertEquals(List.of(PersonVerificationFailureCategory.CONFLICTING_CALLBACK),
            fixture.repository.audits.stream().map(PersonVerificationSecurityAudit::category).toList());
        assertEquals("WAITING", fixture.repository.application.status());
    }

    @Test
    void lateCallbackOnlyAppendsSecurityAudit() {
        Fixture fixture = fixture("FINISH");

        PersonVerificationCallbackOutcome outcome = fixture.coordinator.handleCallback(
            "test-provider", fixture.callback("approved", NOW), NOW);

        assertEquals(PersonVerificationCallbackOutcome.LATE_IGNORED, outcome);
        assertEquals(0, fixture.repository.completeCount);
        assertEquals(List.of(PersonVerificationFailureCategory.LATE_CALLBACK),
            fixture.repository.audits.stream().map(PersonVerificationSecurityAudit::category).toList());
        assertEquals("FINISH", fixture.repository.application.status());
    }

    @Test
    void rejectsCallbackWhenApplicationProviderNoLongerMatches() {
        Fixture fixture = fixture("WAITING");
        fixture.repository.application = new PersonApplicationVerificationState(
            41L, 501L, "other-provider", "WAITING");

        PersonVerificationException failure = assertThrows(PersonVerificationException.class,
            () -> fixture.coordinator.handleCallback(
                "test-provider", fixture.callback("approved", NOW), NOW));

        assertEquals(PersonVerificationFailureCategory.PROVIDER_MISMATCH, failure.category());
        assertEquals(0, fixture.repository.completeCount);
        assertEquals(List.of(PersonVerificationFailureCategory.PROVIDER_MISMATCH),
            fixture.repository.audits.stream().map(PersonVerificationSecurityAudit::category).toList());
    }

    private Fixture fixture(String applicationStatus) {
        PersonDeterministicTestProvider provider = new PersonDeterministicTestProvider("person-test-secret");
        PersonVerificationProviderProperties properties = new PersonVerificationProviderProperties();
        properties.setEnabledProviders(Set.of("test-provider"));
        InMemoryRepository repository = new InMemoryRepository(applicationStatus);
        PersonVerificationSecurityAuditRecorder recorder =
            new PersonVerificationSecurityAuditRecorder(repository);
        PersonVerificationAttemptCoordinator coordinator = new PersonVerificationAttemptCoordinator(
            new PersonVerificationProviderRegistry(List.of(provider), properties), repository, recorder);
        return new Fixture(provider, repository, coordinator);
    }

    private record Fixture(
        PersonDeterministicTestProvider provider,
        InMemoryRepository repository,
        PersonVerificationAttemptCoordinator coordinator
    ) {
        PersonProviderCallbackEnvelope callback(String payload, Instant timestamp) {
            String requestId = "person-41-1";
            return new PersonProviderCallbackEnvelope(
                requestId,
                timestamp.getEpochSecond(),
                payload,
                provider.sign(requestId, timestamp.getEpochSecond(), payload));
        }
    }

    private static final class InMemoryRepository implements PersonVerificationAttemptRepository {
        private PersonApplicationVerificationState application;
        private final List<PersonVerificationAttempt> attempts = new ArrayList<>();
        private final List<PersonVerificationSecurityAudit> audits = new ArrayList<>();
        private int completeCount;

        private InMemoryRepository(String applicationStatus) {
            application = new PersonApplicationVerificationState(41L, 501L, "test-provider", applicationStatus);
            attempts.add(new PersonVerificationAttempt(
                91L, 41L, 501L, "test-provider", "person-41-1", "fingerprint", null, 1,
                PersonProviderAttemptStatus.PENDING, null, null, null, null));
        }

        @Override
        public PersonApplicationVerificationState lockApplication(long applicationId) {
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
            return attempts.stream()
                .filter(attempt -> attempt.providerCode().equals(providerCode)
                    && providerRequestId.equals(attempt.providerRequestId()))
                .findFirst();
        }

        @Override
        public void complete(long verificationAttemptId, PersonVerifiedCallback callback) {
            PersonVerificationAttempt current = attempts.stream()
                .filter(attempt -> attempt.verificationAttemptId() == verificationAttemptId)
                .findFirst()
                .orElseThrow();
            attempts.remove(current);
            attempts.add(new PersonVerificationAttempt(
                current.verificationAttemptId(), current.applicationId(), current.submissionId(),
                current.providerCode(), current.providerRequestId(), current.requestFingerprint(),
                callback.callbackDigest(),
                current.attemptNo(), callback.status(), callback.normalizedResultJson(),
                callback.providerEvidenceJson(), callback.errorCode(), callback.completedAt()));
            completeCount++;
        }

        @Override
        public void appendSecurityAudit(PersonVerificationSecurityAudit audit) {
            audits.add(audit);
        }
    }
}

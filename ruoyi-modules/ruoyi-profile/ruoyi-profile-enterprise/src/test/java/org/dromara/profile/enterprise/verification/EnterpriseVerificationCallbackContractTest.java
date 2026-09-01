package org.dromara.profile.enterprise.verification;

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
class EnterpriseVerificationCallbackContractTest {

    private static final Instant NOW = Instant.parse("2026-09-01T10:00:00Z");

    @Test
    void authenticatesAndHandlesIdempotentConflictingAndLateCallbacksWithoutPublishing() {
        Fixture fixture = fixture("WAITING");
        EnterpriseProviderCallbackEnvelope accepted = fixture.callback("approved", NOW);

        assertEquals(EnterpriseVerificationCallbackOutcome.ACCEPTED,
            fixture.coordinator.handleCallback("test-provider", accepted, NOW));
        assertEquals(EnterpriseVerificationCallbackOutcome.IDEMPOTENT,
            fixture.coordinator.handleCallback("test-provider", accepted, NOW.plusSeconds(1)));
        EnterpriseVerificationException conflict = assertThrows(EnterpriseVerificationException.class,
            () -> fixture.coordinator.handleCallback(
                "test-provider", fixture.callback("rejected", NOW.plusSeconds(2)), NOW.plusSeconds(2)));

        assertEquals(EnterpriseVerificationFailureCategory.CONFLICTING_CALLBACK, conflict.category());
        assertEquals(1, fixture.repository.completeCount);
        assertEquals(List.of(EnterpriseVerificationFailureCategory.CONFLICTING_CALLBACK),
            fixture.repository.audits.stream().map(EnterpriseVerificationSecurityAudit::category).toList());
        assertEquals("WAITING", fixture.repository.application.status());

        Fixture terminal = fixture("FINISH");
        assertEquals(EnterpriseVerificationCallbackOutcome.LATE_IGNORED,
            terminal.coordinator.handleCallback(
                "test-provider", terminal.callback("approved", NOW), NOW));
        assertEquals(0, terminal.repository.completeCount);
        assertEquals("FINISH", terminal.repository.application.status());
    }

    @Test
    void auditsForgedAndExpiredCallbacks() {
        Fixture fixture = fixture("WAITING");

        EnterpriseVerificationException forged = assertThrows(EnterpriseVerificationException.class,
            () -> fixture.coordinator.handleCallback("test-provider",
                new EnterpriseProviderCallbackEnvelope(
                    "enterprise-71-1", NOW.getEpochSecond(), "approved", "00"), NOW));
        EnterpriseVerificationException expired = assertThrows(EnterpriseVerificationException.class,
            () -> fixture.coordinator.handleCallback(
                "test-provider", fixture.callback("approved", NOW.minusSeconds(301)), NOW));

        assertEquals(EnterpriseVerificationFailureCategory.INVALID_SIGNATURE, forged.category());
        assertEquals(EnterpriseVerificationFailureCategory.EXPIRED_CALLBACK, expired.category());
        assertEquals(0, fixture.repository.completeCount);
    }

    @Test
    void rejectsCallbackWhenApplicationProviderNoLongerMatches() {
        Fixture fixture = fixture("WAITING", "other-provider");

        EnterpriseVerificationException failure = assertThrows(EnterpriseVerificationException.class,
            () -> fixture.coordinator.handleCallback(
                "test-provider", fixture.callback("approved", NOW), NOW));

        assertEquals(EnterpriseVerificationFailureCategory.PROVIDER_MISMATCH, failure.category());
        assertEquals(0, fixture.repository.completeCount);
        assertEquals(List.of(EnterpriseVerificationFailureCategory.PROVIDER_MISMATCH),
            fixture.repository.audits.stream().map(EnterpriseVerificationSecurityAudit::category).toList());
    }

    private Fixture fixture(String applicationStatus) {
        return fixture(applicationStatus, "test-provider");
    }

    private Fixture fixture(String applicationStatus, String applicationProviderCode) {
        EnterpriseDeterministicTestProvider provider =
            new EnterpriseDeterministicTestProvider("enterprise-test-secret");
        EnterpriseVerificationProviderProperties properties = new EnterpriseVerificationProviderProperties();
        properties.setEnabledProviders(Set.of("test-provider"));
        InMemoryRepository repository = new InMemoryRepository(applicationStatus, applicationProviderCode);
        EnterpriseVerificationSecurityAuditRecorder recorder =
            new EnterpriseVerificationSecurityAuditRecorder(repository);
        EnterpriseVerificationAttemptCoordinator coordinator = new EnterpriseVerificationAttemptCoordinator(
            new EnterpriseVerificationProviderRegistry(List.of(provider), properties), repository, recorder);
        return new Fixture(provider, repository, coordinator);
    }

    private record Fixture(
        EnterpriseDeterministicTestProvider provider,
        InMemoryRepository repository,
        EnterpriseVerificationAttemptCoordinator coordinator
    ) {
        EnterpriseProviderCallbackEnvelope callback(String payload, Instant timestamp) {
            String requestId = "enterprise-71-1";
            return new EnterpriseProviderCallbackEnvelope(
                requestId,
                timestamp.getEpochSecond(),
                payload,
                provider.sign(requestId, timestamp.getEpochSecond(), payload));
        }
    }

    private static final class InMemoryRepository implements EnterpriseVerificationAttemptRepository {
        private final EnterpriseApplicationVerificationState application;
        private final List<EnterpriseVerificationAttempt> attempts = new ArrayList<>();
        private final List<EnterpriseVerificationSecurityAudit> audits = new ArrayList<>();
        private int completeCount;

        private InMemoryRepository(String applicationStatus, String applicationProviderCode) {
            application = new EnterpriseApplicationVerificationState(
                71L, 801L, applicationProviderCode, applicationStatus);
            attempts.add(new EnterpriseVerificationAttempt(
                191L, 71L, 801L, "test-provider", "enterprise-71-1", "fingerprint", null, 1,
                EnterpriseProviderAttemptStatus.PENDING, null, null, null, null));
        }

        @Override
        public EnterpriseApplicationVerificationState lockApplication(long applicationId) {
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
            return attempts.stream()
                .filter(attempt -> attempt.providerCode().equals(providerCode)
                    && providerRequestId.equals(attempt.providerRequestId()))
                .findFirst();
        }

        @Override
        public void complete(long verificationAttemptId, EnterpriseVerifiedCallback callback) {
            EnterpriseVerificationAttempt current = attempts.stream()
                .filter(attempt -> attempt.verificationAttemptId() == verificationAttemptId)
                .findFirst()
                .orElseThrow();
            attempts.remove(current);
            attempts.add(new EnterpriseVerificationAttempt(
                current.verificationAttemptId(), current.applicationId(), current.submissionId(),
                current.providerCode(), current.providerRequestId(), current.requestFingerprint(),
                callback.callbackDigest(),
                current.attemptNo(), callback.status(), callback.normalizedResultJson(),
                callback.providerEvidenceJson(), callback.errorCode(), callback.completedAt()));
            completeCount++;
        }

        @Override
        public void appendSecurityAudit(EnterpriseVerificationSecurityAudit audit) {
            audits.add(audit);
        }
    }
}

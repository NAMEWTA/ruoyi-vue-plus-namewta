package org.dromara.profile.person.verification;

import cn.dev33.satoken.annotation.SaIgnore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("dev")
class PersonVerificationCallbackControllerTest {

    private static final Instant NOW = Instant.parse("2026-09-01T10:00:00Z");

    @Test
    void exposesSaIgnoredAuthenticatedCallbackAndStableFailureCategory() throws Exception {
        PersonDeterministicTestProvider provider = new PersonDeterministicTestProvider("person-http-secret");
        InMemoryRepository repository = new InMemoryRepository();
        PersonVerificationProviderProperties properties = new PersonVerificationProviderProperties();
        properties.setEnabledProviders(Set.of("test-provider"));
        PersonVerificationAttemptCoordinator coordinator = new PersonVerificationAttemptCoordinator(
            new PersonVerificationProviderRegistry(List.of(provider), properties),
            repository,
            new PersonVerificationSecurityAuditRecorder(repository));
        PersonVerificationCallbackController controller =
            new PersonVerificationCallbackController(coordinator, () -> NOW);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new PersonVerificationCallbackExceptionHandler())
            .build();

        assertNotNull(PersonVerificationCallbackController.class
            .getMethod("callback", String.class, PersonVerificationCallbackController.CallbackRequest.class)
            .getAnnotation(SaIgnore.class));

        String signature = provider.sign("person-41-1", NOW.getEpochSecond(), "approved");
        String acceptedBody = """
            {"providerRequestId":"person-41-1","timestampEpochSecond":%d,
             "payload":"approved","signature":"%s"}
            """.formatted(NOW.getEpochSecond(), signature);
        mvc.perform(post("/profile/person/verification/providers/test-provider/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(acceptedBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value("ACCEPTED"));
        mvc.perform(post("/profile/person/verification/providers/test-provider/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(acceptedBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value("IDEMPOTENT"));

        String forgedBody = """
            {"providerRequestId":"person-41-1","timestampEpochSecond":%d,
             "payload":"approved","signature":"00"}
            """.formatted(NOW.getEpochSecond());
        mvc.perform(post("/profile/person/verification/providers/test-provider/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(forgedBody))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.data.category").value("INVALID_SIGNATURE"));
    }

    private static final class InMemoryRepository implements PersonVerificationAttemptRepository {
        private PersonVerificationAttempt attempt = new PersonVerificationAttempt(
            91L, 41L, 501L, "test-provider", "person-41-1", "fingerprint", null, 1,
            PersonProviderAttemptStatus.PENDING, null, null, null, null);

        @Override
        public PersonApplicationVerificationState lockApplication(long applicationId) {
            return new PersonApplicationVerificationState(41L, 501L, "test-provider", "WAITING");
        }

        @Override
        public int nextAttemptNo(long applicationId) {
            return 2;
        }

        @Override
        public PersonVerificationAttempt append(PersonVerificationAttempt value) {
            attempt = value;
            return value;
        }

        @Override
        public Optional<PersonVerificationAttempt> lockByProviderRequest(String providerCode,
                                                                          String providerRequestId) {
            return Optional.of(attempt);
        }

        @Override
        public void complete(long verificationAttemptId, PersonVerifiedCallback callback) {
            attempt = new PersonVerificationAttempt(
                attempt.verificationAttemptId(), attempt.applicationId(), attempt.submissionId(),
                attempt.providerCode(), attempt.providerRequestId(), attempt.requestFingerprint(),
                callback.callbackDigest(), attempt.attemptNo(), callback.status(),
                callback.normalizedResultJson(), callback.providerEvidenceJson(), callback.errorCode(),
                callback.completedAt());
        }

        @Override
        public void appendSecurityAudit(PersonVerificationSecurityAudit audit) {
        }
    }
}

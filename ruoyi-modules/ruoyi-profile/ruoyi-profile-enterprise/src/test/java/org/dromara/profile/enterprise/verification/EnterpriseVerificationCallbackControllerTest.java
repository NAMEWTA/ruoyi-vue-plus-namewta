package org.dromara.profile.enterprise.verification;

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
class EnterpriseVerificationCallbackControllerTest {

    private static final Instant NOW = Instant.parse("2026-09-01T10:00:00Z");

    @Test
    void exposesSaIgnoredAuthenticatedCallbackAndStableFailureCategory() throws Exception {
        EnterpriseDeterministicTestProvider provider =
            new EnterpriseDeterministicTestProvider("enterprise-http-secret");
        InMemoryRepository repository = new InMemoryRepository();
        EnterpriseVerificationProviderProperties properties = new EnterpriseVerificationProviderProperties();
        properties.setEnabledProviders(Set.of("test-provider"));
        EnterpriseVerificationAttemptCoordinator coordinator = new EnterpriseVerificationAttemptCoordinator(
            new EnterpriseVerificationProviderRegistry(List.of(provider), properties),
            repository,
            new EnterpriseVerificationSecurityAuditRecorder(repository));
        EnterpriseVerificationCallbackController controller =
            new EnterpriseVerificationCallbackController(coordinator, () -> NOW);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new EnterpriseVerificationCallbackExceptionHandler())
            .build();

        assertNotNull(EnterpriseVerificationCallbackController.class
            .getMethod("callback", String.class, EnterpriseVerificationCallbackController.CallbackRequest.class)
            .getAnnotation(SaIgnore.class));

        String signature = provider.sign("enterprise-71-1", NOW.getEpochSecond(), "approved");
        String acceptedBody = """
            {"providerRequestId":"enterprise-71-1","timestampEpochSecond":%d,
             "payload":"approved","signature":"%s"}
            """.formatted(NOW.getEpochSecond(), signature);
        mvc.perform(post("/profile/enterprise/verification/providers/test-provider/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(acceptedBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value("ACCEPTED"));
        mvc.perform(post("/profile/enterprise/verification/providers/test-provider/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(acceptedBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value("IDEMPOTENT"));

        String forgedBody = """
            {"providerRequestId":"enterprise-71-1","timestampEpochSecond":%d,
             "payload":"approved","signature":"00"}
            """.formatted(NOW.getEpochSecond());
        mvc.perform(post("/profile/enterprise/verification/providers/test-provider/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(forgedBody))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.data.category").value("INVALID_SIGNATURE"));
    }

    private static final class InMemoryRepository implements EnterpriseVerificationAttemptRepository {
        private EnterpriseVerificationAttempt attempt = new EnterpriseVerificationAttempt(
            191L, 71L, 801L, "test-provider", "enterprise-71-1", "fingerprint", null, 1,
            EnterpriseProviderAttemptStatus.PENDING, null, null, null, null);

        @Override
        public EnterpriseApplicationVerificationState lockApplication(long applicationId) {
            return new EnterpriseApplicationVerificationState(71L, 801L, "test-provider", "WAITING");
        }

        @Override
        public int nextAttemptNo(long applicationId) {
            return 2;
        }

        @Override
        public EnterpriseVerificationAttempt append(EnterpriseVerificationAttempt value) {
            attempt = value;
            return value;
        }

        @Override
        public Optional<EnterpriseVerificationAttempt> lockByProviderRequest(String providerCode,
                                                                              String providerRequestId) {
            return Optional.of(attempt);
        }

        @Override
        public void complete(long verificationAttemptId, EnterpriseVerifiedCallback callback) {
            attempt = new EnterpriseVerificationAttempt(
                attempt.verificationAttemptId(), attempt.applicationId(), attempt.submissionId(),
                attempt.providerCode(), attempt.providerRequestId(), attempt.requestFingerprint(),
                callback.callbackDigest(), attempt.attemptNo(), callback.status(),
                callback.normalizedResultJson(), callback.providerEvidenceJson(), callback.errorCode(),
                callback.completedAt());
        }

        @Override
        public void appendSecurityAudit(EnterpriseVerificationSecurityAudit audit) {
        }
    }
}

package org.dromara.profile.enterprise.application;

import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import org.dromara.profile.api.domain.ProfileType;
import org.dromara.profile.api.material.ProfileMaterialPort;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerKey;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerType;
import org.dromara.profile.enterprise.verification.EnterpriseVerificationAttemptCoordinator;
import org.dromara.profile.enterprise.verification.EnterpriseVerificationException;
import org.dromara.profile.enterprise.verification.EnterpriseVerificationProviderRegistry;
import org.dromara.profile.enterprise.verification.EnterpriseVerificationStartAttemptCommand;
import org.dromara.system.api.ConfigService;
import org.dromara.workflow.api.event.ProcessEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class EnterpriseApplicationService {

    private static final String DEFAULT_PROVIDER_KEY = "profile.enterprise.provider.default";
    private static final String FLOW_CODE_KEY = "profile.enterprise.flowCode";
    private static final Set<String> EDITABLE_STATUSES = Set.of("DRAFT", "BACK", "CANCEL");
    private static final Set<String> EVENT_STATUSES = Set.of("BACK", "CANCEL", "INVALID", "TERMINATION");
    private static final Pattern CREDIT_CODE = Pattern.compile("[0-9A-Z-]{8,64}");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final EnterpriseApplicationRepository repository;
    private final ProfileMaterialPort materials;
    private final EnterpriseVerificationProviderRegistry providers;
    private final EnterpriseVerificationAttemptCoordinator attempts;
    private final EnterpriseWorkflowGateway workflow;
    private final ConfigService configService;
    private final Clock clock;

    @Autowired
    public EnterpriseApplicationService(EnterpriseApplicationRepository repository, ProfileMaterialPort materials,
                                    EnterpriseVerificationProviderRegistry providers,
                                    EnterpriseVerificationAttemptCoordinator attempts,
                                    EnterpriseWorkflowGateway workflow, ConfigService configService) {
        this(repository, materials, providers, attempts, workflow, configService, Clock.systemUTC());
    }

    EnterpriseApplicationService(EnterpriseApplicationRepository repository, ProfileMaterialPort materials,
                             EnterpriseVerificationProviderRegistry providers,
                             EnterpriseVerificationAttemptCoordinator attempts,
                             EnterpriseWorkflowGateway workflow, ConfigService configService, Clock clock) {
        this.repository = repository;
        this.materials = materials;
        this.providers = providers;
        this.attempts = attempts;
        this.workflow = workflow;
        this.configService = configService;
        this.clock = clock;
    }

    public Optional<EnterpriseApplicationView> current(long userId) {
        requireUserId(userId);
        return repository.findOpenByUserId(userId).map(EnterpriseApplicationView::from);
    }

    public EnterpriseProbeView probe(EnterpriseProbeCommand command) {
        if (command == null) {
            throw failure("ENTERPRISE_PROBE_REQUIRED");
        }
        String identityKey = normalizeCredit(command.unifiedCreditCode());
        if (identityKey == null || !CREDIT_CODE.matcher(identityKey).matches()) {
            throw failure("ENTERPRISE_CREDIT_CODE_INVALID");
        }
        return new EnterpriseProbeView(repository.probeStatus(identityKey));
    }

    @DSTransactional
    public EnterpriseApplicationView save(long userId, EnterpriseDraftCommand command) {
        requireUserId(userId);
        EnterpriseIdentityFields fields = EnterpriseIdentityFields.normalize(command);
        validateDraft(fields);
        Optional<EnterpriseApplication> current = repository.findOpenByUserId(userId);
        if (current.isPresent() && !current.get().editable()) {
            throw failure("ENTERPRISE_APPLICATION_READ_ONLY");
        }
        String providerCode = current.map(EnterpriseApplication::providerCode).orElseGet(this::defaultProvider);
        requireProviderEnabled(providerCode);
        Long accountProfileId = repository.findEffectiveProfileIdByUser(userId);
        accountProfileId = positiveId(accountProfileId);
        Long identityProfileId = fields.identityKey() == null
            ? null : repository.findActiveProfileIdByIdentity(fields.identityKey());
        identityProfileId = positiveId(identityProfileId);
        Long targetProfileId = accountProfileId == null ? identityProfileId : accountProfileId;
        EnterpriseApplication saved = repository.saveDraft(userId, providerCode,
            new EnterpriseDraftUpdate(fields, targetProfileId, command.expectedVersion()));
        return EnterpriseApplicationView.from(saved);
    }

    @DSTransactional
    public EnterpriseApplicationView submit(long userId, int expectedVersion) {
        requireUserId(userId);
        EnterpriseApplication application = repository.lockOpenByUserId(userId);
        if (application.applicantUserId() != userId || !EDITABLE_STATUSES.contains(application.status())) {
            throw failure("ENTERPRISE_APPLICATION_NOT_EDITABLE");
        }
        if (application.version() != expectedVersion) {
            throw failure("ENTERPRISE_APPLICATION_VERSION_CONFLICT");
        }
        validateComplete(application.fields());
        requireProviderEnabled(application.providerCode());
        repository.requireSubmissionAllowed(userId, application.targetProfileId(),
            application.fields().identityKey());
        MaterialOwnerKey working = owner(MaterialOwnerType.WORKING, application.enterpriseApplicationId());
        Set<String> qualifiers = new HashSet<>();
        qualifiers.add("ALWAYS");
        if (!application.fields().handlerIsLegalRepresentative()) {
            qualifiers.add("HANDLER_NOT_LEGAL_REPRESENTATIVE");
        }
        materials.validateRequired(working, "*", Set.copyOf(qualifiers));

        int snapshotVersion = application.submissionSeq() + 1;
        Instant submittedTime = clock.instant();
        EnterpriseSubmission submission = repository.insertSubmission(
            application, application.fields(), snapshotVersion, submittedTime);
        materials.snapshotImmutable(working, owner(MaterialOwnerType.SUBMISSION,
            submission.enterpriseSubmissionId()));
        EnterpriseApplication waiting = repository.markWaiting(application.enterpriseApplicationId(), snapshotVersion,
            expectedVersion, submittedTime);
        startVerificationAttempt(new EnterpriseVerificationStartAttemptCommand(application.enterpriseApplicationId(),
            submission.enterpriseSubmissionId(), fingerprint(application.fields(), snapshotVersion)));
        workflow.start(application.enterpriseApplicationId(), submission.enterpriseSubmissionId(), snapshotVersion);
        return EnterpriseApplicationView.from(waiting);
    }

    @EventListener
    @DSTransactional
    public void handleProcessEvent(ProcessEvent event) {
        if (event == null || !expectedFlowCode().equals(event.getFlowCode())) {
            return;
        }
        Long applicationId = positiveLong(event.getBusinessId());
        if (applicationId == null) {
            return;
        }
        Integer snapshotVersion = snapshotVersion(event.getParams());
        if (snapshotVersion == null) {
            snapshotVersion = workflow.persistedSnapshotVersion(event);
        }
        if (snapshotVersion == null) {
            return;
        }
        EnterpriseApplication application = repository.lockById(applicationId);
        if (application.submissionSeq() != snapshotVersion || !"WAITING".equals(application.status())) {
            return;
        }
        String status = normalizeStatus(event.getStatus());
        if ("FINISH".equals(status)) {
            if ("REJECT".equals(normalizeDecision(event.getParams()))) {
                repository.updateWorkflowStatus(applicationId, snapshotVersion, "INVALID",
                    application.version(), clock.instant());
                return;
            }
            EnterpriseSubmission submission = repository.requireSubmission(applicationId, snapshotVersion);
            EnterprisePublication publication = repository.publishApproved(applicationId, snapshotVersion, clock.instant());
            materials.snapshotImmutable(owner(MaterialOwnerType.SUBMISSION, submission.enterpriseSubmissionId()),
                owner(MaterialOwnerType.VERSION, publication.enterpriseVersionId()));
        } else if (EVENT_STATUSES.contains(status)) {
            repository.updateWorkflowStatus(applicationId, snapshotVersion, status,
                application.version(), clock.instant());
        }
    }

    private String normalizeDecision(Map<String, Object> params) {
        Object value = params == null ? null : params.get("profileDecision");
        return value == null ? "" : value.toString().strip().toUpperCase(Locale.ROOT);
    }

    private void validateDraft(EnterpriseIdentityFields fields) {
        if (length(fields.enterpriseName()) > 255 || length(fields.unifiedCreditCode()) > 64
            || length(fields.enterpriseType()) > 64 || length(fields.legalRepresentativeName()) > 100
            || length(fields.legalDocumentNumber()) > 128 || length(fields.registeredAddress()) > 500
            || length(fields.contactName()) > 100 || length(fields.contactPhone()) > 64
            || length(fields.email()) > 255 || length(fields.industryCode()) > 64 || length(fields.website()) > 500) {
            throw failure("ENTERPRISE_FIELD_TOO_LONG");
        }
        if (fields.unifiedCreditCode() != null && !CREDIT_CODE.matcher(fields.unifiedCreditCode()).matches()) {
            throw failure("ENTERPRISE_CREDIT_CODE_INVALID");
        }
        if (fields.legalDocumentTypeCode() != null) {
            DocumentTypeRule rule = documentRule(fields.legalDocumentTypeCode());
            if (fields.legalDocumentNumber() != null
                && !Pattern.matches(rule.numberPattern(), fields.legalDocumentNumber())) {
                throw failure("ENTERPRISE_LEGAL_DOCUMENT_NUMBER_INVALID");
            }
        }
        if (fields.email() != null && !EMAIL.matcher(fields.email()).matches()) {
            throw failure("ENTERPRISE_EMAIL_INVALID");
        }
        if (fields.registeredCapital() != null && fields.registeredCapital().signum() < 0) {
            throw failure("ENTERPRISE_REGISTERED_CAPITAL_INVALID");
        }
        validateDates(fields, false);
    }

    private void validateComplete(EnterpriseIdentityFields fields) {
        validateDraft(fields);
        if (fields.enterpriseName() == null || fields.unifiedCreditCode() == null || fields.enterpriseType() == null
            || fields.legalRepresentativeName() == null || fields.legalDocumentTypeCode() == null
            || fields.legalDocumentNumber() == null || fields.establishedDate() == null
            || fields.registeredAddress() == null || fields.businessScope() == null) {
            throw failure("ENTERPRISE_FIELDS_INCOMPLETE");
        }
        DocumentTypeRule rule = documentRule(fields.legalDocumentTypeCode());
        if (!Pattern.matches(rule.numberPattern(), fields.legalDocumentNumber())) {
            throw failure("ENTERPRISE_LEGAL_DOCUMENT_NUMBER_INVALID");
        }
        validateDates(fields, true);
    }

    private void validateDates(EnterpriseIdentityFields fields, boolean complete) {
        LocalDate today = LocalDate.now(clock);
        if (fields.establishedDate() != null && fields.establishedDate().isAfter(today)) {
            throw failure("ENTERPRISE_ESTABLISHED_DATE_INVALID");
        }
        if (fields.businessTermFrom() != null && fields.businessTermUntil() != null
            && fields.businessTermFrom().isAfter(fields.businessTermUntil())) {
            throw failure("ENTERPRISE_BUSINESS_TERM_INVALID");
        }
        if (complete && fields.businessTermFrom() != null && fields.businessTermUntil() != null
            && (fields.businessTermFrom().isAfter(today) || fields.businessTermUntil().isBefore(today))) {
            throw failure("ENTERPRISE_BUSINESS_TERM_EXPIRED");
        }
    }

    private DocumentTypeRule documentRule(String documentTypeCode) {
        return repository.findDocumentType(documentTypeCode)
            .orElseThrow(() -> failure("ENTERPRISE_DOCUMENT_TYPE_UNAVAILABLE"));
    }

    private String defaultProvider() {
        String providerCode = configService.getConfigValue(DEFAULT_PROVIDER_KEY);
        if (providerCode == null || providerCode.isBlank()) {
            throw failure("ENTERPRISE_PROVIDER_NOT_CONFIGURED");
        }
        return providerCode.strip();
    }

    private void requireProviderEnabled(String providerCode) {
        try {
            providers.requireEnabled(providerCode);
        } catch (EnterpriseVerificationException exception) {
            throw new EnterpriseApplicationException("ENTERPRISE_PROVIDER_UNAVAILABLE", exception);
        }
    }

    private void startVerificationAttempt(EnterpriseVerificationStartAttemptCommand command) {
        try {
            attempts.startAttempt(command);
        } catch (EnterpriseVerificationException exception) {
            throw new EnterpriseApplicationException("ENTERPRISE_PROVIDER_UNAVAILABLE", exception);
        }
    }

    private String expectedFlowCode() {
        String flowCode = configService.getConfigValue(FLOW_CODE_KEY);
        return flowCode == null ? "" : flowCode.strip();
    }

    private String fingerprint(EnterpriseIdentityFields fields, int snapshotVersion) {
        String canonical = fields.identityKey() + "\n" + snapshotVersion;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private String normalizeCredit(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip().toUpperCase(Locale.ROOT);
    }

    private MaterialOwnerKey owner(MaterialOwnerType type, long ownerId) {
        return new MaterialOwnerKey(ProfileType.ENTERPRISE, type, ownerId);
    }

    private Integer snapshotVersion(Map<String, Object> params) {
        if (params == null) {
            return null;
        }
        Object value = params.get("snapshotVersion");
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : Integer.valueOf(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Long positiveLong(String value) {
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0 ? parsed : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Long positiveId(Long value) {
        return value != null && value > 0 ? value : null;
    }

    private String normalizeStatus(String status) {
        return status == null ? "" : status.strip().toUpperCase(java.util.Locale.ROOT);
    }

    private int length(String value) {
        return value == null ? 0 : value.length();
    }

    private void requireUserId(long userId) {
        if (userId <= 0) {
            throw failure("ENTERPRISE_USER_INVALID");
        }
    }

    private EnterpriseApplicationException failure(String category) {
        return new EnterpriseApplicationException(category);
    }
}

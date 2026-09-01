package org.dromara.profile.person.application;

import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import org.dromara.profile.api.domain.ProfileType;
import org.dromara.profile.api.material.ProfileMaterialPort;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerKey;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerType;
import org.dromara.profile.person.verification.PersonVerificationAttemptCoordinator;
import org.dromara.profile.person.verification.PersonVerificationException;
import org.dromara.profile.person.verification.PersonVerificationProviderRegistry;
import org.dromara.profile.person.verification.PersonVerificationStartAttemptCommand;
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class PersonApplicationService {

    private static final String DEFAULT_PROVIDER_KEY = "profile.person.provider.default";
    private static final String FLOW_CODE_KEY = "profile.person.flowCode";
    private static final Set<String> EDITABLE_STATUSES = Set.of("DRAFT", "BACK", "CANCEL");
    private static final Set<String> EVENT_STATUSES = Set.of("BACK", "CANCEL", "INVALID", "TERMINATION");
    private static final Set<String> GENDERS = Set.of("MALE", "FEMALE", "UNKNOWN");

    private final PersonApplicationRepository repository;
    private final ProfileMaterialPort materials;
    private final PersonVerificationProviderRegistry providers;
    private final PersonVerificationAttemptCoordinator attempts;
    private final PersonWorkflowGateway workflow;
    private final ConfigService configService;
    private final Clock clock;

    @Autowired
    public PersonApplicationService(PersonApplicationRepository repository, ProfileMaterialPort materials,
                                    PersonVerificationProviderRegistry providers,
                                    PersonVerificationAttemptCoordinator attempts,
                                    PersonWorkflowGateway workflow, ConfigService configService) {
        this(repository, materials, providers, attempts, workflow, configService, Clock.systemUTC());
    }

    PersonApplicationService(PersonApplicationRepository repository, ProfileMaterialPort materials,
                             PersonVerificationProviderRegistry providers,
                             PersonVerificationAttemptCoordinator attempts,
                             PersonWorkflowGateway workflow, ConfigService configService, Clock clock) {
        this.repository = repository;
        this.materials = materials;
        this.providers = providers;
        this.attempts = attempts;
        this.workflow = workflow;
        this.configService = configService;
        this.clock = clock;
    }

    public Optional<PersonApplicationView> current(long userId) {
        requireUserId(userId);
        return repository.findOpenByUserId(userId).map(PersonApplicationView::from);
    }

    @DSTransactional
    public PersonApplicationView save(long userId, PersonDraftCommand command) {
        requireUserId(userId);
        PersonIdentityFields fields = PersonIdentityFields.normalize(command);
        validateDraft(fields);
        Optional<PersonApplication> current = repository.findOpenByUserId(userId);
        if (current.isPresent() && !current.get().editable()) {
            throw failure("PERSON_APPLICATION_READ_ONLY");
        }
        String providerCode = current.map(PersonApplication::providerCode).orElseGet(this::defaultProvider);
        requireProviderEnabled(providerCode);
        Long accountProfileId = repository.findEffectiveProfileIdByUser(userId);
        Long identityProfileId = fields.identityKey() == null
            ? null : repository.findActiveProfileIdByIdentity(fields.identityKey());
        Long targetProfileId = accountProfileId == null ? identityProfileId : accountProfileId;
        PersonApplication saved = repository.saveDraft(userId, providerCode,
            new PersonDraftUpdate(fields, targetProfileId, command.expectedVersion()));
        return PersonApplicationView.from(saved);
    }

    @DSTransactional
    public PersonApplicationView submit(long userId, int expectedVersion) {
        requireUserId(userId);
        PersonApplication application = repository.lockOpenByUserId(userId);
        if (application.applicantUserId() != userId || !EDITABLE_STATUSES.contains(application.status())) {
            throw failure("PERSON_APPLICATION_NOT_EDITABLE");
        }
        if (application.version() != expectedVersion) {
            throw failure("PERSON_APPLICATION_VERSION_CONFLICT");
        }
        validateComplete(application.fields());
        requireProviderEnabled(application.providerCode());
        repository.requireSubmissionAllowed(userId, application.targetProfileId(),
            application.fields().identityKey());
        MaterialOwnerKey working = owner(MaterialOwnerType.WORKING, application.personApplicationId());
        materials.validateRequired(working, application.fields().documentTypeCode(), Set.of("ALWAYS"));

        int snapshotVersion = application.submissionSeq() + 1;
        Instant submittedTime = clock.instant();
        PersonSubmission submission = repository.insertSubmission(
            application, application.fields(), snapshotVersion, submittedTime);
        materials.snapshotImmutable(working, owner(MaterialOwnerType.SUBMISSION,
            submission.personSubmissionId()));
        PersonApplication waiting = repository.markWaiting(application.personApplicationId(), snapshotVersion,
            expectedVersion, submittedTime);
        startVerificationAttempt(new PersonVerificationStartAttemptCommand(application.personApplicationId(),
            submission.personSubmissionId(), fingerprint(application.fields(), snapshotVersion)));
        workflow.start(application.personApplicationId(), submission.personSubmissionId(), snapshotVersion);
        return PersonApplicationView.from(waiting);
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
        PersonApplication application = repository.lockById(applicationId);
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
            PersonSubmission submission = repository.requireSubmission(applicationId, snapshotVersion);
            PersonPublication publication = repository.publishApproved(applicationId, snapshotVersion, clock.instant());
            materials.snapshotImmutable(owner(MaterialOwnerType.SUBMISSION, submission.personSubmissionId()),
                owner(MaterialOwnerType.VERSION, publication.personVersionId()));
        } else if (EVENT_STATUSES.contains(status)) {
            repository.updateWorkflowStatus(applicationId, snapshotVersion, status,
                application.version(), clock.instant());
        }
    }

    private String normalizeDecision(Map<String, Object> params) {
        Object value = params == null ? null : params.get("profileDecision");
        return value == null ? "" : value.toString().strip().toUpperCase(Locale.ROOT);
    }

    private void validateDraft(PersonIdentityFields fields) {
        if (length(fields.fullName()) > 100 || length(fields.documentNumber()) > 128) {
            throw failure("PERSON_FIELD_TOO_LONG");
        }
        if (fields.gender() != null && !GENDERS.contains(fields.gender())) {
            throw failure("PERSON_GENDER_INVALID");
        }
        if (fields.documentTypeCode() != null) {
            DocumentTypeRule rule = documentRule(fields.documentTypeCode());
            if (fields.documentNumber() != null && !Pattern.matches(rule.numberPattern(), fields.documentNumber())) {
                throw failure("PERSON_DOCUMENT_NUMBER_INVALID");
            }
        }
        validateDates(fields, false);
    }

    private void validateComplete(PersonIdentityFields fields) {
        validateDraft(fields);
        if (fields.fullName() == null || fields.documentTypeCode() == null || fields.documentNumber() == null
            || fields.gender() == null || fields.birthDate() == null) {
            throw failure("PERSON_FIELDS_INCOMPLETE");
        }
        DocumentTypeRule rule = documentRule(fields.documentTypeCode());
        if (!Pattern.matches(rule.numberPattern(), fields.documentNumber())) {
            throw failure("PERSON_DOCUMENT_NUMBER_INVALID");
        }
        if (rule.validityRequired() && (fields.validFrom() == null || fields.validUntil() == null)) {
            throw failure("PERSON_DOCUMENT_VALIDITY_REQUIRED");
        }
        validateDates(fields, true);
    }

    private void validateDates(PersonIdentityFields fields, boolean complete) {
        LocalDate today = LocalDate.now(clock);
        if (fields.birthDate() != null && fields.birthDate().isAfter(today)) {
            throw failure("PERSON_BIRTH_DATE_INVALID");
        }
        if (fields.validFrom() != null && fields.validUntil() != null
            && fields.validFrom().isAfter(fields.validUntil())) {
            throw failure("PERSON_DOCUMENT_VALIDITY_INVALID");
        }
        if (complete && fields.validFrom() != null && fields.validUntil() != null
            && (fields.validFrom().isAfter(today) || fields.validUntil().isBefore(today))) {
            throw failure("PERSON_DOCUMENT_EXPIRED");
        }
    }

    private DocumentTypeRule documentRule(String documentTypeCode) {
        return repository.findDocumentType(documentTypeCode)
            .orElseThrow(() -> failure("PERSON_DOCUMENT_TYPE_UNAVAILABLE"));
    }

    private String defaultProvider() {
        String providerCode = configService.getConfigValue(DEFAULT_PROVIDER_KEY);
        if (providerCode == null || providerCode.isBlank()) {
            throw failure("PERSON_PROVIDER_NOT_CONFIGURED");
        }
        return providerCode.strip();
    }

    private void requireProviderEnabled(String providerCode) {
        try {
            providers.requireEnabled(providerCode);
        } catch (PersonVerificationException exception) {
            throw new PersonApplicationException("PERSON_PROVIDER_UNAVAILABLE", exception);
        }
    }

    private void startVerificationAttempt(PersonVerificationStartAttemptCommand command) {
        try {
            attempts.startAttempt(command);
        } catch (PersonVerificationException exception) {
            throw new PersonApplicationException("PERSON_PROVIDER_UNAVAILABLE", exception);
        }
    }

    private String expectedFlowCode() {
        String flowCode = configService.getConfigValue(FLOW_CODE_KEY);
        return flowCode == null ? "" : flowCode.strip();
    }

    private String fingerprint(PersonIdentityFields fields, int snapshotVersion) {
        String canonical = fields.identityKey() + "\n" + snapshotVersion;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private MaterialOwnerKey owner(MaterialOwnerType type, long ownerId) {
        return new MaterialOwnerKey(ProfileType.PERSON, type, ownerId);
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

    private String normalizeStatus(String status) {
        return status == null ? "" : status.strip().toUpperCase(java.util.Locale.ROOT);
    }

    private int length(String value) {
        return value == null ? 0 : value.length();
    }

    private void requireUserId(long userId) {
        if (userId <= 0) {
            throw failure("PERSON_USER_INVALID");
        }
    }

    private PersonApplicationException failure(String category) {
        return new PersonApplicationException(category);
    }
}

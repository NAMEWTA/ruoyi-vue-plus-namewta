package org.dromara.profile.person.service.impl;

import org.dromara.profile.person.domain.application.PersonDocumentTypeRule;
import org.dromara.profile.person.domain.application.PersonApplication;
import org.dromara.profile.person.domain.vo.PersonApplicationVo;
import org.dromara.profile.person.domain.bo.PersonApplicationSaveBo;
import org.dromara.profile.person.domain.application.PersonDraftUpdate;
import org.dromara.profile.person.domain.application.PersonIdentityFields;
import org.dromara.profile.person.domain.application.PersonPublication;
import org.dromara.profile.person.domain.application.PersonSubmission;
import org.dromara.profile.person.domain.application.PersonActiveProjection;
import org.dromara.profile.person.domain.vo.PersonApplicationRow;
import org.dromara.profile.person.domain.vo.PersonBindingEventRow;
import org.dromara.profile.person.domain.vo.PersonBindingRow;
import org.dromara.profile.person.domain.vo.PersonDocumentTypeRow;
import org.dromara.profile.person.domain.vo.PersonProfileRow;
import org.dromara.profile.person.domain.vo.PersonSubmissionRow;
import org.dromara.profile.person.domain.vo.PersonVersionRow;
import org.dromara.profile.person.mapper.PersonApplicationMapper;
import org.dromara.profile.person.domain.exception.PersonApplicationException;
import org.dromara.profile.person.service.IPersonApplicationService;
import org.dromara.profile.person.service.PersonWorkflowGateway;
import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.dromara.profile.api.domain.ProfileType;
import org.dromara.profile.api.material.ProfileMaterialPort;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerKey;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerType;
import org.dromara.profile.person.domain.exception.PersonVerificationException;
import org.dromara.profile.person.domain.verification.PersonVerificationStartAttemptCommand;
import org.dromara.system.api.ConfigService;
import org.dromara.workflow.api.event.ProcessEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class PersonApplicationServiceImpl implements IPersonApplicationService {

    private static final String DEFAULT_PROVIDER_KEY = "profile.person.provider.default";
    private static final String FLOW_CODE_KEY = "profile.person.flowCode";
    private static final Set<String> EDITABLE_STATUSES = Set.of("DRAFT", "BACK", "CANCEL");
    private static final Set<String> EVENT_STATUSES = Set.of("BACK", "CANCEL", "INVALID", "TERMINATION");
    private static final Set<String> GENDERS = Set.of("MALE", "FEMALE", "UNKNOWN");

    private final PersonApplicationMapper mapper;
    private final JsonMapper jsonMapper;
    private final ProfileMaterialPort materials;
    private final PersonVerificationProviderRegistry providers;
    private final PersonVerificationAttemptCoordinator attempts;
    private final PersonWorkflowGateway workflow;
    private final ConfigService configService;
    private final Clock clock;

    @Autowired
    public PersonApplicationServiceImpl(PersonApplicationMapper mapper, JsonMapper jsonMapper,
                                    ProfileMaterialPort materials,
                                    PersonVerificationProviderRegistry providers,
                                    PersonVerificationAttemptCoordinator attempts,
                                    PersonWorkflowGateway workflow, ConfigService configService) {
        this(mapper, jsonMapper, materials, providers, attempts, workflow, configService, Clock.systemUTC());
    }

    PersonApplicationServiceImpl(PersonApplicationMapper mapper, JsonMapper jsonMapper,
                             ProfileMaterialPort materials,
                             PersonVerificationProviderRegistry providers,
                             PersonVerificationAttemptCoordinator attempts,
                             PersonWorkflowGateway workflow, ConfigService configService, Clock clock) {
        this.mapper = mapper;
        this.jsonMapper = jsonMapper;
        this.materials = materials;
        this.providers = providers;
        this.attempts = attempts;
        this.workflow = workflow;
        this.configService = configService;
        this.clock = clock;
    }

    public Optional<PersonApplicationVo> current(long userId) {
        requireUserId(userId);
        return findOpenByUserId(userId).map(PersonApplicationVo::from);
    }

    @DSTransactional
    public PersonApplicationVo save(long userId, PersonApplicationSaveBo command) {
        requireUserId(userId);
        PersonIdentityFields fields = PersonIdentityFields.normalize(command);
        validateDraft(fields);
        Optional<PersonApplication> current = findOpenByUserId(userId);
        if (current.isPresent() && !current.get().editable()) {
            throw failure("PERSON_APPLICATION_READ_ONLY");
        }
        String providerCode = current.map(PersonApplication::providerCode).orElseGet(this::defaultProvider);
        requireProviderEnabled(providerCode);
        Long accountProfileId = findEffectiveProfileIdByUser(userId);
        Long identityProfileId = fields.identityKey() == null
            ? null : findActiveProfileIdByIdentity(fields.identityKey());
        Long targetProfileId = accountProfileId == null ? identityProfileId : accountProfileId;
        PersonApplication saved = saveDraft(userId, providerCode,
            new PersonDraftUpdate(fields, targetProfileId, command.expectedVersion()));
        return PersonApplicationVo.from(saved);
    }

    @DSTransactional
    public PersonApplicationVo submit(long userId, int expectedVersion) {
        requireUserId(userId);
        PersonApplication application = lockOpenByUserId(userId);
        if (application.applicantUserId() != userId || !EDITABLE_STATUSES.contains(application.status())) {
            throw failure("PERSON_APPLICATION_NOT_EDITABLE");
        }
        if (application.version() != expectedVersion) {
            throw failure("PERSON_APPLICATION_VERSION_CONFLICT");
        }
        validateComplete(application.fields());
        requireProviderEnabled(application.providerCode());
        requireSubmissionAllowed(userId, application.targetProfileId(),
            application.fields().identityKey());
        MaterialOwnerKey working = owner(MaterialOwnerType.WORKING, application.personApplicationId());
        materials.validateRequired(working, application.fields().documentTypeCode(), Set.of("ALWAYS"));

        int snapshotVersion = application.submissionSeq() + 1;
        Instant submittedTime = clock.instant();
        PersonSubmission submission = insertSubmission(
            application, application.fields(), snapshotVersion, submittedTime);
        materials.snapshotImmutable(working, owner(MaterialOwnerType.SUBMISSION,
            submission.personSubmissionId()));
        PersonApplication waiting = markWaiting(application.personApplicationId(), snapshotVersion,
            expectedVersion, submittedTime);
        startVerificationAttempt(new PersonVerificationStartAttemptCommand(application.personApplicationId(),
            submission.personSubmissionId(), fingerprint(application.fields(), snapshotVersion)));
        workflow.start(application.personApplicationId(), submission.personSubmissionId(), snapshotVersion);
        return PersonApplicationVo.from(waiting);
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
        PersonApplication application = lockById(applicationId);
        if (application.submissionSeq() != snapshotVersion || !"WAITING".equals(application.status())) {
            return;
        }
        String status = normalizeStatus(event.getStatus());
        if ("FINISH".equals(status)) {
            if ("REJECT".equals(normalizeDecision(event.getParams()))) {
                updateWorkflowStatus(applicationId, snapshotVersion, "INVALID",
                    application.version(), clock.instant());
                return;
            }
            PersonSubmission submission = requireSubmission(applicationId, snapshotVersion);
            PersonPublication publication = publishApproved(applicationId, snapshotVersion, clock.instant());
            materials.snapshotImmutable(owner(MaterialOwnerType.SUBMISSION, submission.personSubmissionId()),
                owner(MaterialOwnerType.VERSION, publication.personVersionId()));
        } else if (EVENT_STATUSES.contains(status)) {
            updateWorkflowStatus(applicationId, snapshotVersion, status,
                application.version(), clock.instant());
        }
    }

    Optional<PersonApplication> findOpenByUserId(long userId) {
        return Optional.ofNullable(mapper.selectOpenByUserId(userId)).map(this::application);
    }

    PersonApplication lockOpenByUserId(long userId) {
        return requireApplication(mapper.lockOpenByUserId(userId));
    }

    PersonApplication lockById(long applicationId) {
        return requireApplication(mapper.lockApplicationById(applicationId));
    }

    @Override
    public Optional<PersonDocumentTypeRule> findDocumentType(String documentTypeCode) {
        PersonDocumentTypeRow row = mapper.selectDocumentType(documentTypeCode);
        return Optional.ofNullable(row).map(value -> new PersonDocumentTypeRule(value.getDocumentTypeCode(),
            value.getNumberPattern(), "Y".equals(value.getValidityRequired())));
    }

    Long findActiveProfileIdByIdentity(String identityKey) {
        return identityKey == null ? null : mapper.selectActiveProfileIdByIdentity(identityKey);
    }

    Long findEffectiveProfileIdByUser(long userId) {
        return mapper.selectEffectiveProfileIdByUser(userId);
    }

    PersonApplication saveDraft(long userId, String providerCode, PersonDraftUpdate update) {
        PersonApplicationRow current = mapper.lockOpenByUserId(userId);
        try {
            if (current == null) {
                if (update.expectedVersion() != 0) {
                    throw failure("PERSON_APPLICATION_VERSION_CONFLICT");
                }
                PersonApplicationRow inserted = draftRow(IdWorker.getId(), userId, providerCode, update);
                requireChanged(mapper.insertApplication(inserted), "PERSON_APPLICATION_CREATE_CONFLICT");
            } else {
                if (!editable(current.getStatus()) || intValue(current.getVersion()) != update.expectedVersion()) {
                    throw failure("PERSON_APPLICATION_VERSION_CONFLICT");
                }
                PersonApplicationRow changed = draftRow(current.getPersonApplicationId(), userId,
                    current.getProviderCode(), update);
                changed.setVersion(update.expectedVersion());
                requireChanged(mapper.updateDraft(changed), "PERSON_APPLICATION_VERSION_CONFLICT");
            }
        } catch (DuplicateKeyException exception) {
            throw failure("PERSON_APPLICATION_IDENTITY_CONFLICT", exception);
        }
        return requireApplication(mapper.selectOpenByUserId(userId));
    }

    void requireSubmissionAllowed(long userId, Long targetProfileId, String identityKey) {
        if (identityKey == null || identityKey.isBlank()) {
            throw failure("PERSON_IDENTITY_REQUIRED");
        }
        PersonProfileRow profile = mapper.lockActiveProfileByIdentity(identityKey);
        PersonBindingRow userBinding = mapper.lockEffectiveBindingByUser(userId);
        if (userBinding != null) {
            if ("SUSPENDED".equals(userBinding.getStatus())) {
                throw failure("PERSON_BINDING_SUSPENDED");
            }
            if (targetProfileId == null || !userBinding.getPersonProfileId().equals(targetProfileId)) {
                throw failure("PERSON_ACCOUNT_ALREADY_BOUND");
            }
            if (profile != null && !profile.getPersonProfileId().equals(targetProfileId)) {
                throw failure("PERSON_IDENTITY_CONFLICT");
            }
            return;
        }
        PersonBindingRow profileBinding = profile == null
            ? null : mapper.lockEffectiveBindingByProfile(profile.getPersonProfileId());
        if (profileBinding != null && profileBinding.getUserId() != userId) {
            throw failure("PERSON_REBIND_CONFIRMATION_REQUIRED");
        }
        if (profileBinding != null && "SUSPENDED".equals(profileBinding.getStatus())) {
            throw failure("PERSON_BINDING_SUSPENDED");
        }
    }

    PersonSubmission insertSubmission(PersonApplication application, PersonIdentityFields fields,
                                      int submissionSeq, Instant submittedTime) {
        PersonSubmissionRow row = submissionRow(application, fields, submissionSeq, submittedTime);
        try {
            requireChanged(mapper.insertSubmission(row), "PERSON_SUBMISSION_CONFLICT");
        } catch (DuplicateKeyException exception) {
            throw failure("PERSON_SUBMISSION_CONFLICT", exception);
        }
        return submission(row);
    }

    @Override
    public PersonSubmission requireSubmission(long applicationId, int submissionSeq) {
        PersonSubmissionRow row = mapper.selectSubmission(applicationId, submissionSeq);
        if (row == null) {
            throw failure("PERSON_SUBMISSION_NOT_FOUND");
        }
        return submission(row);
    }

    PersonApplication markWaiting(long applicationId, int submissionSeq, int expectedVersion,
                                  Instant submittedTime) {
        requireChanged(mapper.markWaiting(applicationId, submissionSeq, expectedVersion, submittedTime),
            "PERSON_APPLICATION_VERSION_CONFLICT");
        return requireApplication(mapper.lockApplicationById(applicationId));
    }

    @Override
    @DSTransactional
    public PersonPublication publishApproved(long applicationId, int snapshotVersion, Instant finishedTime) {
        PersonApplicationRow application = mapper.lockApplicationById(applicationId);
        if (application == null || !"WAITING".equals(application.getStatus())
            || intValue(application.getSubmissionSeq()) != snapshotVersion) {
            throw failure("PERSON_APPLICATION_DECISION_CONFLICT");
        }
        if ("Y".equals(application.getRebindIntent())) {
            throw failure("PERSON_REBIND_NOT_SUPPORTED_BY_THIS_COMMAND");
        }
        PersonSubmissionRow submission = mapper.selectSubmission(applicationId, snapshotVersion);
        if (submission == null) {
            throw failure("PERSON_SUBMISSION_NOT_FOUND");
        }

        try {
            PublicationTarget target = publicationTarget(application.getApplicantUserId(), submission);
            PersonVersionRow currentVersion = mapper.selectCurrentVersionForUpdate(target.profile().getPersonProfileId());
            int nextVersion = currentVersion == null ? 1 : intValue(currentVersion.getVersionNo()) + 1;
            if (currentVersion != null) {
                requireChanged(mapper.supersedeVersion(currentVersion.getPersonVersionId()),
                    "PERSON_PROFILE_VERSION_CONFLICT");
            }

            PersonVersionRow version = versionRow(target.profile().getPersonProfileId(), nextVersion,
                submission, finishedTime);
            requireChanged(mapper.insertVersion(version), "PERSON_PROFILE_VERSION_CONFLICT");
            PersonProfileRow updatedProfile = profileRow(target.profile().getPersonProfileId(),
                target.profile().getPreviousProfileId(), version.getPersonVersionId(), submission,
                target.profile().getVersion());
            requireChanged(mapper.updateProfile(updatedProfile), "PERSON_PROFILE_VERSION_CONFLICT");

            PersonBindingRow binding = target.binding();
            if (binding == null) {
                binding = bindingRow(target.profile().getPersonProfileId(), application.getApplicantUserId(),
                    submission.getPersonSubmissionId(), finishedTime);
                requireChanged(mapper.insertBinding(binding), "PERSON_BINDING_CONFLICT");
                requireChanged(mapper.insertBindingEvent(bindingEvent(binding, submission.getPersonSubmissionId(),
                    finishedTime)), "PERSON_BINDING_EVENT_CONFLICT");
            }

            requireChanged(mapper.finishApplication(applicationId, snapshotVersion,
                intValue(application.getDecisionVersion()), intValue(application.getVersion()), finishedTime),
                "PERSON_APPLICATION_DECISION_CONFLICT");
            return new PersonPublication(target.profile().getPersonProfileId(), version.getPersonVersionId(),
                binding.getPersonBindingId(), target.successor());
        } catch (DuplicateKeyException exception) {
            throw failure("PERSON_PUBLICATION_CONFLICT", exception);
        }
    }

    void updateWorkflowStatus(long applicationId, int snapshotVersion, String status,
                              int expectedVersion, Instant occurredTime) {
        requireChanged(mapper.updateWorkflowStatus(applicationId, snapshotVersion, status,
            expectedVersion, occurredTime), "PERSON_APPLICATION_DECISION_CONFLICT");
    }

    List<PersonActiveProjection> findActiveProjections(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectActiveProjections(userIds).stream().map(row -> new PersonActiveProjection(
            row.getUserId(), row.getPersonProfileId(), row.getVerifiedAt())).toList();
    }

    private PublicationTarget publicationTarget(long userId, PersonSubmissionRow submission) {
        PersonProfileRow profile = mapper.lockActiveProfileByIdentity(submission.getIdentityKey());
        PersonBindingRow userBinding = mapper.lockEffectiveBindingByUser(userId);
        if (userBinding != null) {
            if ("SUSPENDED".equals(userBinding.getStatus())) {
                throw failure("PERSON_BINDING_SUSPENDED");
            }
            if (submission.getTargetProfileId() == null
                || !userBinding.getPersonProfileId().equals(submission.getTargetProfileId())) {
                throw failure("PERSON_ACCOUNT_ALREADY_BOUND");
            }
            if (profile != null && !profile.getPersonProfileId().equals(submission.getTargetProfileId())) {
                throw failure("PERSON_IDENTITY_CONFLICT");
            }
            PersonProfileRow target = profile == null
                ? mapper.lockActiveProfileById(submission.getTargetProfileId()) : profile;
            if (target == null) {
                throw failure("PERSON_PROFILE_NOT_FOUND");
            }
            PersonBindingRow targetBinding = mapper.lockEffectiveBindingByProfile(target.getPersonProfileId());
            if (targetBinding == null || !targetBinding.getPersonBindingId().equals(userBinding.getPersonBindingId())) {
                throw failure("PERSON_BINDING_CONFLICT");
            }
            if (!submission.getIdentityKey().equals(target.getIdentityKey())) {
                requireChanged(mapper.updateIdentityGuard(target.getPersonProfileId(), submission.getIdentityKey()),
                    "PERSON_IDENTITY_CONFLICT");
            }
            return new PublicationTarget(target, targetBinding, false);
        }
        PersonBindingRow profileBinding = profile == null
            ? null : mapper.lockEffectiveBindingByProfile(profile.getPersonProfileId());
        if (profileBinding != null && profileBinding.getUserId() != userId) {
            throw failure("PERSON_REBIND_CONFIRMATION_REQUIRED");
        }
        if (profileBinding != null && "SUSPENDED".equals(profileBinding.getStatus())) {
            throw failure("PERSON_BINDING_SUSPENDED");
        }
        if (profile != null) {
            return new PublicationTarget(profile, profileBinding, false);
        }

        PersonProfileRow revoked = mapper.lockLatestRevokedProfileByIdentity(submission.getIdentityKey());
        long profileId = IdWorker.getId();
        PersonProfileRow created = profileRow(profileId,
            revoked == null ? null : revoked.getPersonProfileId(), null, submission, 0);
        requireChanged(mapper.insertIdentityGuard(IdWorker.getId(), submission.getIdentityKey(), profileId),
            "PERSON_IDENTITY_CONFLICT");
        requireChanged(mapper.insertProfile(created), "PERSON_IDENTITY_CONFLICT");
        return new PublicationTarget(created, null, revoked != null);
    }

    private PersonApplicationRow draftRow(long applicationId, long userId, String providerCode,
                                          PersonDraftUpdate update) {
        PersonApplicationRow row = new PersonApplicationRow();
        row.setPersonApplicationId(applicationId);
        row.setApplicantUserId(userId);
        row.setTargetProfileId(update.targetProfileId());
        row.setProviderCode(providerCode);
        copy(row, update.fields());
        return row;
    }

    private PersonSubmissionRow submissionRow(PersonApplication application, PersonIdentityFields fields,
                                              int submissionSeq, Instant submittedTime) {
        PersonSubmissionRow row = new PersonSubmissionRow();
        row.setPersonSubmissionId(IdWorker.getId());
        row.setPersonApplicationId(application.personApplicationId());
        row.setSubmissionSeq(submissionSeq);
        row.setApplicantUserId(application.applicantUserId());
        row.setProviderCode(application.providerCode());
        row.setRebindIntent(application.rebindIntent() ? "Y" : "N");
        row.setTargetProfileId(application.targetProfileId());
        row.setExpectedBindingId(application.expectedBindingId());
        row.setExpectedBindingVersion(application.expectedBindingVersion());
        row.setFieldSnapshotJson(jsonMapper.writeValueAsString(fields));
        row.setSubmittedTime(submittedTime);
        copy(row, fields);
        return row;
    }

    private PersonProfileRow profileRow(long profileId, Long previousProfileId, Long currentVersionId,
                                        PersonSubmissionRow submission, Integer version) {
        PersonProfileRow row = new PersonProfileRow();
        row.setPersonProfileId(profileId);
        row.setPreviousProfileId(previousProfileId);
        row.setCurrentVersionId(currentVersionId);
        row.setVersion(version);
        row.setStatus("ACTIVE");
        copy(row, submission);
        return row;
    }

    private PersonVersionRow versionRow(long profileId, int versionNo, PersonSubmissionRow submission,
                                        Instant publishedTime) {
        PersonVersionRow row = new PersonVersionRow();
        row.setPersonVersionId(IdWorker.getId());
        row.setPersonProfileId(profileId);
        row.setVersionNo(versionNo);
        row.setSourceType("USER_SUBMISSION");
        row.setSourceId(submission.getPersonSubmissionId());
        row.setStatus("CURRENT");
        row.setPublishedTime(publishedTime);
        copy(row, submission);
        return row;
    }

    private PersonBindingRow bindingRow(long profileId, long userId, long sourceId, Instant boundTime) {
        PersonBindingRow row = new PersonBindingRow();
        row.setPersonBindingId(IdWorker.getId());
        row.setPersonProfileId(profileId);
        row.setUserId(userId);
        row.setStatus("ACTIVE");
        row.setBindingVersion(1);
        row.setSourceType("USER_SUBMISSION");
        row.setSourceId(sourceId);
        row.setBoundTime(boundTime);
        return row;
    }

    private PersonBindingEventRow bindingEvent(PersonBindingRow binding, long sourceId, Instant occurredTime) {
        PersonBindingEventRow row = new PersonBindingEventRow();
        row.setPersonBindingEventId(IdWorker.getId());
        row.setPersonBindingId(binding.getPersonBindingId());
        row.setPersonProfileId(binding.getPersonProfileId());
        row.setUserId(binding.getUserId());
        row.setEventType("ACTIVE");
        row.setBindingVersion(binding.getBindingVersion());
        row.setSourceType("USER_SUBMISSION");
        row.setSourceId(sourceId);
        row.setReason("PERSON_VERIFICATION_APPROVED");
        row.setOccurredTime(occurredTime);
        return row;
    }

    private PersonApplication application(PersonApplicationRow row) {
        return new PersonApplication(row.getPersonApplicationId(), row.getApplicantUserId(),
            row.getTargetProfileId(), row.getStatus(), fields(row), row.getProviderCode(),
            intValue(row.getSubmissionSeq()), "Y".equals(row.getRebindIntent()), row.getExpectedBindingId(),
            row.getExpectedBindingVersion(), intValue(row.getDecisionVersion()), intValue(row.getVersion()),
            row.getSubmittedTime(), row.getFinishedTime());
    }

    private PersonSubmission submission(PersonSubmissionRow row) {
        return new PersonSubmission(row.getPersonSubmissionId(), row.getPersonApplicationId(),
            intValue(row.getSubmissionSeq()), row.getApplicantUserId(), fields(row), row.getProviderCode(),
            row.getSubmittedTime());
    }

    private PersonIdentityFields fields(PersonApplicationRow row) {
        return new PersonIdentityFields(row.getFullName(), row.getDocumentTypeCode(), row.getDocumentNumber(),
            row.getIdentityKey(), row.getGender(), row.getBirthDate(), row.getValidFrom(), row.getValidUntil());
    }

    private PersonIdentityFields fields(PersonSubmissionRow row) {
        return new PersonIdentityFields(row.getFullName(), row.getDocumentTypeCode(), row.getDocumentNumber(),
            row.getIdentityKey(), row.getGender(), row.getBirthDate(), row.getValidFrom(), row.getValidUntil());
    }

    private void copy(PersonApplicationRow target, PersonIdentityFields fields) {
        target.setFullName(fields.fullName());
        target.setDocumentTypeCode(fields.documentTypeCode());
        target.setDocumentNumber(fields.documentNumber());
        target.setIdentityKey(fields.identityKey());
        target.setGender(fields.gender());
        target.setBirthDate(fields.birthDate());
        target.setValidFrom(fields.validFrom());
        target.setValidUntil(fields.validUntil());
    }

    private void copy(PersonSubmissionRow target, PersonIdentityFields fields) {
        target.setFullName(fields.fullName());
        target.setDocumentTypeCode(fields.documentTypeCode());
        target.setDocumentNumber(fields.documentNumber());
        target.setIdentityKey(fields.identityKey());
        target.setGender(fields.gender());
        target.setBirthDate(fields.birthDate());
        target.setValidFrom(fields.validFrom());
        target.setValidUntil(fields.validUntil());
    }

    private void copy(PersonProfileRow target, PersonSubmissionRow source) {
        target.setFullName(source.getFullName());
        target.setDocumentTypeCode(source.getDocumentTypeCode());
        target.setDocumentNumber(source.getDocumentNumber());
        target.setIdentityKey(source.getIdentityKey());
        target.setGender(source.getGender());
        target.setBirthDate(source.getBirthDate());
        target.setValidFrom(source.getValidFrom());
        target.setValidUntil(source.getValidUntil());
    }

    private void copy(PersonVersionRow target, PersonSubmissionRow source) {
        target.setFullName(source.getFullName());
        target.setDocumentTypeCode(source.getDocumentTypeCode());
        target.setDocumentNumber(source.getDocumentNumber());
        target.setIdentityKey(source.getIdentityKey());
        target.setGender(source.getGender());
        target.setBirthDate(source.getBirthDate());
        target.setValidFrom(source.getValidFrom());
        target.setValidUntil(source.getValidUntil());
    }

    private PersonApplication requireApplication(PersonApplicationRow row) {
        if (row == null) {
            throw failure("PERSON_APPLICATION_NOT_FOUND");
        }
        return application(row);
    }

    private void requireChanged(int changed, String category) {
        if (changed != 1) {
            throw failure(category);
        }
    }

    private boolean editable(String status) {
        return "DRAFT".equals(status) || "BACK".equals(status) || "CANCEL".equals(status);
    }

    private int intValue(Integer value) {
        return value == null ? 0 : value;
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
            PersonDocumentTypeRule rule = documentRule(fields.documentTypeCode());
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
        PersonDocumentTypeRule rule = documentRule(fields.documentTypeCode());
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

    private PersonDocumentTypeRule documentRule(String documentTypeCode) {
        return findDocumentType(documentTypeCode)
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

    private PersonApplicationException failure(String category, Throwable cause) {
        return new PersonApplicationException(category, cause);
    }

    private record PublicationTarget(PersonProfileRow profile, PersonBindingRow binding, boolean successor) {
    }
}

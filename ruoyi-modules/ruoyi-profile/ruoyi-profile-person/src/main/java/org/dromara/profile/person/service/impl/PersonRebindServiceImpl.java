package org.dromara.profile.person.service.impl;

import org.dromara.profile.person.service.IPersonRebindService;

import org.dromara.profile.person.domain.exception.PersonRebindException;
import org.dromara.profile.person.mapper.PersonRebindMapper;
import org.dromara.profile.person.mapper.PersonApplicationMapper;
import org.dromara.profile.person.event.PersonReboundEvent;
import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import org.dromara.profile.api.domain.ProfileType;
import org.dromara.profile.api.material.ProfileMaterialPort;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerKey;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerType;
import org.dromara.profile.person.domain.application.PersonDocumentTypeRule;
import org.dromara.profile.person.domain.application.PersonApplication;
import org.dromara.profile.person.domain.bo.PersonApplicationSaveBo;
import org.dromara.profile.person.domain.application.PersonIdentityFields;
import org.dromara.profile.person.domain.application.PersonRebindPublication;
import org.dromara.profile.person.domain.application.PersonSubmission;
import org.dromara.profile.person.service.PersonWorkflowGateway;
import org.dromara.profile.person.domain.vo.PersonApplicationRow;
import org.dromara.profile.person.domain.vo.PersonBindingEventRow;
import org.dromara.profile.person.domain.vo.PersonBindingRow;
import org.dromara.profile.person.domain.vo.PersonDocumentTypeRow;
import org.dromara.profile.person.domain.vo.PersonProfileRow;
import org.dromara.profile.person.domain.vo.PersonSubmissionRow;
import org.dromara.profile.person.domain.vo.PersonVersionRow;
import org.dromara.profile.person.domain.bo.PersonRebindConfirmBo;
import org.dromara.profile.person.domain.vo.PersonRebindConfirmationVo;
import org.dromara.profile.person.domain.bo.PersonRebindIdentityBo;
import org.dromara.profile.person.domain.bo.PersonRebindMatchBo;
import org.dromara.profile.person.domain.vo.PersonRebindMatchVo;
import org.dromara.profile.person.domain.bo.PersonRebindProbeBo;
import org.dromara.profile.person.domain.vo.PersonRebindProbeVo;
import org.dromara.profile.person.domain.vo.PersonRebindSubmissionVo;
import org.dromara.profile.person.domain.bo.PersonRebindSubmitBo;
import org.dromara.profile.person.domain.vo.PersonRebindUnbindVo;
import org.dromara.profile.person.domain.exception.PersonVerificationException;
import org.dromara.profile.person.domain.verification.PersonVerificationStartAttemptCommand;
import org.dromara.system.api.UserService;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class PersonRebindServiceImpl implements IPersonRebindService {

    private static final Set<String> EDITABLE_STATUSES = Set.of("DRAFT", "BACK", "CANCEL");
    private static final Set<String> GENDERS = Set.of("MALE", "FEMALE", "UNKNOWN");

    private final PersonRebindMapper mapper;
    private final PersonApplicationMapper applicationMapper;
    private final JsonMapper jsonMapper;
    private final ProfileMaterialPort materials;
    private final PersonVerificationProviderRegistry providers;
    private final PersonVerificationAttemptCoordinator attempts;
    private final PersonWorkflowGateway workflow;
    private final UserService users;
    private final Clock clock;

    @Autowired
    public PersonRebindServiceImpl(PersonRebindMapper mapper, PersonApplicationMapper applicationMapper,
                               JsonMapper jsonMapper,
                               ProfileMaterialPort materials, PersonVerificationProviderRegistry providers,
                               PersonVerificationAttemptCoordinator attempts, PersonWorkflowGateway workflow,
                               UserService users) {
        this(mapper, applicationMapper, jsonMapper, materials, providers, attempts, workflow, users,
            Clock.systemUTC());
    }

    PersonRebindServiceImpl(PersonRebindMapper mapper, PersonApplicationMapper applicationMapper,
                        JsonMapper jsonMapper,
                        ProfileMaterialPort materials, PersonVerificationProviderRegistry providers,
                        PersonVerificationAttemptCoordinator attempts, PersonWorkflowGateway workflow,
                        UserService users, Clock clock) {
        this.mapper = mapper;
        this.applicationMapper = applicationMapper;
        this.jsonMapper = jsonMapper;
        this.materials = materials;
        this.providers = providers;
        this.attempts = attempts;
        this.workflow = workflow;
        this.users = users;
        this.clock = clock;
    }

    public PersonRebindProbeVo probe(PersonRebindProbeBo command) {
        if (command == null) {
            throw failure("PERSON_REBIND_PROBE_INVALID");
        }
        String type = upper(command.documentTypeCode());
        String number = upper(command.documentNumber());
        if (type == null || number == null) {
            throw failure("PERSON_REBIND_PROBE_INVALID");
        }
        return new PersonRebindProbeVo(probeStatus(type + ":" + number));
    }

    public PersonRebindMatchVo match(long userId, PersonRebindMatchBo command) {
        PersonIdentityFields fields = safeIdentity(command == null ? null : command.identity());
        if (fields == null || findEffectiveProfileIdByUser(userId) != null) {
            return unavailable();
        }
        Optional<PersonApplication> current = findOpenByUserId(userId);
        if (current.isEmpty() || !EDITABLE_STATUSES.contains(current.get().status())
            || !same(current.get().fields(), fields)) {
            return unavailable();
        }
        Optional<PersonRebindMapper.RebindCandidateRow> candidate = findExactCandidate(fields);
        if (candidate.isEmpty() || candidate.get().getOldUserId() == userId) {
            return unavailable();
        }
        return new PersonRebindMatchVo("REBIND_AVAILABLE", maskedPhone(candidate.get().getOldUserId()));
    }

    @DSTransactional
    public PersonRebindConfirmationVo confirm(long userId, PersonRebindConfirmBo command) {
        requireUserId(userId);
        if (command == null) {
            throw failure("PERSON_REBIND_NOT_AVAILABLE");
        }
        PersonIdentityFields fields = requireIdentity(command.identity());
        PersonApplicationRow application = lockOpenApplication(userId);
        if (!EDITABLE_STATUSES.contains(application.getStatus())
            || intValue(application.getVersion()) != command.expectedVersion()
            || !same(application, fields)) {
            throw failure("PERSON_REBIND_NOT_AVAILABLE");
        }
        requireApplicantUnbound(userId);
        PersonRebindMapper.RebindCandidateRow candidate = findExactCandidate(fields)
            .filter(value -> value.getOldUserId() != userId)
            .orElseThrow(() -> failure("PERSON_REBIND_NOT_AVAILABLE"));
        candidate = lockCandidate(candidate, fields);
        int version = confirm(application, candidate, command.expectedVersion());
        return new PersonRebindConfirmationVo("CONFIRMED", maskedPhone(candidate.getOldUserId()), version);
    }

    @DSTransactional
    public PersonRebindSubmissionVo submit(long userId, PersonRebindSubmitBo command) {
        requireUserId(userId);
        if (command == null) {
            throw failure("PERSON_REBIND_SUBMIT_INVALID");
        }
        PersonApplication application = lockOpenByUserId(userId);
        if (application.applicantUserId() != userId || !EDITABLE_STATUSES.contains(application.status())
            || application.version() != command.expectedVersion()) {
            throw failure("PERSON_REBIND_VERSION_CONFLICT");
        }
        validateComplete(application.fields());
        requireProviderEnabled(application.providerCode());
        requireApplicantUnbound(userId);
        requireFrozenCandidate(application);

        MaterialOwnerKey working = owner(MaterialOwnerType.WORKING, application.personApplicationId());
        materials.validateRequired(working, application.fields().documentTypeCode(), Set.of("ALWAYS"));
        int snapshotVersion = application.submissionSeq() + 1;
        Instant submittedTime = clock.instant();
        PersonSubmission submission = insertSubmission(application, application.fields(),
            snapshotVersion, submittedTime);
        materials.snapshotImmutable(working, owner(MaterialOwnerType.SUBMISSION,
            submission.personSubmissionId()));
        PersonApplication waiting = markWaiting(application.personApplicationId(), snapshotVersion,
            command.expectedVersion(), submittedTime);
        startVerificationAttempt(new PersonVerificationStartAttemptCommand(application.personApplicationId(),
            submission.personSubmissionId(), fingerprint(application.fields(), snapshotVersion)));
        workflow.start(application.personApplicationId(), submission.personSubmissionId(), snapshotVersion);
        return new PersonRebindSubmissionVo(waiting.status(), waiting.submissionSeq(), waiting.version());
    }

    @DSTransactional
    public PersonRebindUnbindVo unbind(long userId) {
        requireUserId(userId);
        unbindBinding(userId, clock.instant());
        return new PersonRebindUnbindVo("UNBOUND");
    }

    @Override
    @DSTransactional
    public Optional<PersonRebindPublication> publishApprovedRebind(long applicationId, int snapshotVersion,
                                                                   Instant finishedTime) {
        PersonApplicationRow application = mapper.lockApplication(applicationId);
        if (application == null || !"WAITING".equals(application.getStatus())
            || intValue(application.getSubmissionSeq()) != snapshotVersion) {
            return Optional.empty();
        }
        if (!"Y".equals(application.getRebindIntent())) {
            return Optional.empty();
        }
        PersonSubmissionRow submission = mapper.lockSubmission(applicationId, snapshotVersion);
        if (submission == null || !"Y".equals(submission.getRebindIntent())) {
            throw failure("PERSON_REBIND_SNAPSHOT_INVALID");
        }
        requireFrozenSnapshot(application, submission);

        PersonProfileRow profile = mapper.lockProfile(submission.getTargetProfileId());
        PersonBindingRow oldBinding = mapper.lockExpectedBinding(submission.getExpectedBindingId(),
            submission.getTargetProfileId(), submission.getExpectedBindingVersion());
        if (profile == null || oldBinding == null || !same(submission, profile)) {
            throw failure("PERSON_REBIND_BINDING_CHANGED");
        }
        if (mapper.lockEffectiveBindingByUser(application.getApplicantUserId()) != null) {
            throw failure("PERSON_REBIND_APPLICANT_ALREADY_BOUND");
        }

        try {
            PersonVersionRow currentVersion = mapper.lockCurrentVersion(profile.getPersonProfileId());
            int nextVersion = currentVersion == null ? 1 : intValue(currentVersion.getVersionNo()) + 1;
            if (currentVersion != null) {
                requireChanged(mapper.supersedeVersion(currentVersion.getPersonVersionId()),
                    "PERSON_REBIND_PROFILE_VERSION_CONFLICT");
            }

            PersonVersionRow version = version(profile.getPersonProfileId(), nextVersion, submission, finishedTime);
            requireChanged(mapper.insertVersion(version), "PERSON_REBIND_PROFILE_VERSION_CONFLICT");
            PersonProfileRow updated = updatedProfile(profile, version.getPersonVersionId(), submission);
            requireChanged(mapper.updateProfile(updated), "PERSON_REBIND_PROFILE_VERSION_CONFLICT");

            int oldBindingVersion = intValue(oldBinding.getBindingVersion());
            requireChanged(mapper.unbind(oldBinding.getPersonBindingId(), oldBindingVersion,
                application.getApplicantUserId(), finishedTime), "PERSON_REBIND_BINDING_CHANGED");
            requireChanged(mapper.insertBindingEvent(bindingEvent(oldBinding, "UNBOUND",
                oldBindingVersion + 1, submission.getPersonSubmissionId(), "PERSON_REBIND_APPROVED", finishedTime)),
                "PERSON_REBIND_BINDING_EVENT_CONFLICT");

            PersonBindingRow newBinding = binding(profile.getPersonProfileId(), application.getApplicantUserId(),
                submission.getPersonSubmissionId(), finishedTime);
            requireChanged(mapper.insertBinding(newBinding), "PERSON_REBIND_BINDING_CONFLICT");
            requireChanged(mapper.insertBindingEvent(bindingEvent(newBinding, "ACTIVE", 1,
                submission.getPersonSubmissionId(), "PERSON_REBIND_APPROVED", finishedTime)),
                "PERSON_REBIND_BINDING_EVENT_CONFLICT");

            requireChanged(mapper.finishApplication(applicationId, snapshotVersion,
                intValue(application.getDecisionVersion()), intValue(application.getVersion()), finishedTime),
                "PERSON_REBIND_DECISION_CONFLICT");
            return Optional.of(new PersonRebindPublication(submission.getPersonSubmissionId(), version.getPersonVersionId(),
                new PersonReboundEvent(profile.getPersonProfileId(), applicationId, oldBinding.getUserId())));
        } catch (DuplicateKeyException exception) {
            throw failure("PERSON_REBIND_PUBLICATION_CONFLICT", exception);
        }
    }

    String probeStatus(String identityKey) {
        return mapper.selectProbeStatus(identityKey);
    }

    Optional<PersonRebindMapper.RebindCandidateRow> findExactCandidate(PersonIdentityFields fields) {
        return Optional.ofNullable(mapper.selectExactCandidate(fields.fullName(), fields.documentTypeCode(),
            fields.documentNumber(), fields.identityKey(), fields.gender(), fields.birthDate(),
            fields.validFrom(), fields.validUntil()));
    }

    PersonApplicationRow lockOpenApplication(long userId) {
        PersonApplicationRow application = mapper.lockOpenApplication(userId);
        if (application == null) {
            throw failure("PERSON_REBIND_APPLICATION_REQUIRED");
        }
        return application;
    }

    void requireApplicantUnbound(long userId) {
        if (mapper.lockEffectiveBindingByUser(userId) != null) {
            throw failure("PERSON_REBIND_APPLICANT_ALREADY_BOUND");
        }
    }

    int confirm(PersonApplicationRow application, PersonRebindMapper.RebindCandidateRow candidate,
                int expectedVersion) {
        int changed = mapper.confirmIntent(application.getPersonApplicationId(), application.getApplicantUserId(),
            candidate.getPersonProfileId(), candidate.getPersonBindingId(), candidate.getBindingVersion(),
            expectedVersion);
        requireChanged(changed, "PERSON_REBIND_VERSION_CONFLICT");
        return expectedVersion + 1;
    }

    PersonRebindMapper.RebindCandidateRow lockCandidate(
        PersonRebindMapper.RebindCandidateRow candidate, PersonIdentityFields fields) {
        PersonRebindMapper.RebindCandidateRow locked = mapper.lockFrozenCandidate(candidate.getPersonProfileId(),
            candidate.getPersonBindingId(), candidate.getBindingVersion());
        if (locked == null || !same(fields, locked)) {
            throw failure("PERSON_REBIND_NOT_AVAILABLE");
        }
        return locked;
    }

    PersonRebindMapper.RebindCandidateRow requireFrozenCandidate(PersonApplication application) {
        if (!application.rebindIntent() || application.targetProfileId() == null
            || application.expectedBindingId() == null || application.expectedBindingVersion() == null) {
            throw failure("PERSON_REBIND_CONFIRMATION_REQUIRED");
        }
        PersonRebindMapper.RebindCandidateRow candidate = mapper.lockFrozenCandidate(
            application.targetProfileId(), application.expectedBindingId(), application.expectedBindingVersion());
        if (candidate == null || !same(application.fields(), candidate)) {
            throw failure("PERSON_REBIND_BINDING_CHANGED");
        }
        return candidate;
    }

    void unbindBinding(long userId, Instant occurredTime) {
        PersonBindingRow binding = mapper.lockEffectiveBindingByUser(userId);
        if (binding == null) {
            throw failure("PERSON_BINDING_NOT_FOUND");
        }
        int nextVersion = intValue(binding.getBindingVersion()) + 1;
        requireChanged(mapper.unbind(binding.getPersonBindingId(), intValue(binding.getBindingVersion()),
            userId, occurredTime), "PERSON_BINDING_VERSION_CONFLICT");
        requireChanged(mapper.insertBindingEvent(bindingEvent(binding, "UNBOUND", nextVersion,
            "SELF_SERVICE", binding.getPersonBindingId(), "PERSON_SELF_UNBOUND", occurredTime)),
            "PERSON_BINDING_EVENT_CONFLICT");
    }

    Optional<PersonApplication> findOpenByUserId(long userId) {
        return Optional.ofNullable(applicationMapper.selectOpenByUserId(userId)).map(this::application);
    }

    Long findEffectiveProfileIdByUser(long userId) {
        return applicationMapper.selectEffectiveProfileIdByUser(userId);
    }

    PersonApplication lockOpenByUserId(long userId) {
        PersonApplicationRow row = applicationMapper.lockOpenByUserId(userId);
        if (row == null) {
            throw failure("PERSON_APPLICATION_NOT_FOUND");
        }
        return application(row);
    }

    Optional<PersonDocumentTypeRule> findDocumentType(String documentTypeCode) {
        PersonDocumentTypeRow row = applicationMapper.selectDocumentType(documentTypeCode);
        return Optional.ofNullable(row).map(value -> new PersonDocumentTypeRule(value.getDocumentTypeCode(),
            value.getNumberPattern(), "Y".equals(value.getValidityRequired())));
    }

    PersonSubmission insertSubmission(PersonApplication application, PersonIdentityFields fields,
                                      int submissionSeq, Instant submittedTime) {
        PersonSubmissionRow row = submissionRow(application, fields, submissionSeq, submittedTime);
        try {
            requireChanged(applicationMapper.insertSubmission(row), "PERSON_SUBMISSION_CONFLICT");
        } catch (DuplicateKeyException exception) {
            throw failure("PERSON_SUBMISSION_CONFLICT", exception);
        }
        return new PersonSubmission(row.getPersonSubmissionId(), row.getPersonApplicationId(),
            intValue(row.getSubmissionSeq()), row.getApplicantUserId(), fields(row), row.getProviderCode(),
            row.getSubmittedTime());
    }

    PersonApplication markWaiting(long applicationId, int submissionSeq, int expectedVersion,
                                  Instant submittedTime) {
        requireChanged(applicationMapper.markWaiting(applicationId, submissionSeq, expectedVersion, submittedTime),
            "PERSON_APPLICATION_VERSION_CONFLICT");
        PersonApplicationRow row = applicationMapper.lockApplicationById(applicationId);
        if (row == null) {
            throw failure("PERSON_APPLICATION_NOT_FOUND");
        }
        return application(row);
    }

    private void requireFrozenSnapshot(PersonApplicationRow application, PersonSubmissionRow submission) {
        if (!Objects.equals(application.getTargetProfileId(), submission.getTargetProfileId())
            || !Objects.equals(application.getExpectedBindingId(), submission.getExpectedBindingId())
            || !Objects.equals(application.getExpectedBindingVersion(), submission.getExpectedBindingVersion())
            || !same(application, submission)) {
            throw failure("PERSON_REBIND_SNAPSHOT_INVALID");
        }
    }

    boolean same(PersonIdentityFields fields, PersonRebindMapper.RebindCandidateRow candidate) {
        return Objects.equals(fields.fullName(), candidate.getFullName())
            && Objects.equals(fields.documentTypeCode(), candidate.getDocumentTypeCode())
            && Objects.equals(fields.documentNumber(), candidate.getDocumentNumber())
            && Objects.equals(fields.identityKey(), candidate.getIdentityKey())
            && Objects.equals(fields.gender(), candidate.getGender())
            && Objects.equals(fields.birthDate(), candidate.getBirthDate())
            && Objects.equals(fields.validFrom(), candidate.getValidFrom())
            && Objects.equals(fields.validUntil(), candidate.getValidUntil());
    }

    boolean same(PersonApplicationRow application, PersonIdentityFields fields) {
        return Objects.equals(application.getFullName(), fields.fullName())
            && Objects.equals(application.getDocumentTypeCode(), fields.documentTypeCode())
            && Objects.equals(application.getDocumentNumber(), fields.documentNumber())
            && Objects.equals(application.getIdentityKey(), fields.identityKey())
            && Objects.equals(application.getGender(), fields.gender())
            && Objects.equals(application.getBirthDate(), fields.birthDate())
            && Objects.equals(application.getValidFrom(), fields.validFrom())
            && Objects.equals(application.getValidUntil(), fields.validUntil());
    }

    private boolean same(PersonApplicationRow application, PersonSubmissionRow submission) {
        return Objects.equals(application.getFullName(), submission.getFullName())
            && Objects.equals(application.getDocumentTypeCode(), submission.getDocumentTypeCode())
            && Objects.equals(application.getDocumentNumber(), submission.getDocumentNumber())
            && Objects.equals(application.getIdentityKey(), submission.getIdentityKey())
            && Objects.equals(application.getGender(), submission.getGender())
            && Objects.equals(application.getBirthDate(), submission.getBirthDate())
            && Objects.equals(application.getValidFrom(), submission.getValidFrom())
            && Objects.equals(application.getValidUntil(), submission.getValidUntil());
    }

    private boolean same(PersonSubmissionRow submission, PersonProfileRow profile) {
        return Objects.equals(submission.getFullName(), profile.getFullName())
            && Objects.equals(submission.getDocumentTypeCode(), profile.getDocumentTypeCode())
            && Objects.equals(submission.getDocumentNumber(), profile.getDocumentNumber())
            && Objects.equals(submission.getIdentityKey(), profile.getIdentityKey())
            && Objects.equals(submission.getGender(), profile.getGender())
            && Objects.equals(submission.getBirthDate(), profile.getBirthDate())
            && Objects.equals(submission.getValidFrom(), profile.getValidFrom())
            && Objects.equals(submission.getValidUntil(), profile.getValidUntil());
    }

    private PersonVersionRow version(long profileId, int versionNo, PersonSubmissionRow submission,
                                     Instant finishedTime) {
        PersonVersionRow row = new PersonVersionRow();
        row.setPersonVersionId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId());
        row.setPersonProfileId(profileId);
        row.setVersionNo(versionNo);
        row.setSourceType("USER_SUBMISSION");
        row.setSourceId(submission.getPersonSubmissionId());
        row.setPublishedTime(finishedTime);
        copy(row, submission);
        return row;
    }

    private PersonProfileRow updatedProfile(PersonProfileRow profile, long versionId,
                                            PersonSubmissionRow submission) {
        PersonProfileRow row = new PersonProfileRow();
        row.setPersonProfileId(profile.getPersonProfileId());
        row.setCurrentVersionId(versionId);
        row.setVersion(profile.getVersion());
        copy(row, submission);
        return row;
    }

    private PersonBindingRow binding(long profileId, long userId, long sourceId, Instant boundTime) {
        PersonBindingRow row = new PersonBindingRow();
        row.setPersonBindingId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId());
        row.setPersonProfileId(profileId);
        row.setUserId(userId);
        row.setStatus("ACTIVE");
        row.setBindingVersion(1);
        row.setSourceType("USER_SUBMISSION");
        row.setSourceId(sourceId);
        row.setBoundTime(boundTime);
        return row;
    }

    private PersonBindingEventRow bindingEvent(PersonBindingRow binding, String eventType, int bindingVersion,
                                                long sourceId, String reason, Instant occurredTime) {
        return bindingEvent(binding, eventType, bindingVersion, "USER_SUBMISSION", sourceId, reason, occurredTime);
    }

    private PersonBindingEventRow bindingEvent(PersonBindingRow binding, String eventType, int bindingVersion,
                                                String sourceType, long sourceId, String reason,
                                                Instant occurredTime) {
        PersonBindingEventRow row = new PersonBindingEventRow();
        row.setPersonBindingEventId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId());
        row.setPersonBindingId(binding.getPersonBindingId());
        row.setPersonProfileId(binding.getPersonProfileId());
        row.setUserId(binding.getUserId());
        row.setEventType(eventType);
        row.setBindingVersion(bindingVersion);
        row.setSourceType(sourceType);
        row.setSourceId(sourceId);
        row.setReason(reason);
        row.setOccurredTime(occurredTime);
        return row;
    }

    private PersonSubmissionRow submissionRow(PersonApplication application, PersonIdentityFields fields,
                                              int submissionSeq, Instant submittedTime) {
        PersonSubmissionRow row = new PersonSubmissionRow();
        row.setPersonSubmissionId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId());
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

    private PersonApplication application(PersonApplicationRow row) {
        return new PersonApplication(row.getPersonApplicationId(), row.getApplicantUserId(),
            row.getTargetProfileId(), row.getStatus(), fields(row), row.getProviderCode(),
            intValue(row.getSubmissionSeq()), "Y".equals(row.getRebindIntent()), row.getExpectedBindingId(),
            row.getExpectedBindingVersion(), intValue(row.getDecisionVersion()), intValue(row.getVersion()),
            row.getSubmittedTime(), row.getFinishedTime());
    }

    private PersonIdentityFields fields(PersonApplicationRow row) {
        return new PersonIdentityFields(row.getFullName(), row.getDocumentTypeCode(), row.getDocumentNumber(),
            row.getIdentityKey(), row.getGender(), row.getBirthDate(), row.getValidFrom(), row.getValidUntil());
    }

    private PersonIdentityFields fields(PersonSubmissionRow row) {
        return new PersonIdentityFields(row.getFullName(), row.getDocumentTypeCode(), row.getDocumentNumber(),
            row.getIdentityKey(), row.getGender(), row.getBirthDate(), row.getValidFrom(), row.getValidUntil());
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

    private void requireChanged(int changed, String category) {
        if (changed != 1) {
            throw failure(category);
        }
    }

    private PersonIdentityFields safeIdentity(PersonRebindIdentityBo command) {
        try {
            PersonIdentityFields fields = normalize(command);
            validateComplete(fields);
            return fields;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private PersonIdentityFields requireIdentity(PersonRebindIdentityBo command) {
        try {
            PersonIdentityFields fields = normalize(command);
            validateComplete(fields);
            return fields;
        } catch (RuntimeException exception) {
            throw failure("PERSON_REBIND_NOT_AVAILABLE");
        }
    }

    private PersonIdentityFields normalize(PersonRebindIdentityBo command) {
        if (command == null) {
            throw failure("PERSON_REBIND_IDENTITY_REQUIRED");
        }
        return PersonIdentityFields.normalize(new PersonApplicationSaveBo(command.fullName(), command.documentTypeCode(),
            command.documentNumber(), command.gender(), command.birthDate(), command.validFrom(),
            command.validUntil(), 0));
    }

    private void validateComplete(PersonIdentityFields fields) {
        if (fields.fullName() == null || fields.documentTypeCode() == null || fields.documentNumber() == null
            || fields.gender() == null || fields.birthDate() == null || fields.fullName().length() > 100
            || fields.documentNumber().length() > 128 || !GENDERS.contains(fields.gender())) {
            throw failure("PERSON_REBIND_IDENTITY_INVALID");
        }
        PersonDocumentTypeRule rule = findDocumentType(fields.documentTypeCode())
            .orElseThrow(() -> failure("PERSON_REBIND_IDENTITY_INVALID"));
        if (!Pattern.matches(rule.numberPattern(), fields.documentNumber())) {
            throw failure("PERSON_REBIND_IDENTITY_INVALID");
        }
        if (rule.validityRequired() && (fields.validFrom() == null || fields.validUntil() == null)) {
            throw failure("PERSON_REBIND_IDENTITY_INVALID");
        }
        LocalDate today = LocalDate.now(clock);
        if (fields.birthDate().isAfter(today)
            || fields.validFrom() != null && fields.validUntil() != null
            && (fields.validFrom().isAfter(fields.validUntil()) || fields.validFrom().isAfter(today)
            || fields.validUntil().isBefore(today))) {
            throw failure("PERSON_REBIND_IDENTITY_INVALID");
        }
    }

    private void requireProviderEnabled(String providerCode) {
        try {
            providers.requireEnabled(providerCode);
        } catch (PersonVerificationException exception) {
            throw new PersonRebindException("PERSON_PROVIDER_UNAVAILABLE", exception);
        }
    }

    private void startVerificationAttempt(PersonVerificationStartAttemptCommand command) {
        try {
            attempts.startAttempt(command);
        } catch (PersonVerificationException exception) {
            throw new PersonRebindException("PERSON_PROVIDER_UNAVAILABLE", exception);
        }
    }

    private String maskedPhone(long oldUserId) {
        String phone = text(users.selectPhonenumberById(oldUserId));
        if (phone == null || phone.length() < 8) {
            return "****";
        }
        return phone.substring(0, 3) + "*".repeat(phone.length() - 7)
            + phone.substring(phone.length() - 4);
    }

    private String fingerprint(PersonIdentityFields fields, int snapshotVersion) {
        String canonical = fields.identityKey() + "\n" + snapshotVersion + "\nREBIND";
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

    private boolean same(PersonIdentityFields first, PersonIdentityFields second) {
        return first.equals(second);
    }

    private PersonRebindMatchVo unavailable() {
        return new PersonRebindMatchVo("NOT_AVAILABLE", null);
    }

    private String upper(String value) {
        String normalized = text(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String text(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    private int intValue(Integer value) {
        return value == null ? 0 : value;
    }

    private void requireUserId(long userId) {
        if (userId <= 0) {
            throw failure("PERSON_USER_INVALID");
        }
    }

    private PersonRebindException failure(String category) {
        return new PersonRebindException(category);
    }

    private PersonRebindException failure(String category, Throwable cause) {
        return new PersonRebindException(category, cause);
    }

}

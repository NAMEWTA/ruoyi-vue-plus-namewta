package org.dromara.profile.person.persistence;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.dromara.profile.person.application.DocumentTypeRule;
import org.dromara.profile.person.application.PersonActiveProjection;
import org.dromara.profile.person.application.PersonApplication;
import org.dromara.profile.person.application.PersonApplicationException;
import org.dromara.profile.person.application.PersonApplicationRepository;
import org.dromara.profile.person.application.PersonIdentityFields;
import org.dromara.profile.person.application.PersonPublication;
import org.dromara.profile.person.application.PersonSubmission;
import org.dromara.profile.person.persistence.mapper.PersonApplicationMapper;
import org.dromara.profile.person.persistence.row.PersonApplicationRow;
import org.dromara.profile.person.persistence.row.PersonBindingEventRow;
import org.dromara.profile.person.persistence.row.PersonBindingRow;
import org.dromara.profile.person.persistence.row.PersonDocumentTypeRow;
import org.dromara.profile.person.persistence.row.PersonProfileRow;
import org.dromara.profile.person.persistence.row.PersonSubmissionRow;
import org.dromara.profile.person.persistence.row.PersonVersionRow;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class MybatisPersonApplicationRepository implements PersonApplicationRepository {

    private final PersonApplicationMapper mapper;
    private final JsonMapper jsonMapper;

    public MybatisPersonApplicationRepository(PersonApplicationMapper mapper, JsonMapper jsonMapper) {
        this.mapper = mapper;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public Optional<PersonApplication> findOpenByUserId(long userId) {
        return Optional.ofNullable(mapper.selectOpenByUserId(userId)).map(this::application);
    }

    @Override
    public PersonApplication lockOpenByUserId(long userId) {
        return requireApplication(mapper.lockOpenByUserId(userId));
    }

    @Override
    public PersonApplication lockById(long applicationId) {
        return requireApplication(mapper.lockApplicationById(applicationId));
    }

    @Override
    public Optional<DocumentTypeRule> findDocumentType(String documentTypeCode) {
        PersonDocumentTypeRow row = mapper.selectDocumentType(documentTypeCode);
        return Optional.ofNullable(row).map(value -> new DocumentTypeRule(value.getDocumentTypeCode(),
            value.getNumberPattern(), "Y".equals(value.getValidityRequired())));
    }

    @Override
    public Long findActiveProfileIdByIdentity(String identityKey) {
        return identityKey == null ? null : mapper.selectActiveProfileIdByIdentity(identityKey);
    }

    @Override
    public Long findEffectiveProfileIdByUser(long userId) {
        return mapper.selectEffectiveProfileIdByUser(userId);
    }

    @Override
    public PersonApplication saveDraft(long userId, String providerCode,
                                       org.dromara.profile.person.application.PersonDraftUpdate update) {
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
        PersonApplicationRow saved = mapper.selectOpenByUserId(userId);
        return requireApplication(saved);
    }

    @Override
    public void requireSubmissionAllowed(long userId, Long targetProfileId, String identityKey) {
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

    @Override
    public PersonSubmission insertSubmission(PersonApplication application, PersonIdentityFields fields,
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

    @Override
    public PersonApplication markWaiting(long applicationId, int submissionSeq, int expectedVersion,
                                         Instant submittedTime) {
        requireChanged(mapper.markWaiting(applicationId, submissionSeq, expectedVersion, submittedTime),
            "PERSON_APPLICATION_VERSION_CONFLICT");
        return requireApplication(mapper.lockApplicationById(applicationId));
    }

    @Override
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

    @Override
    public void updateWorkflowStatus(long applicationId, int snapshotVersion, String status,
                                     int expectedVersion, Instant occurredTime) {
        requireChanged(mapper.updateWorkflowStatus(applicationId, snapshotVersion, status,
            expectedVersion, occurredTime), "PERSON_APPLICATION_DECISION_CONFLICT");
    }

    @Override
    public List<PersonActiveProjection> findActiveProjections(Set<Long> userIds) {
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
                                          org.dromara.profile.person.application.PersonDraftUpdate update) {
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

    private PersonApplicationException failure(String category) {
        return new PersonApplicationException(category);
    }

    private PersonApplicationException failure(String category, Throwable cause) {
        return new PersonApplicationException(category, cause);
    }

    private record PublicationTarget(PersonProfileRow profile, PersonBindingRow binding, boolean successor) {
    }
}

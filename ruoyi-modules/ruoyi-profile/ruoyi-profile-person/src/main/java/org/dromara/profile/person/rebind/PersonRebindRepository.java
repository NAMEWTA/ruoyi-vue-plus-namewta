package org.dromara.profile.person.rebind;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.dromara.profile.person.application.PersonApplication;
import org.dromara.profile.person.application.PersonIdentityFields;
import org.dromara.profile.person.persistence.row.PersonApplicationRow;
import org.dromara.profile.person.persistence.row.PersonBindingEventRow;
import org.dromara.profile.person.persistence.row.PersonBindingRow;
import org.dromara.profile.person.persistence.row.PersonProfileRow;
import org.dromara.profile.person.persistence.row.PersonSubmissionRow;
import org.dromara.profile.person.persistence.row.PersonVersionRow;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PersonRebindRepository {

    private final PersonRebindMapper mapper;

    public String probeStatus(String identityKey) {
        return mapper.selectProbeStatus(identityKey);
    }

    public Optional<PersonRebindMapper.RebindCandidateRow> findExactCandidate(PersonIdentityFields fields) {
        return Optional.ofNullable(mapper.selectExactCandidate(fields.fullName(), fields.documentTypeCode(),
            fields.documentNumber(), fields.identityKey(), fields.gender(), fields.birthDate(),
            fields.validFrom(), fields.validUntil()));
    }

    public PersonApplicationRow lockOpenApplication(long userId) {
        PersonApplicationRow application = mapper.lockOpenApplication(userId);
        if (application == null) {
            throw failure("PERSON_REBIND_APPLICATION_REQUIRED");
        }
        return application;
    }

    public void requireApplicantUnbound(long userId) {
        if (mapper.lockEffectiveBindingByUser(userId) != null) {
            throw failure("PERSON_REBIND_APPLICANT_ALREADY_BOUND");
        }
    }

    public int confirm(PersonApplicationRow application, PersonRebindMapper.RebindCandidateRow candidate,
                       int expectedVersion) {
        int changed = mapper.confirmIntent(application.getPersonApplicationId(), application.getApplicantUserId(),
            candidate.getPersonProfileId(), candidate.getPersonBindingId(), candidate.getBindingVersion(),
            expectedVersion);
        requireChanged(changed, "PERSON_REBIND_VERSION_CONFLICT");
        return expectedVersion + 1;
    }

    public PersonRebindMapper.RebindCandidateRow lockCandidate(
        PersonRebindMapper.RebindCandidateRow candidate, PersonIdentityFields fields) {
        PersonRebindMapper.RebindCandidateRow locked = mapper.lockFrozenCandidate(candidate.getPersonProfileId(),
            candidate.getPersonBindingId(), candidate.getBindingVersion());
        if (locked == null || !same(fields, locked)) {
            throw failure("PERSON_REBIND_NOT_AVAILABLE");
        }
        return locked;
    }

    public PersonRebindMapper.RebindCandidateRow requireFrozenCandidate(PersonApplication application) {
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

    public Optional<Publication> publish(long applicationId, int snapshotVersion, Instant finishedTime) {
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
            return Optional.of(new Publication(submission.getPersonSubmissionId(), version.getPersonVersionId(),
                new PersonReboundEvent(profile.getPersonProfileId(), applicationId, oldBinding.getUserId())));
        } catch (DuplicateKeyException exception) {
            throw failure("PERSON_REBIND_PUBLICATION_CONFLICT", exception);
        }
    }

    public void unbind(long userId, Instant occurredTime) {
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

    private void requireFrozenSnapshot(PersonApplicationRow application, PersonSubmissionRow submission) {
        if (!Objects.equals(application.getTargetProfileId(), submission.getTargetProfileId())
            || !Objects.equals(application.getExpectedBindingId(), submission.getExpectedBindingId())
            || !Objects.equals(application.getExpectedBindingVersion(), submission.getExpectedBindingVersion())
            || !same(application, submission)) {
            throw failure("PERSON_REBIND_SNAPSHOT_INVALID");
        }
    }

    public boolean same(PersonIdentityFields fields, PersonRebindMapper.RebindCandidateRow candidate) {
        return Objects.equals(fields.fullName(), candidate.getFullName())
            && Objects.equals(fields.documentTypeCode(), candidate.getDocumentTypeCode())
            && Objects.equals(fields.documentNumber(), candidate.getDocumentNumber())
            && Objects.equals(fields.identityKey(), candidate.getIdentityKey())
            && Objects.equals(fields.gender(), candidate.getGender())
            && Objects.equals(fields.birthDate(), candidate.getBirthDate())
            && Objects.equals(fields.validFrom(), candidate.getValidFrom())
            && Objects.equals(fields.validUntil(), candidate.getValidUntil());
    }

    public boolean same(PersonApplicationRow application, PersonIdentityFields fields) {
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
        row.setPersonVersionId(IdWorker.getId());
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

    private PersonBindingEventRow bindingEvent(PersonBindingRow binding, String eventType, int bindingVersion,
                                                long sourceId, String reason, Instant occurredTime) {
        return bindingEvent(binding, eventType, bindingVersion, "USER_SUBMISSION", sourceId, reason, occurredTime);
    }

    private PersonBindingEventRow bindingEvent(PersonBindingRow binding, String eventType, int bindingVersion,
                                                String sourceType, long sourceId, String reason,
                                                Instant occurredTime) {
        PersonBindingEventRow row = new PersonBindingEventRow();
        row.setPersonBindingEventId(IdWorker.getId());
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

    private int intValue(Integer value) {
        return value == null ? 0 : value;
    }

    private void requireChanged(int changed, String category) {
        if (changed != 1) {
            throw failure(category);
        }
    }

    private PersonRebindException failure(String category) {
        return new PersonRebindException(category);
    }

    private PersonRebindException failure(String category, Throwable cause) {
        return new PersonRebindException(category, cause);
    }

    public record Publication(long personSubmissionId, long personVersionId, PersonReboundEvent event) {
    }
}

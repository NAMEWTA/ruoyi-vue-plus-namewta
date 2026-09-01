package org.dromara.profile.enterprise.persistence;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.dromara.profile.enterprise.application.DocumentTypeRule;
import org.dromara.profile.enterprise.application.EnterpriseActiveProjection;
import org.dromara.profile.enterprise.application.EnterpriseApplication;
import org.dromara.profile.enterprise.application.EnterpriseApplicationException;
import org.dromara.profile.enterprise.application.EnterpriseApplicationRepository;
import org.dromara.profile.enterprise.application.EnterpriseIdentityFields;
import org.dromara.profile.enterprise.application.EnterprisePublication;
import org.dromara.profile.enterprise.application.EnterpriseSubmission;
import org.dromara.profile.enterprise.persistence.mapper.EnterpriseApplicationMapper;
import org.dromara.profile.enterprise.persistence.row.EnterpriseApplicationRow;
import org.dromara.profile.enterprise.persistence.row.EnterpriseBindingEventRow;
import org.dromara.profile.enterprise.persistence.row.EnterpriseBindingRow;
import org.dromara.profile.enterprise.persistence.row.EnterpriseDocumentTypeRow;
import org.dromara.profile.enterprise.persistence.row.EnterpriseProfileRow;
import org.dromara.profile.enterprise.persistence.row.EnterpriseSubmissionRow;
import org.dromara.profile.enterprise.persistence.row.EnterpriseVersionRow;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class MybatisEnterpriseApplicationRepository implements EnterpriseApplicationRepository {

    private final EnterpriseApplicationMapper mapper;
    private final JsonMapper jsonMapper;

    public MybatisEnterpriseApplicationRepository(EnterpriseApplicationMapper mapper, JsonMapper jsonMapper) {
        this.mapper = mapper;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public Optional<EnterpriseApplication> findOpenByUserId(long userId) {
        return Optional.ofNullable(mapper.selectOpenByUserId(userId)).map(this::application);
    }

    @Override
    public EnterpriseApplication lockOpenByUserId(long userId) {
        return requireApplication(mapper.lockOpenByUserId(userId));
    }

    @Override
    public EnterpriseApplication lockById(long applicationId) {
        return requireApplication(mapper.lockApplicationById(applicationId));
    }

    @Override
    public Optional<DocumentTypeRule> findDocumentType(String documentTypeCode) {
        EnterpriseDocumentTypeRow row = mapper.selectDocumentType(documentTypeCode);
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
    public String probeStatus(String identityKey) {
        return mapper.selectProbeStatus(identityKey);
    }

    @Override
    public EnterpriseApplication saveDraft(long userId, String providerCode,
                                       org.dromara.profile.enterprise.application.EnterpriseDraftUpdate update) {
        EnterpriseApplicationRow current = mapper.lockOpenByUserId(userId);
        try {
            if (current == null) {
                if (update.expectedVersion() != 0) {
                    throw failure("ENTERPRISE_APPLICATION_VERSION_CONFLICT");
                }
                EnterpriseApplicationRow inserted = draftRow(IdWorker.getId(), userId, providerCode, update);
                requireChanged(mapper.insertApplication(inserted), "ENTERPRISE_APPLICATION_CREATE_CONFLICT");
            } else {
                if (!editable(current.getStatus()) || intValue(current.getVersion()) != update.expectedVersion()) {
                    throw failure("ENTERPRISE_APPLICATION_VERSION_CONFLICT");
                }
                EnterpriseApplicationRow changed = draftRow(current.getEnterpriseApplicationId(), userId,
                    current.getProviderCode(), update);
                changed.setVersion(update.expectedVersion());
                requireChanged(mapper.updateDraft(changed), "ENTERPRISE_APPLICATION_VERSION_CONFLICT");
            }
        } catch (DuplicateKeyException exception) {
            throw failure("ENTERPRISE_APPLICATION_IDENTITY_CONFLICT", exception);
        }
        EnterpriseApplicationRow saved = mapper.selectOpenByUserId(userId);
        return requireApplication(saved);
    }

    @Override
    public void requireSubmissionAllowed(long userId, Long targetProfileId, String identityKey) {
        if (identityKey == null || identityKey.isBlank()) {
            throw failure("ENTERPRISE_IDENTITY_REQUIRED");
        }
        EnterpriseBindingRow userBinding = mapper.lockEffectiveBindingByUser(userId);
        if (userBinding != null) {
            throw failure("ENTERPRISE_ACCOUNT_ALREADY_RESPONSIBLE");
        }
        EnterpriseProfileRow profile = mapper.lockActiveProfileByIdentity(identityKey);
        EnterpriseBindingRow profileBinding = profile == null
            ? null : mapper.lockEffectiveBindingByProfile(profile.getEnterpriseProfileId());
        if (profileBinding != null) {
            throw failure("ENTERPRISE_RESPONSIBLE_ALREADY_BOUND");
        }
        if (profile != null && (targetProfileId == null
            || !profile.getEnterpriseProfileId().equals(targetProfileId))) {
            throw failure("ENTERPRISE_IDENTITY_CONFLICT");
        }
    }

    @Override
    public EnterpriseSubmission insertSubmission(EnterpriseApplication application, EnterpriseIdentityFields fields,
                                             int submissionSeq, Instant submittedTime) {
        EnterpriseSubmissionRow row = submissionRow(application, fields, submissionSeq, submittedTime);
        try {
            requireChanged(mapper.insertSubmission(row), "ENTERPRISE_SUBMISSION_CONFLICT");
        } catch (DuplicateKeyException exception) {
            throw failure("ENTERPRISE_SUBMISSION_CONFLICT", exception);
        }
        return submission(row);
    }

    @Override
    public EnterpriseSubmission requireSubmission(long applicationId, int submissionSeq) {
        EnterpriseSubmissionRow row = mapper.selectSubmission(applicationId, submissionSeq);
        if (row == null) {
            throw failure("ENTERPRISE_SUBMISSION_NOT_FOUND");
        }
        return submission(row);
    }

    @Override
    public EnterpriseApplication markWaiting(long applicationId, int submissionSeq, int expectedVersion,
                                         Instant submittedTime) {
        requireChanged(mapper.markWaiting(applicationId, submissionSeq, expectedVersion, submittedTime),
            "ENTERPRISE_APPLICATION_VERSION_CONFLICT");
        return requireApplication(mapper.lockApplicationById(applicationId));
    }

    @Override
    public EnterprisePublication publishApproved(long applicationId, int snapshotVersion, Instant finishedTime) {
        EnterpriseApplicationRow application = mapper.lockApplicationById(applicationId);
        if (application == null || !"WAITING".equals(application.getStatus())
            || intValue(application.getSubmissionSeq()) != snapshotVersion) {
            throw failure("ENTERPRISE_APPLICATION_DECISION_CONFLICT");
        }
        EnterpriseSubmissionRow submission = mapper.selectSubmission(applicationId, snapshotVersion);
        if (submission == null) {
            throw failure("ENTERPRISE_SUBMISSION_NOT_FOUND");
        }

        try {
            PublicationTarget target = publicationTarget(application.getApplicantUserId(), submission);
            EnterpriseVersionRow currentVersion = mapper.selectCurrentVersionForUpdate(target.profile().getEnterpriseProfileId());
            int nextVersion = currentVersion == null ? 1 : intValue(currentVersion.getVersionNo()) + 1;
            if (currentVersion != null) {
                requireChanged(mapper.supersedeVersion(currentVersion.getEnterpriseVersionId()),
                    "ENTERPRISE_PROFILE_VERSION_CONFLICT");
            }

            EnterpriseVersionRow version = versionRow(target.profile().getEnterpriseProfileId(), nextVersion,
                submission, finishedTime);
            requireChanged(mapper.insertVersion(version), "ENTERPRISE_PROFILE_VERSION_CONFLICT");
            EnterpriseProfileRow updatedProfile = profileRow(target.profile().getEnterpriseProfileId(),
                target.profile().getPreviousProfileId(), version.getEnterpriseVersionId(), submission,
                target.profile().getVersion());
            requireChanged(mapper.updateProfile(updatedProfile), "ENTERPRISE_PROFILE_VERSION_CONFLICT");

            EnterpriseBindingRow binding = bindingRow(target.profile().getEnterpriseProfileId(),
                application.getApplicantUserId(), submission.getEnterpriseSubmissionId(), finishedTime);
            requireChanged(mapper.insertBinding(binding), "ENTERPRISE_BINDING_CONFLICT");
            requireChanged(mapper.insertBindingEvent(bindingEvent(binding, submission.getEnterpriseSubmissionId(),
                finishedTime)), "ENTERPRISE_BINDING_EVENT_CONFLICT");

            requireChanged(mapper.finishApplication(applicationId, snapshotVersion,
                intValue(application.getDecisionVersion()), intValue(application.getVersion()), finishedTime),
                "ENTERPRISE_APPLICATION_DECISION_CONFLICT");
            return new EnterprisePublication(target.profile().getEnterpriseProfileId(), version.getEnterpriseVersionId(),
                binding.getEnterpriseBindingId(), target.successor());
        } catch (DuplicateKeyException exception) {
            throw failure("ENTERPRISE_PUBLICATION_CONFLICT", exception);
        }
    }

    @Override
    public void updateWorkflowStatus(long applicationId, int snapshotVersion, String status,
                                     int expectedVersion, Instant occurredTime) {
        requireChanged(mapper.updateWorkflowStatus(applicationId, snapshotVersion, status,
            expectedVersion, occurredTime), "ENTERPRISE_APPLICATION_DECISION_CONFLICT");
    }

    @Override
    public List<EnterpriseActiveProjection> findActiveProjections(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectActiveProjections(userIds).stream().map(row -> new EnterpriseActiveProjection(
            row.getUserId(), row.getEnterpriseProfileId(), row.getVerifiedAt())).toList();
    }

    private PublicationTarget publicationTarget(long userId, EnterpriseSubmissionRow submission) {
        EnterpriseBindingRow userBinding = mapper.lockEffectiveBindingByUser(userId);
        if (userBinding != null) {
            throw failure("ENTERPRISE_ACCOUNT_ALREADY_RESPONSIBLE");
        }
        EnterpriseProfileRow profile = mapper.lockActiveProfileByIdentity(submission.getIdentityKey());
        EnterpriseBindingRow profileBinding = profile == null
            ? null : mapper.lockEffectiveBindingByProfile(profile.getEnterpriseProfileId());
        if (profileBinding != null) {
            throw failure("ENTERPRISE_RESPONSIBLE_ALREADY_BOUND");
        }
        if (profile != null) {
            return new PublicationTarget(profile, false);
        }

        EnterpriseProfileRow revoked = mapper.lockLatestRevokedProfileByIdentity(submission.getIdentityKey());
        long profileId = IdWorker.getId();
        EnterpriseProfileRow created = profileRow(profileId,
            revoked == null ? null : revoked.getEnterpriseProfileId(), null, submission, 0);
        requireChanged(mapper.insertProfile(created), "ENTERPRISE_IDENTITY_CONFLICT");
        return new PublicationTarget(created, revoked != null);
    }

    private EnterpriseApplicationRow draftRow(long applicationId, long userId, String providerCode,
                                          org.dromara.profile.enterprise.application.EnterpriseDraftUpdate update) {
        EnterpriseApplicationRow row = new EnterpriseApplicationRow();
        row.setEnterpriseApplicationId(applicationId);
        row.setApplicantUserId(userId);
        row.setTargetProfileId(update.targetProfileId());
        row.setProviderCode(providerCode);
        copy(row, update.fields());
        return row;
    }

    private EnterpriseSubmissionRow submissionRow(EnterpriseApplication application, EnterpriseIdentityFields fields,
                                              int submissionSeq, Instant submittedTime) {
        EnterpriseSubmissionRow row = new EnterpriseSubmissionRow();
        row.setEnterpriseSubmissionId(IdWorker.getId());
        row.setEnterpriseApplicationId(application.enterpriseApplicationId());
        row.setSubmissionSeq(submissionSeq);
        row.setApplicantUserId(application.applicantUserId());
        row.setProviderCode(application.providerCode());
        row.setTargetProfileId(application.targetProfileId());
        row.setFieldSnapshotJson(jsonMapper.writeValueAsString(fields));
        row.setSubmittedTime(submittedTime);
        copy(row, fields);
        return row;
    }

    private EnterpriseProfileRow profileRow(long profileId, Long previousProfileId, Long currentVersionId,
                                        EnterpriseSubmissionRow submission, Integer version) {
        EnterpriseProfileRow row = new EnterpriseProfileRow();
        row.setEnterpriseProfileId(profileId);
        row.setPreviousProfileId(previousProfileId);
        row.setCurrentVersionId(currentVersionId);
        row.setVersion(version);
        row.setStatus("ACTIVE");
        copy(row, submission);
        return row;
    }

    private EnterpriseVersionRow versionRow(long profileId, int versionNo, EnterpriseSubmissionRow submission,
                                        Instant publishedTime) {
        EnterpriseVersionRow row = new EnterpriseVersionRow();
        row.setEnterpriseVersionId(IdWorker.getId());
        row.setEnterpriseProfileId(profileId);
        row.setVersionNo(versionNo);
        row.setSourceType("USER_SUBMISSION");
        row.setSourceId(submission.getEnterpriseSubmissionId());
        row.setStatus("CURRENT");
        row.setPublishedTime(publishedTime);
        copy(row, submission);
        return row;
    }

    private EnterpriseBindingRow bindingRow(long profileId, long userId, long sourceId, Instant boundTime) {
        EnterpriseBindingRow row = new EnterpriseBindingRow();
        row.setEnterpriseBindingId(IdWorker.getId());
        row.setEnterpriseProfileId(profileId);
        row.setUserId(userId);
        row.setStatus("ACTIVE");
        row.setBindingVersion(1);
        row.setSourceType("USER_SUBMISSION");
        row.setSourceId(sourceId);
        row.setBoundTime(boundTime);
        return row;
    }

    private EnterpriseBindingEventRow bindingEvent(EnterpriseBindingRow binding, long sourceId, Instant occurredTime) {
        EnterpriseBindingEventRow row = new EnterpriseBindingEventRow();
        row.setEnterpriseBindingEventId(IdWorker.getId());
        row.setEnterpriseBindingId(binding.getEnterpriseBindingId());
        row.setEnterpriseProfileId(binding.getEnterpriseProfileId());
        row.setUserId(binding.getUserId());
        row.setEventType("ACTIVE");
        row.setBindingVersion(binding.getBindingVersion());
        row.setSourceType("USER_SUBMISSION");
        row.setSourceId(sourceId);
        row.setReason("ENTERPRISE_VERIFICATION_APPROVED");
        row.setOccurredTime(occurredTime);
        return row;
    }

    private EnterpriseApplication application(EnterpriseApplicationRow row) {
        return new EnterpriseApplication(row.getEnterpriseApplicationId(), row.getApplicantUserId(),
            row.getTargetProfileId(), row.getStatus(), fields(row), row.getProviderCode(),
            intValue(row.getSubmissionSeq()), intValue(row.getDecisionVersion()), intValue(row.getVersion()),
            row.getSubmittedTime(), row.getFinishedTime());
    }

    private EnterpriseSubmission submission(EnterpriseSubmissionRow row) {
        return new EnterpriseSubmission(row.getEnterpriseSubmissionId(), row.getEnterpriseApplicationId(),
            intValue(row.getSubmissionSeq()), row.getApplicantUserId(), fields(row), row.getProviderCode(),
            row.getSubmittedTime());
    }

    private EnterpriseIdentityFields fields(EnterpriseApplicationRow row) {
        return new EnterpriseIdentityFields(row.getEnterpriseName(), row.getUnifiedCreditCode(), row.getIdentityKey(),
            row.getEnterpriseType(), row.getLegalRepresentativeName(), row.getLegalDocumentTypeCode(),
            row.getLegalDocumentNumber(), "Y".equals(row.getHandlerIsLegalRepresentative()),
            row.getEstablishedDate(), row.getBusinessTermFrom(), row.getBusinessTermUntil(),
            row.getRegisteredAddress(), row.getBusinessScope(), row.getContactName(), row.getContactPhone(),
            row.getEmail(), row.getRegisteredCapital(), row.getIndustryCode(), row.getWebsite());
    }

    private EnterpriseIdentityFields fields(EnterpriseSubmissionRow row) {
        return new EnterpriseIdentityFields(row.getEnterpriseName(), row.getUnifiedCreditCode(), row.getIdentityKey(),
            row.getEnterpriseType(), row.getLegalRepresentativeName(), row.getLegalDocumentTypeCode(),
            row.getLegalDocumentNumber(), "Y".equals(row.getHandlerIsLegalRepresentative()),
            row.getEstablishedDate(), row.getBusinessTermFrom(), row.getBusinessTermUntil(),
            row.getRegisteredAddress(), row.getBusinessScope(), row.getContactName(), row.getContactPhone(),
            row.getEmail(), row.getRegisteredCapital(), row.getIndustryCode(), row.getWebsite());
    }

    private void copy(EnterpriseApplicationRow target, EnterpriseIdentityFields fields) {
        target.setEnterpriseName(fields.enterpriseName());
        target.setUnifiedCreditCode(fields.unifiedCreditCode());
        target.setIdentityKey(fields.identityKey());
        target.setEnterpriseType(fields.enterpriseType());
        target.setLegalRepresentativeName(fields.legalRepresentativeName());
        target.setLegalDocumentTypeCode(fields.legalDocumentTypeCode());
        target.setLegalDocumentNumber(fields.legalDocumentNumber());
        target.setHandlerIsLegalRepresentative(fields.handlerIsLegalRepresentative() ? "Y" : "N");
        copyOptional(target, fields);
    }

    private void copy(EnterpriseSubmissionRow target, EnterpriseIdentityFields fields) {
        target.setEnterpriseName(fields.enterpriseName());
        target.setUnifiedCreditCode(fields.unifiedCreditCode());
        target.setIdentityKey(fields.identityKey());
        target.setEnterpriseType(fields.enterpriseType());
        target.setLegalRepresentativeName(fields.legalRepresentativeName());
        target.setLegalDocumentTypeCode(fields.legalDocumentTypeCode());
        target.setLegalDocumentNumber(fields.legalDocumentNumber());
        target.setHandlerIsLegalRepresentative(fields.handlerIsLegalRepresentative() ? "Y" : "N");
        copyOptional(target, fields);
    }

    private void copy(EnterpriseProfileRow target, EnterpriseSubmissionRow source) {
        target.setEnterpriseName(source.getEnterpriseName());
        target.setUnifiedCreditCode(source.getUnifiedCreditCode());
        target.setEnterpriseType(source.getEnterpriseType());
        target.setLegalRepresentativeName(source.getLegalRepresentativeName());
        target.setLegalDocumentTypeCode(source.getLegalDocumentTypeCode());
        target.setLegalDocumentNumber(source.getLegalDocumentNumber());
        copyOptional(target, source);
    }

    private void copy(EnterpriseVersionRow target, EnterpriseSubmissionRow source) {
        target.setEnterpriseName(source.getEnterpriseName());
        target.setUnifiedCreditCode(source.getUnifiedCreditCode());
        target.setEnterpriseType(source.getEnterpriseType());
        target.setLegalRepresentativeName(source.getLegalRepresentativeName());
        target.setLegalDocumentTypeCode(source.getLegalDocumentTypeCode());
        target.setLegalDocumentNumber(source.getLegalDocumentNumber());
        copyOptional(target, source);
    }

    private void copyOptional(EnterpriseApplicationRow target, EnterpriseIdentityFields fields) {
        target.setEstablishedDate(fields.establishedDate());
        target.setBusinessTermFrom(fields.businessTermFrom());
        target.setBusinessTermUntil(fields.businessTermUntil());
        target.setRegisteredAddress(fields.registeredAddress());
        target.setBusinessScope(fields.businessScope());
        target.setContactName(fields.contactName());
        target.setContactPhone(fields.contactPhone());
        target.setEmail(fields.email());
        target.setRegisteredCapital(fields.registeredCapital());
        target.setIndustryCode(fields.industryCode());
        target.setWebsite(fields.website());
    }

    private void copyOptional(EnterpriseSubmissionRow target, EnterpriseIdentityFields fields) {
        target.setEstablishedDate(fields.establishedDate());
        target.setBusinessTermFrom(fields.businessTermFrom());
        target.setBusinessTermUntil(fields.businessTermUntil());
        target.setRegisteredAddress(fields.registeredAddress());
        target.setBusinessScope(fields.businessScope());
        target.setContactName(fields.contactName());
        target.setContactPhone(fields.contactPhone());
        target.setEmail(fields.email());
        target.setRegisteredCapital(fields.registeredCapital());
        target.setIndustryCode(fields.industryCode());
        target.setWebsite(fields.website());
    }

    private void copyOptional(EnterpriseProfileRow target, EnterpriseSubmissionRow source) {
        target.setEstablishedDate(source.getEstablishedDate());
        target.setBusinessTermFrom(source.getBusinessTermFrom());
        target.setBusinessTermUntil(source.getBusinessTermUntil());
        target.setRegisteredAddress(source.getRegisteredAddress());
        target.setBusinessScope(source.getBusinessScope());
        target.setContactName(source.getContactName());
        target.setContactPhone(source.getContactPhone());
        target.setEmail(source.getEmail());
        target.setRegisteredCapital(source.getRegisteredCapital());
        target.setIndustryCode(source.getIndustryCode());
        target.setWebsite(source.getWebsite());
    }

    private void copyOptional(EnterpriseVersionRow target, EnterpriseSubmissionRow source) {
        target.setEstablishedDate(source.getEstablishedDate());
        target.setBusinessTermFrom(source.getBusinessTermFrom());
        target.setBusinessTermUntil(source.getBusinessTermUntil());
        target.setRegisteredAddress(source.getRegisteredAddress());
        target.setBusinessScope(source.getBusinessScope());
        target.setContactName(source.getContactName());
        target.setContactPhone(source.getContactPhone());
        target.setEmail(source.getEmail());
        target.setRegisteredCapital(source.getRegisteredCapital());
        target.setIndustryCode(source.getIndustryCode());
        target.setWebsite(source.getWebsite());
    }

    private EnterpriseApplication requireApplication(EnterpriseApplicationRow row) {
        if (row == null) {
            throw failure("ENTERPRISE_APPLICATION_NOT_FOUND");
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

    private EnterpriseApplicationException failure(String category) {
        return new EnterpriseApplicationException(category);
    }

    private EnterpriseApplicationException failure(String category, Throwable cause) {
        return new EnterpriseApplicationException(category, cause);
    }

    private record PublicationTarget(EnterpriseProfileRow profile, boolean successor) {
    }
}

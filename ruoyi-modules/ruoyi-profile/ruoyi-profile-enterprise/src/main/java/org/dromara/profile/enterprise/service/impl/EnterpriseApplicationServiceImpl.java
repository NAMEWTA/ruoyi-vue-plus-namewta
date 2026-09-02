package org.dromara.profile.enterprise.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.dromara.profile.enterprise.domain.application.EnterpriseDocumentTypeRule;
import org.dromara.profile.enterprise.domain.application.EnterpriseApplication;
import org.dromara.profile.enterprise.domain.vo.EnterpriseApplicationVo;
import org.dromara.profile.enterprise.domain.bo.EnterpriseApplicationSaveBo;
import org.dromara.profile.enterprise.domain.application.EnterpriseDraftUpdate;
import org.dromara.profile.enterprise.domain.application.EnterpriseIdentityFields;
import org.dromara.profile.enterprise.domain.bo.EnterpriseApplicationProbeBo;
import org.dromara.profile.enterprise.domain.vo.EnterpriseApplicationProbeVo;
import org.dromara.profile.enterprise.domain.application.EnterprisePublication;
import org.dromara.profile.enterprise.domain.application.EnterpriseSubmission;
import org.dromara.profile.enterprise.domain.exception.EnterpriseApplicationException;
import org.dromara.profile.enterprise.service.IEnterpriseApplicationService;
import org.dromara.profile.enterprise.service.EnterpriseWorkflowGateway;
import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import org.dromara.profile.api.domain.ProfileType;
import org.dromara.profile.api.material.ProfileMaterialPort;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerKey;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerType;
import org.dromara.profile.enterprise.domain.exception.EnterpriseVerificationException;
import org.dromara.profile.enterprise.domain.verification.EnterpriseVerificationStartAttemptCommand;
import org.dromara.profile.enterprise.domain.vo.EnterpriseApplicationRow;
import org.dromara.profile.enterprise.domain.vo.EnterpriseBindingEventRow;
import org.dromara.profile.enterprise.domain.vo.EnterpriseBindingRow;
import org.dromara.profile.enterprise.domain.vo.EnterpriseDocumentTypeRow;
import org.dromara.profile.enterprise.domain.vo.EnterpriseProfileRow;
import org.dromara.profile.enterprise.domain.vo.EnterpriseSubmissionRow;
import org.dromara.profile.enterprise.domain.vo.EnterpriseVersionRow;
import org.dromara.profile.enterprise.mapper.EnterpriseApplicationMapper;
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
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class EnterpriseApplicationServiceImpl implements IEnterpriseApplicationService {

    private static final String DEFAULT_PROVIDER_KEY = "profile.enterprise.provider.default";
    private static final String FLOW_CODE_KEY = "profile.enterprise.flowCode";
    private static final Set<String> EDITABLE_STATUSES = Set.of("DRAFT", "BACK", "CANCEL");
    private static final Set<String> EVENT_STATUSES = Set.of("BACK", "CANCEL", "INVALID", "TERMINATION");
    private static final Pattern CREDIT_CODE = Pattern.compile("[0-9A-Z-]{8,64}");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final EnterpriseApplicationMapper mapper;
    private final JsonMapper jsonMapper;
    private final ProfileMaterialPort materials;
    private final EnterpriseVerificationProviderRegistry providers;
    private final EnterpriseVerificationAttemptCoordinator attempts;
    private final EnterpriseWorkflowGateway workflow;
    private final ConfigService configService;
    private final Clock clock;

    @Autowired
    public EnterpriseApplicationServiceImpl(EnterpriseApplicationMapper mapper, JsonMapper jsonMapper,
                                    ProfileMaterialPort materials,
                                    EnterpriseVerificationProviderRegistry providers,
                                    EnterpriseVerificationAttemptCoordinator attempts,
                                    EnterpriseWorkflowGateway workflow, ConfigService configService) {
        this(mapper, jsonMapper, materials, providers, attempts, workflow, configService, Clock.systemUTC());
    }

    EnterpriseApplicationServiceImpl(EnterpriseApplicationMapper mapper, JsonMapper jsonMapper,
                             ProfileMaterialPort materials,
                             EnterpriseVerificationProviderRegistry providers,
                             EnterpriseVerificationAttemptCoordinator attempts,
                             EnterpriseWorkflowGateway workflow, ConfigService configService, Clock clock) {
        this.mapper = mapper;
        this.jsonMapper = jsonMapper;
        this.materials = materials;
        this.providers = providers;
        this.attempts = attempts;
        this.workflow = workflow;
        this.configService = configService;
        this.clock = clock;
    }

    @Override
    public Optional<EnterpriseApplicationVo> current(long userId) {
        requireUserId(userId);
        return findOpenByUserId(userId).map(EnterpriseApplicationVo::from);
    }

    @Override
    public EnterpriseApplicationProbeVo probe(EnterpriseApplicationProbeBo command) {
        if (command == null) {
            throw failure("ENTERPRISE_PROBE_REQUIRED");
        }
        String identityKey = normalizeCredit(command.unifiedCreditCode());
        if (identityKey == null || !CREDIT_CODE.matcher(identityKey).matches()) {
            throw failure("ENTERPRISE_CREDIT_CODE_INVALID");
        }
        return new EnterpriseApplicationProbeVo(probeStatus(identityKey));
    }

    @DSTransactional
    @Override
    public EnterpriseApplicationVo save(long userId, EnterpriseApplicationSaveBo command) {
        requireUserId(userId);
        EnterpriseIdentityFields fields = EnterpriseIdentityFields.normalize(command);
        validateDraft(fields);
        Optional<EnterpriseApplication> current = findOpenByUserId(userId);
        if (current.isPresent() && !current.get().editable()) {
            throw failure("ENTERPRISE_APPLICATION_READ_ONLY");
        }
        String providerCode = current.map(EnterpriseApplication::providerCode).orElseGet(this::defaultProvider);
        requireProviderEnabled(providerCode);
        Long accountProfileId = findEffectiveProfileIdByUser(userId);
        accountProfileId = positiveId(accountProfileId);
        Long identityProfileId = fields.identityKey() == null
            ? null : findActiveProfileIdByIdentity(fields.identityKey());
        identityProfileId = positiveId(identityProfileId);
        Long targetProfileId = accountProfileId == null ? identityProfileId : accountProfileId;
        EnterpriseApplication saved = saveDraft(userId, providerCode,
            new EnterpriseDraftUpdate(fields, targetProfileId, command.expectedVersion()));
        return EnterpriseApplicationVo.from(saved);
    }

    @DSTransactional
    @Override
    public EnterpriseApplicationVo submit(long userId, int expectedVersion) {
        requireUserId(userId);
        EnterpriseApplication application = lockOpenByUserId(userId);
        if (application.applicantUserId() != userId || !EDITABLE_STATUSES.contains(application.status())) {
            throw failure("ENTERPRISE_APPLICATION_NOT_EDITABLE");
        }
        if (application.version() != expectedVersion) {
            throw failure("ENTERPRISE_APPLICATION_VERSION_CONFLICT");
        }
        validateComplete(application.fields());
        requireProviderEnabled(application.providerCode());
        requireSubmissionAllowed(userId, application.targetProfileId(),
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
        EnterpriseSubmission submission = insertSubmission(
            application, application.fields(), snapshotVersion, submittedTime);
        materials.snapshotImmutable(working, owner(MaterialOwnerType.SUBMISSION,
            submission.enterpriseSubmissionId()));
        EnterpriseApplication waiting = markWaiting(application.enterpriseApplicationId(), snapshotVersion,
            expectedVersion, submittedTime);
        startVerificationAttempt(new EnterpriseVerificationStartAttemptCommand(application.enterpriseApplicationId(),
            submission.enterpriseSubmissionId(), fingerprint(application.fields(), snapshotVersion)));
        workflow.start(application.enterpriseApplicationId(), submission.enterpriseSubmissionId(), snapshotVersion);
        return EnterpriseApplicationVo.from(waiting);
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
        EnterpriseApplication application = lockById(applicationId);
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
            EnterpriseSubmission submission = requireSubmission(applicationId, snapshotVersion);
            EnterprisePublication publication = publishApproved(applicationId, snapshotVersion, clock.instant());
            materials.snapshotImmutable(owner(MaterialOwnerType.SUBMISSION, submission.enterpriseSubmissionId()),
                owner(MaterialOwnerType.VERSION, publication.enterpriseVersionId()));
        } else if (EVENT_STATUSES.contains(status)) {
            updateWorkflowStatus(applicationId, snapshotVersion, status,
                application.version(), clock.instant());
        }
    }

    Optional<EnterpriseApplication> findOpenByUserId(long userId) {
        return Optional.ofNullable(mapper.selectOpenByUserId(userId)).map(this::application);
    }

    EnterpriseApplication lockOpenByUserId(long userId) {
        return requireApplication(mapper.lockOpenByUserId(userId));
    }

    EnterpriseApplication lockById(long applicationId) {
        return requireApplication(mapper.lockApplicationById(applicationId));
    }

    @Override
    public Optional<EnterpriseDocumentTypeRule> findDocumentType(String documentTypeCode) {
        EnterpriseDocumentTypeRow row = mapper.selectDocumentType(documentTypeCode);
        return Optional.ofNullable(row).map(value -> new EnterpriseDocumentTypeRule(value.getDocumentTypeCode(),
            value.getNumberPattern(), "Y".equals(value.getValidityRequired())));
    }

    Long findActiveProfileIdByIdentity(String identityKey) {
        return identityKey == null ? null : mapper.selectActiveProfileIdByIdentity(identityKey);
    }

    Long findEffectiveProfileIdByUser(long userId) {
        return mapper.selectEffectiveProfileIdByUser(userId);
    }

    String probeStatus(String identityKey) {
        return mapper.selectProbeStatus(identityKey);
    }

    EnterpriseApplication saveDraft(long userId, String providerCode, EnterpriseDraftUpdate update) {
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
        return requireApplication(mapper.selectOpenByUserId(userId));
    }

    void requireSubmissionAllowed(long userId, Long targetProfileId, String identityKey) {
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

    EnterpriseSubmission insertSubmission(EnterpriseApplication application, EnterpriseIdentityFields fields,
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

    EnterpriseApplication markWaiting(long applicationId, int submissionSeq, int expectedVersion,
                                      Instant submittedTime) {
        requireChanged(mapper.markWaiting(applicationId, submissionSeq, expectedVersion, submittedTime),
            "ENTERPRISE_APPLICATION_VERSION_CONFLICT");
        return requireApplication(mapper.lockApplicationById(applicationId));
    }

    @Override
    @DSTransactional
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
            EnterpriseVersionRow currentVersion = mapper.selectCurrentVersionForUpdate(
                target.profile().getEnterpriseProfileId());
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
            return new EnterprisePublication(target.profile().getEnterpriseProfileId(),
                version.getEnterpriseVersionId(), binding.getEnterpriseBindingId(), target.successor());
        } catch (DuplicateKeyException exception) {
            throw failure("ENTERPRISE_PUBLICATION_CONFLICT", exception);
        }
    }

    void updateWorkflowStatus(long applicationId, int snapshotVersion, String status,
                              int expectedVersion, Instant occurredTime) {
        requireChanged(mapper.updateWorkflowStatus(applicationId, snapshotVersion, status,
            expectedVersion, occurredTime), "ENTERPRISE_APPLICATION_DECISION_CONFLICT");
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
                                              EnterpriseDraftUpdate update) {
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

    private EnterpriseApplicationException failure(String category, Throwable cause) {
        return new EnterpriseApplicationException(category, cause);
    }

    private record PublicationTarget(EnterpriseProfileRow profile, boolean successor) {
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
            EnterpriseDocumentTypeRule rule = documentRule(fields.legalDocumentTypeCode());
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
        EnterpriseDocumentTypeRule rule = documentRule(fields.legalDocumentTypeCode());
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

    private EnterpriseDocumentTypeRule documentRule(String documentTypeCode) {
        return findDocumentType(documentTypeCode)
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

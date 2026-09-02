package org.dromara.profile.enterprise.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.dromara.profile.enterprise.service.IEnterpriseAdminService;
import org.dromara.profile.enterprise.service.IEnterpriseApplicationService;

import org.dromara.profile.enterprise.domain.exception.EnterpriseAdminException;
import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import org.dromara.common.core.domain.PageResult;
import org.dromara.profile.api.domain.ProfileType;
import org.dromara.profile.api.ProfileService;
import org.dromara.profile.api.material.ProfileMaterialPort;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialAttachCommand;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerKey;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerType;
import org.dromara.profile.enterprise.domain.bo.*;
import org.dromara.profile.enterprise.domain.vo.*;
import org.dromara.profile.enterprise.domain.vo.EnterpriseAdminRows.*;
import org.dromara.profile.enterprise.domain.bo.EnterpriseApplicationSaveBo;
import org.dromara.profile.enterprise.domain.application.EnterpriseIdentityFields;
import org.dromara.profile.enterprise.domain.application.EnterprisePublication;
import org.dromara.profile.enterprise.domain.application.EnterpriseSubmission;
import org.dromara.profile.enterprise.mapper.EnterpriseAdminMapper;
import org.dromara.system.api.UserService;
import org.dromara.system.api.domain.UserDTO;
import org.dromara.workflow.api.WorkflowService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class EnterpriseAdminServiceImpl implements IEnterpriseAdminService {

    private static final Set<String> DECISIONS = Set.of("APPROVED", "REJECTED");
    private static final Set<String> BINDING_ACTIONS = Set.of("SUSPEND", "RESUME", "UNBIND");

    private final EnterpriseAdminMapper mapper;
    private final JsonMapper jsonMapper;
    private final IEnterpriseApplicationService applications;
    private final ProfileMaterialPort materials;
    private final WorkflowService workflow;
    private final UserService users;
    private final ProfileService profiles;
    private final Clock clock;

    @Autowired
    public EnterpriseAdminServiceImpl(EnterpriseAdminMapper mapper, JsonMapper jsonMapper,
                                  IEnterpriseApplicationService applications,
                                  ProfileMaterialPort materials,
                                  ObjectProvider<WorkflowService> workflowProvider, UserService users,
                                  ProfileService profiles) {
        this(mapper, jsonMapper, applications, materials, workflowProvider.getIfAvailable(), users, profiles,
            Clock.systemUTC());
    }

    EnterpriseAdminServiceImpl(EnterpriseAdminMapper mapper, JsonMapper jsonMapper,
                           IEnterpriseApplicationService applications,
                           ProfileMaterialPort materials, WorkflowService workflow, UserService users,
                           ProfileService profiles) {
        this(mapper, jsonMapper, applications, materials, workflow, users, profiles, Clock.systemUTC());
    }

    EnterpriseAdminServiceImpl(EnterpriseAdminMapper mapper, JsonMapper jsonMapper,
                       IEnterpriseApplicationService applications,
                       ProfileMaterialPort materials, WorkflowService workflow, UserService users,
                       ProfileService profiles, Clock clock) {
        this.mapper = mapper;
        this.jsonMapper = jsonMapper;
        this.applications = applications;
        this.materials = materials;
        this.workflow = workflow;
        this.users = users;
        this.profiles = profiles;
        this.clock = clock;
    }

    @Override
    public PageResult<EnterpriseProfileSummaryVo> page(EnterpriseAdminQueryBo query) {
        return pageData(query == null ? new EnterpriseAdminQueryBo(null, null, null, 1, 20) : query);
    }

    @Override
    public List<EnterpriseAccountCandidateVo> eligibleUsers(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        List<UserDTO> candidates = users.searchActiveUsers(keyword.strip(), 50).stream()
            .filter(user -> user.getUserId() != null && "0".equals(user.getStatus()))
            .toList();
        Map<Long, org.dromara.profile.api.domain.ProfileSummary> summaries = profiles.findByUserIds(
            candidates.stream().map(UserDTO::getUserId).toList());
        return candidates.stream()
            .filter(user -> {
                var summary = summaries.get(user.getUserId());
                return summary != null && summary.person() != null;
            })
            .filter(user -> !hasEffectiveBinding(user.getUserId()))
            .limit(20)
            .map(user -> new EnterpriseAccountCandidateVo(user.getUserId(), user.getUserName(), user.getNickName()))
            .toList();
    }

    @Override
    public EnterpriseProfileDetailVo detail(long profileId) {
        EnterpriseProfileDetailVo detail = detailData(positive(profileId, "ENTERPRISE_PROFILE_INVALID"));
        List<ProfileMaterialPort.MaterialReferenceView> current = detail.versions().isEmpty()
            ? List.of() : materials.list(owner(MaterialOwnerType.VERSION,
            detail.versions().stream().filter(version -> "CURRENT".equals(version.status())).findFirst()
                .orElse(detail.versions().getFirst()).versionId()));
        return new EnterpriseProfileDetailVo(detail.profile(), detail.versions(), detail.bindings(), detail.sources(), detail.audits(), current);
    }

    @Override
    public EnterpriseReviewContextVo review(long applicationId) {
        var data = reviewData(positive(applicationId, "ENTERPRISE_APPLICATION_INVALID"));
        var owner = owner(MaterialOwnerType.SUBMISSION, data.submissionId());
        return new EnterpriseReviewContextVo(data.applicationId(), data.applicantUserId(), data.status(), data.submissionSeq(),
            data.decisionVersion(), data.version(), data.submissionId(), data.fieldSnapshotJson(),
            data.submittedTime(), materials.list(owner));
    }

    @Override
    public org.dromara.system.api.OssService.OssAccessUrl reviewMaterial(long applicationId, long materialRefId) {
        return materials.accessUrl(owner(MaterialOwnerType.SUBMISSION, review(applicationId).submissionId()), materialRefId);
    }

    @Override
    public org.dromara.system.api.OssService.OssAccessUrl material(long profileId, long materialRefId) {
        EnterpriseProfileDetailVo detail = detail(profileId);
        EnterpriseProfileVersionVo current = detail.versions().stream().filter(version -> "CURRENT".equals(version.status()))
            .findFirst().orElseThrow(() -> failure("ENTERPRISE_PROFILE_VERSION_NOT_FOUND"));
        return materials.accessUrl(owner(MaterialOwnerType.VERSION, current.versionId()), materialRefId);
    }

    @DSTransactional
    @Override
    public EnterpriseAdminResultVo decide(long operatorId, long applicationId, EnterpriseAdminDecisionBo command) {
        String reason = reason(command == null ? null : command.reason());
        String decision = upper(command == null ? null : command.decision());
        if (!DECISIONS.contains(decision)) {
            throw failure("ENTERPRISE_ADMIN_DECISION_INVALID");
        }
        if (workflow == null) {
            throw failure("ENTERPRISE_ADMIN_WORKFLOW_UNAVAILABLE");
        }
        Instant now = clock.instant();
        var state = beginDecision(positive(applicationId, "ENTERPRISE_APPLICATION_INVALID"), decision,
            positive(operatorId, "ENTERPRISE_OPERATOR_INVALID"), reason, now);
        try {
            workflow.terminateInstance(Long.toString(applicationId), reason);
        } catch (RuntimeException exception) {
            throw new EnterpriseAdminException("ENTERPRISE_ADMIN_WORKFLOW_TERMINATION_FAILED", exception);
        }
        if ("REJECTED".equals(decision)) {
            finalizeRejected(state, operatorId, reason, now);
            return new EnterpriseAdminResultVo("REJECTED", 0L, null, null, state.decisionVersion());
        }
        resumeForApproval(state, operatorId);
        EnterpriseSubmission submission = applications.requireSubmission(applicationId, state.snapshotVersion());
        EnterprisePublication publication = applications.publishApproved(applicationId, state.snapshotVersion(), now);
        materials.snapshotImmutable(owner(MaterialOwnerType.SUBMISSION, submission.enterpriseSubmissionId()),
            owner(MaterialOwnerType.VERSION, publication.enterpriseVersionId()));
        finalizeApproved(state, publication.enterpriseProfileId(), publication.enterpriseVersionId(), operatorId,
            reason, now);
        return new EnterpriseAdminResultVo("APPROVED", publication.enterpriseProfileId(), publication.enterpriseVersionId(),
            publication.enterpriseBindingId(), state.decisionVersion());
    }

    @DSTransactional
    @Override
    public EnterpriseAdminResultVo create(long operatorId, EnterpriseAdminCreateBo command) {
        if (command == null) {
            throw failure("ENTERPRISE_ADMIN_CREATE_REQUIRED");
        }
        String reason = reason(command.reason());
        EnterpriseIdentityFields fields = fields(command.identity());
        Instant now = clock.instant();
        var state = beginCreate(fields, positive(operatorId, "ENTERPRISE_OPERATOR_INVALID"), reason, now);
        List<EnterpriseAdminMaterialBo> inputs = command.materials() == null ? List.of() : List.copyOf(command.materials());
        for (EnterpriseAdminMaterialBo input : inputs) {
            materials.attach(new MaterialAttachCommand(owner(MaterialOwnerType.SOURCE, state.sourceId()),
                input.ossId(), input.materialNodeId()));
        }
        MaterialOwnerKey source = owner(MaterialOwnerType.SOURCE, state.sourceId());
        materials.validateRequired(source, fields.legalDocumentTypeCode(), Set.of("ALWAYS"));
        if (command.bindUserId() != null) {
            requireEligibleUser(command.bindUserId());
        }
        EnterpriseAdminResultVo result = completeCreate(state, command.bindUserId(), operatorId, reason, now);
        materials.snapshotImmutable(source, owner(MaterialOwnerType.VERSION, result.versionId()));
        return result;
    }

    @DSTransactional
    @Override
    public EnterpriseAdminResultVo revise(long operatorId, long profileId, EnterpriseAdminReviseBo command) {
        if (command == null) {
            throw failure("ENTERPRISE_ADMIN_REVISION_REQUIRED");
        }
        String reason = reason(command.reason());
        EnterpriseIdentityFields fields = fields(command.identity());
        Instant now = clock.instant();
        var state = beginRevise(positive(profileId, "ENTERPRISE_PROFILE_INVALID"), fields,
            command.expectedVersion(), positive(operatorId, "ENTERPRISE_OPERATOR_INVALID"), reason, now);
        MaterialOwnerKey source = owner(MaterialOwnerType.SOURCE, state.sourceId());
        materials.validateRequired(source, fields.legalDocumentTypeCode(), Set.of("ALWAYS"));
        EnterpriseAdminResultVo result = completeRevise(state, operatorId, reason, now);
        materials.snapshotImmutable(source, owner(MaterialOwnerType.VERSION, result.versionId()));
        return result;
    }

    @DSTransactional
    @Override
    public EnterpriseAdminResultVo manageBinding(long operatorId, long profileId, EnterpriseAdminBindingBo command) {
        String action = upper(command == null ? null : command.action());
        if (!BINDING_ACTIONS.contains(action)) {
            throw failure("ENTERPRISE_BINDING_ACTION_INVALID");
        }
        return manageBindingData(positive(profileId, "ENTERPRISE_PROFILE_INVALID"), action,
            command.expectedBindingVersion(), positive(operatorId, "ENTERPRISE_OPERATOR_INVALID"),
            reason(command.reason()), clock.instant());
    }

    @DSTransactional
    @Override
    public EnterpriseAdminResultVo assign(long operatorId, long profileId, EnterpriseAdminAssignBo command) {
        if (command == null || command.userId() == null) {
            throw failure("ENTERPRISE_BINDING_TARGET_REQUIRED");
        }
        requireEligibleUser(command.userId());
        return assignData(positive(profileId, "ENTERPRISE_PROFILE_INVALID"), command.userId(),
            positive(operatorId, "ENTERPRISE_OPERATOR_INVALID"), reason(command.reason()), clock.instant());
    }

    @DSTransactional
    @Override
    public EnterpriseAdminResultVo revoke(long operatorId, long profileId, EnterpriseAdminRevokeBo command) {
        if (command == null) {
            throw failure("ENTERPRISE_REVOKE_REQUIRED");
        }
        return revokeData(positive(profileId, "ENTERPRISE_PROFILE_INVALID"), command.expectedVersion(),
            positive(operatorId, "ENTERPRISE_OPERATOR_INVALID"), reason(command.reason()), clock.instant());
    }

    PageResult<EnterpriseProfileSummaryVo> pageData(EnterpriseAdminQueryBo query) {
        int page = Math.max(1, query.pageNum());
        int size = query.pageSize() <= 0 ? 20 : Math.min(query.pageSize(), 200);
        String status = upper(query.status());
        if (!status.isEmpty() && !List.of("ACTIVE", "REVOKED").contains(status)) {
            throw failure("ENTERPRISE_QUERY_STATUS_INVALID");
        }
        String enterpriseName = text(query.enterpriseName());
        String unifiedCreditCode = upper(query.unifiedCreditCode());
        long total = mapper.countProfiles(enterpriseName, unifiedCreditCode, status);
        List<EnterpriseProfileSummaryVo> rows = mapper.selectProfiles(enterpriseName, unifiedCreditCode, status,
            size, (page - 1) * size).stream().map(this::summary).toList();
        return PageResult.build(rows, total);
    }

    EnterpriseProfileDetailVo detailData(long profileId) {
        ProfileRow profile = requireProfile(mapper.selectProfile(profileId));
        List<EnterpriseProfileVersionVo> versions = mapper.selectVersions(profileId).stream()
            .map(this::version).toList();
        return new EnterpriseProfileDetailVo(summary(profile), versions,
            mapper.selectBindings(profileId).stream().map(this::binding).toList(),
            mapper.selectSources(profileId).stream().map(this::source).toList(),
            mapper.selectAudits(profileId).stream().map(this::audit).toList(), List.of());
    }

    ReviewData reviewData(long applicationId) {
        return reviewData(requireReview(mapper.selectReview(applicationId)));
    }

    DecisionState beginDecision(long applicationId, String decision, long operatorId,
                                String reason, Instant now) {
        ReviewRow row = requireReview(mapper.lockWaitingApplication(applicationId));
        int nextDecision = value(row.getDecisionVersion()) + 1;
        changed(mapper.markOverridePending(applicationId, decision, reason, value(row.getDecisionVersion()),
            value(row.getVersion()), operatorId, now), "ENTERPRISE_ADMIN_DECISION_CONFLICT");
        changed(mapper.insertDecision(IdWorker.getId(), applicationId, row.getSubmissionId(), nextDecision,
            decision, operatorId, reason, now), "ENTERPRISE_ADMIN_DECISION_CONFLICT");
        audit(null, applicationId, null, "ADMIN_DECISION_PENDING", operatorId,
            "profile:enterprise:override", reason, "WAITING", "OVERRIDE_PENDING", now);
        return new DecisionState(applicationId, row.getSubmissionId(), value(row.getSubmissionSeq()), nextDecision);
    }

    void resumeForApproval(DecisionState state, long operatorId) {
        changed(mapper.resumeWaiting(state.applicationId(), state.decisionVersion(), operatorId),
            "ENTERPRISE_ADMIN_DECISION_CONFLICT");
    }

    void finalizeApproved(DecisionState state, long profileId, long versionId, long operatorId,
                          String reason, Instant now) {
        changed(mapper.markApproved(state.applicationId(), operatorId, reason, now),
            "ENTERPRISE_ADMIN_DECISION_CONFLICT");
        changed(mapper.finalizeDecision(state.applicationId(), state.decisionVersion(), operatorId, now),
            "ENTERPRISE_ADMIN_DECISION_CONFLICT");
        audit(profileId, state.applicationId(), null, "ADMIN_APPROVE", operatorId,
            "profile:enterprise:override", reason, "OVERRIDE_PENDING", "FINISH", now);
    }

    void finalizeRejected(DecisionState state, long operatorId, String reason, Instant now) {
        changed(mapper.markRejected(state.applicationId(), state.decisionVersion(), operatorId, reason, now),
            "ENTERPRISE_ADMIN_DECISION_CONFLICT");
        changed(mapper.finalizeDecision(state.applicationId(), state.decisionVersion(), operatorId, now),
            "ENTERPRISE_ADMIN_DECISION_CONFLICT");
        audit(null, state.applicationId(), null, "ADMIN_REJECT", operatorId,
            "profile:enterprise:override", reason, "OVERRIDE_PENDING", "INVALID", now);
    }

    CreateState beginCreate(EnterpriseIdentityFields fields, long operatorId, String reason, Instant now) {
        long profileId = IdWorker.getId();
        long sourceId = IdWorker.getId();
        try {
            changed(mapper.insertProfile(profileId, fields.enterpriseName(), fields.unifiedCreditCode(),
                fields.enterpriseType(), fields.legalRepresentativeName(), fields.legalDocumentTypeCode(),
                fields.legalDocumentNumber(), fields.establishedDate(), fields.businessTermFrom(),
                fields.businessTermUntil(), fields.registeredAddress(), fields.businessScope(), fields.contactName(),
                fields.contactPhone(), fields.email(), fields.registeredCapital(), fields.industryCode(),
                fields.website(), operatorId, now), "ENTERPRISE_ADMIN_CREATE_CONFLICT");
            insertSource(sourceId, profileId, "ADMIN_CREATE", fields, operatorId, reason, now);
        } catch (DuplicateKeyException exception) {
            throw new EnterpriseAdminException("ENTERPRISE_ADMIN_IDENTITY_CONFLICT", exception);
        }
        return new CreateState(profileId, sourceId, fields);
    }

    EnterpriseAdminResultVo completeCreate(CreateState state, Long bindUserId, long operatorId,
                                           String reason, Instant now) {
        long versionId = IdWorker.getId();
        try {
            insertVersion(versionId, state.profileId(), 1, "ADMIN_CREATE", state.sourceId(), state.fields(),
                operatorId, now);
            changed(mapper.updateProfileVersion(state.profileId(), versionId, state.fields().enterpriseName(),
                state.fields().unifiedCreditCode(), state.fields().enterpriseType(),
                state.fields().legalRepresentativeName(), state.fields().legalDocumentTypeCode(),
                state.fields().legalDocumentNumber(), state.fields().establishedDate(),
                state.fields().businessTermFrom(), state.fields().businessTermUntil(),
                state.fields().registeredAddress(), state.fields().businessScope(), state.fields().contactName(),
                state.fields().contactPhone(), state.fields().email(), state.fields().registeredCapital(),
                state.fields().industryCode(), state.fields().website(), 0, operatorId, now),
                "ENTERPRISE_ADMIN_CREATE_CONFLICT");
            Long bindingId = bindUserId == null ? null
                : insertBinding(state.profileId(), bindUserId, "ADMIN_CREATE", state.sourceId(), operatorId,
                    reason, now);
            audit(state.profileId(), null, bindingId, "ADMIN_CREATE", operatorId,
                "profile:enterprise:override", reason, null, "ACTIVE", now);
            return new EnterpriseAdminResultVo("ACTIVE", state.profileId(), versionId, bindingId, 1);
        } catch (DuplicateKeyException exception) {
            throw new EnterpriseAdminException("ENTERPRISE_ADMIN_CREATE_CONFLICT", exception);
        }
    }

    ReviseState beginRevise(long profileId, EnterpriseIdentityFields fields, int expectedVersion,
                            long operatorId, String reason, Instant now) {
        ProfileRow profile = requireWritable(mapper.lockProfile(profileId));
        if (value(profile.getVersion()) != expectedVersion) {
            throw failure("ENTERPRISE_PROFILE_VERSION_CONFLICT");
        }
        VersionRow current = mapper.lockCurrentVersion(profileId);
        if (current == null) {
            throw failure("ENTERPRISE_PROFILE_VERSION_NOT_FOUND");
        }
        long sourceId = IdWorker.getId();
        insertSource(sourceId, profileId, "ADMIN_OVERRIDE", fields, operatorId, reason, now);
        mapper.cloneVersionMaterials(current.getVersionId(), sourceId, IdWorker.getId(), operatorId, now);
        return new ReviseState(profileId, sourceId, value(current.getVersionNo()) + 1, expectedVersion, fields);
    }

    EnterpriseAdminResultVo completeRevise(ReviseState state, long operatorId, String reason, Instant now) {
        VersionRow current = mapper.lockCurrentVersion(state.profileId());
        if (current == null || value(current.getVersionNo()) + 1 != state.nextVersionNo()) {
            throw failure("ENTERPRISE_PROFILE_VERSION_CONFLICT");
        }
        long versionId = IdWorker.getId();
        try {
            changed(mapper.supersedeVersion(current.getVersionId(), operatorId, now),
                "ENTERPRISE_PROFILE_VERSION_CONFLICT");
            insertVersion(versionId, state.profileId(), state.nextVersionNo(), "ADMIN_OVERRIDE", state.sourceId(),
                state.fields(), operatorId, now);
            changed(mapper.updateProfileVersion(state.profileId(), versionId, state.fields().enterpriseName(),
                state.fields().unifiedCreditCode(), state.fields().enterpriseType(),
                state.fields().legalRepresentativeName(), state.fields().legalDocumentTypeCode(),
                state.fields().legalDocumentNumber(), state.fields().establishedDate(),
                state.fields().businessTermFrom(), state.fields().businessTermUntil(),
                state.fields().registeredAddress(), state.fields().businessScope(), state.fields().contactName(),
                state.fields().contactPhone(), state.fields().email(), state.fields().registeredCapital(),
                state.fields().industryCode(), state.fields().website(), state.profileVersion(), operatorId, now),
                "ENTERPRISE_PROFILE_VERSION_CONFLICT");
        } catch (DuplicateKeyException exception) {
            throw new EnterpriseAdminException("ENTERPRISE_ADMIN_IDENTITY_CONFLICT", exception);
        }
        audit(state.profileId(), null, null, "ADMIN_OVERRIDE", operatorId, "profile:enterprise:override",
            reason, "ACTIVE", "ACTIVE", now);
        return new EnterpriseAdminResultVo("ACTIVE", state.profileId(), versionId, null,
            state.profileVersion() + 1);
    }

    EnterpriseAdminResultVo manageBindingData(long profileId, String action, int expectedBindingVersion,
                                              long operatorId, String reason, Instant now) {
        requireWritable(mapper.lockProfile(profileId));
        BindingRow binding = mapper.lockEffectiveBinding(profileId);
        if (binding == null || value(binding.getBindingVersion()) != expectedBindingVersion) {
            throw failure("ENTERPRISE_BINDING_VERSION_CONFLICT");
        }
        String source = switch (action) {
            case "SUSPEND" -> "ACTIVE";
            case "RESUME" -> "SUSPENDED";
            case "UNBIND" -> binding.getStatus();
            default -> throw failure("ENTERPRISE_BINDING_ACTION_INVALID");
        };
        String target = switch (action) {
            case "SUSPEND" -> "SUSPENDED";
            case "RESUME" -> "ACTIVE";
            default -> "UNBOUND";
        };
        if (!source.equals(binding.getStatus())) {
            throw failure("ENTERPRISE_BINDING_STATE_CONFLICT");
        }
        int nextVersion = expectedBindingVersion + 1;
        changed(mapper.updateBinding(binding.getBindingId(), source, target, expectedBindingVersion, operatorId, now),
            "ENTERPRISE_BINDING_VERSION_CONFLICT");
        event(binding.getBindingId(), profileId, binding.getUserId(), target, nextVersion, null, reason,
            operatorId, now);
        audit(profileId, null, binding.getBindingId(), "BINDING_" + action, operatorId,
            "profile:enterprise:manage", reason, source, target, now);
        return new EnterpriseAdminResultVo(target, profileId, null, binding.getBindingId(), nextVersion);
    }

    EnterpriseAdminResultVo assignData(long profileId, long userId, long operatorId, String reason, Instant now) {
        requireWritable(mapper.lockProfile(profileId));
        if (mapper.lockEffectiveBinding(profileId) != null || mapper.countEffectiveBindingByUser(userId) != 0) {
            throw failure("ENTERPRISE_BINDING_TARGET_INELIGIBLE");
        }
        try {
            long bindingId = insertBinding(profileId, userId, "ADMIN_OVERRIDE", null, operatorId, reason, now);
            audit(profileId, null, bindingId, "BINDING_ASSIGN", operatorId, "profile:enterprise:override",
                reason, null, "ACTIVE", now);
            return new EnterpriseAdminResultVo("ACTIVE", profileId, null, bindingId, 1);
        } catch (DuplicateKeyException exception) {
            throw new EnterpriseAdminException("ENTERPRISE_BINDING_TARGET_INELIGIBLE", exception);
        }
    }

    EnterpriseAdminResultVo revokeData(long profileId, int expectedVersion, long operatorId, String reason,
                                       Instant now) {
        ProfileRow profile = requireWritable(mapper.lockProfile(profileId));
        if (value(profile.getVersion()) != expectedVersion) {
            throw failure("ENTERPRISE_PROFILE_VERSION_CONFLICT");
        }
        BindingRow binding = mapper.lockEffectiveBinding(profileId);
        changed(mapper.revokeProfile(profileId, expectedVersion, reason, operatorId, now),
            "ENTERPRISE_PROFILE_VERSION_CONFLICT");
        Long bindingId = null;
        if (binding != null) {
            bindingId = binding.getBindingId();
            int next = value(binding.getBindingVersion()) + 1;
            changed(mapper.updateBinding(bindingId, binding.getStatus(), "UNBOUND", value(binding.getBindingVersion()),
                operatorId, now), "ENTERPRISE_BINDING_VERSION_CONFLICT");
            event(bindingId, profileId, binding.getUserId(), "UNBOUND", next, null, reason, operatorId, now);
        }
        audit(profileId, null, bindingId, "REVOKE", operatorId, "profile:enterprise:override", reason,
            "ACTIVE", "REVOKED", now);
        return new EnterpriseAdminResultVo("REVOKED", profileId, profile.getCurrentVersionId(), bindingId,
            expectedVersion + 1);
    }

    boolean hasEffectiveBinding(long userId) {
        return mapper.countEffectiveBindingByUser(userId) != 0;
    }

    private void insertSource(long sourceId, long profileId, String sourceType, EnterpriseIdentityFields fields,
                              long operatorId, String reason, Instant now) {
        changed(mapper.insertSource(sourceId, profileId, sourceType, operatorId, reason, fields.enterpriseName(),
            fields.unifiedCreditCode(), fields.identityKey(), fields.enterpriseType(),
            fields.legalRepresentativeName(), fields.legalDocumentTypeCode(), fields.legalDocumentNumber(),
            fields.establishedDate(), fields.businessTermFrom(), fields.businessTermUntil(),
            fields.registeredAddress(), fields.businessScope(), fields.contactName(), fields.contactPhone(),
            fields.email(), fields.registeredCapital(), fields.industryCode(), fields.website(),
            jsonMapper.writeValueAsString(fields), now), "ENTERPRISE_ADMIN_SOURCE_CONFLICT");
    }

    private void insertVersion(long versionId, long profileId, int versionNo, String sourceType, long sourceId,
                               EnterpriseIdentityFields fields, long operatorId, Instant now) {
        changed(mapper.insertVersion(versionId, profileId, versionNo, sourceType, sourceId, fields.enterpriseName(),
            fields.unifiedCreditCode(), fields.enterpriseType(), fields.legalRepresentativeName(),
            fields.legalDocumentTypeCode(), fields.legalDocumentNumber(), fields.establishedDate(),
            fields.businessTermFrom(), fields.businessTermUntil(), fields.registeredAddress(), fields.businessScope(),
            fields.contactName(), fields.contactPhone(), fields.email(), fields.registeredCapital(),
            fields.industryCode(), fields.website(), operatorId, now), "ENTERPRISE_PROFILE_VERSION_CONFLICT");
    }

    private long insertBinding(long profileId, long userId, String sourceType, Long sourceId, long operatorId,
                               String reason, Instant now) {
        long bindingId = IdWorker.getId();
        changed(mapper.insertBinding(bindingId, profileId, userId, sourceType, sourceId, operatorId, now),
            "ENTERPRISE_BINDING_CONFLICT");
        event(bindingId, profileId, userId, "ACTIVE", 1, sourceId, reason, operatorId, now);
        return bindingId;
    }

    private void event(long bindingId, long profileId, long userId, String type, int version, Long sourceId,
                       String reason, long operatorId, Instant now) {
        changed(mapper.insertBindingEvent(IdWorker.getId(), bindingId, profileId, userId, type, version,
            sourceId, reason, operatorId, now), "ENTERPRISE_BINDING_EVENT_CONFLICT");
    }

    private void audit(Long profileId, Long applicationId, Long bindingId, String operation, long operatorId,
                       String capability, String reason, String before, String after, Instant now) {
        changed(mapper.insertAudit(IdWorker.getId(), profileId, applicationId, bindingId, operation, operatorId,
            capability, reason, before, after, now), "ENTERPRISE_AUDIT_CONFLICT");
    }

    private EnterpriseProfileSummaryVo summary(ProfileRow row) {
        return new EnterpriseProfileSummaryVo(row.getProfileId(), row.getPreviousProfileId(),
            row.getEnterpriseName(), row.getUnifiedCreditCode(), row.getEnterpriseType(),
            row.getLegalRepresentativeName(), row.getStatus(), row.getBindingUserId(), row.getBindingStatus(),
            row.getCreateTime());
    }

    private EnterpriseProfileVersionVo version(VersionRow row) {
        return new EnterpriseProfileVersionVo(row.getVersionId(), value(row.getVersionNo()), row.getSourceType(),
            row.getSourceId(), row.getEnterpriseName(), row.getUnifiedCreditCode(), row.getEnterpriseType(),
            row.getLegalRepresentativeName(), row.getLegalDocumentTypeCode(), row.getLegalDocumentNumber(),
            row.getEstablishedDate(), row.getBusinessTermFrom(), row.getBusinessTermUntil(),
            row.getRegisteredAddress(), row.getBusinessScope(), row.getContactName(), row.getContactPhone(),
            row.getEmail(), row.getRegisteredCapital(), row.getIndustryCode(), row.getWebsite(), row.getStatus(),
            row.getPublishedTime());
    }

    private EnterpriseProfileBindingVo binding(BindingRow row) {
        return new EnterpriseProfileBindingVo(row.getBindingId(), row.getUserId(), row.getStatus(),
            value(row.getBindingVersion()), row.getSourceType(), row.getSourceId(), row.getBoundTime(),
            row.getUnboundTime());
    }

    private EnterpriseProfileSourceVo source(SourceRow row) {
        return new EnterpriseProfileSourceVo(row.getSourceId(), row.getSourceType(), row.getOperatorUserId(),
            row.getReason(), row.getFieldSnapshotJson(), row.getOccurredTime());
    }

    private EnterpriseProfileAuditVo audit(AuditRow row) {
        return new EnterpriseProfileAuditVo(row.getAuditId(), row.getOperationType(), row.getOperatorUserId(),
            row.getCapability(), row.getReason(), row.getBeforeStatus(), row.getAfterStatus(), row.getResult(),
            row.getFailureCategory(), row.getOccurredTime());
    }

    private ReviewData reviewData(ReviewRow row) {
        return new ReviewData(row.getApplicationId(), row.getApplicantUserId(), row.getStatus(),
            value(row.getSubmissionSeq()), value(row.getDecisionVersion()), value(row.getVersion()),
            row.getSubmissionId(), row.getFieldSnapshotJson(), row.getSubmittedTime());
    }

    private ProfileRow requireProfile(ProfileRow row) {
        if (row == null) {
            throw failure("ENTERPRISE_PROFILE_NOT_FOUND");
        }
        return row;
    }

    private ProfileRow requireWritable(ProfileRow row) {
        row = requireProfile(row);
        if ("REVOKED".equals(row.getStatus())) {
            throw failure("ENTERPRISE_PROFILE_REVOKED_READ_ONLY");
        }
        return row;
    }

    private ReviewRow requireReview(ReviewRow row) {
        if (row == null) {
            throw failure("ENTERPRISE_REVIEW_CONTEXT_NOT_FOUND");
        }
        return row;
    }

    private void changed(int count, String category) {
        if (count != 1) {
            throw failure(category);
        }
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private String text(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    record ReviewData(long applicationId, long applicantUserId, String status, int submissionSeq,
                      int decisionVersion, int version, long submissionId, String fieldSnapshotJson,
                      Instant submittedTime) {
    }

    record DecisionState(long applicationId, long submissionId, int snapshotVersion, int decisionVersion) {
    }

    record CreateState(long profileId, long sourceId, EnterpriseIdentityFields fields) {
    }

    record ReviseState(long profileId, long sourceId, int nextVersionNo, int profileVersion,
                       EnterpriseIdentityFields fields) {
    }

    private void requireEligibleUser(long userId) {
        UserDTO user = users.selectById(userId);
        var profile = profiles.findByUserId(userId);
        if (user == null || !"0".equals(user.getStatus()) || hasEffectiveBinding(userId)
            || profile == null || profile.person() == null) {
            throw failure("ENTERPRISE_BINDING_TARGET_INELIGIBLE");
        }
    }

    private EnterpriseIdentityFields fields(EnterpriseAdminIdentityBo identity) {
        if (identity == null) {
            throw failure("ENTERPRISE_IDENTITY_REQUIRED");
        }
        EnterpriseIdentityFields fields = EnterpriseIdentityFields.normalize(new EnterpriseApplicationSaveBo(
            identity.enterpriseName(), identity.unifiedCreditCode(), identity.enterpriseType(),
            identity.legalRepresentativeName(), identity.legalDocumentTypeCode(), identity.legalDocumentNumber(),
            true, identity.establishedDate(), identity.businessTermFrom(), identity.businessTermUntil(),
            identity.registeredAddress(), identity.businessScope(), identity.contactName(), identity.contactPhone(),
            identity.email(), identity.registeredCapital(), identity.industryCode(), identity.website(), 0));
        LocalDate today = LocalDate.now(clock);
        if (fields.enterpriseName() == null || fields.unifiedCreditCode() == null || fields.enterpriseType() == null
            || fields.legalRepresentativeName() == null || fields.legalDocumentTypeCode() == null
            || fields.legalDocumentNumber() == null || fields.establishedDate() == null
            || fields.registeredAddress() == null || fields.businessScope() == null
            || fields.establishedDate().isAfter(today)
            || (fields.businessTermFrom() != null && fields.businessTermUntil() != null
            && fields.businessTermFrom().isAfter(fields.businessTermUntil()))) {
            throw failure("ENTERPRISE_IDENTITY_INVALID");
        }
        var rule = applications.findDocumentType(fields.legalDocumentTypeCode())
            .orElseThrow(() -> failure("ENTERPRISE_DOCUMENT_TYPE_UNAVAILABLE"));
        if (!Pattern.matches(rule.numberPattern(), fields.legalDocumentNumber())) {
            throw failure("ENTERPRISE_IDENTITY_INVALID");
        }
        return fields;
    }

    private MaterialOwnerKey owner(MaterialOwnerType type, long id) {
        return new MaterialOwnerKey(ProfileType.ENTERPRISE, type, id);
    }

    private long positive(long value, String category) {
        if (value <= 0) {
            throw failure(category);
        }
        return value;
    }

    private String reason(String value) {
        if (value == null || value.isBlank() || value.strip().length() > 500) {
            throw failure("ENTERPRISE_ADMIN_REASON_REQUIRED");
        }
        return value.strip();
    }

    private String upper(String value) {
        return value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
    }

    private EnterpriseAdminException failure(String category) {
        return new EnterpriseAdminException(category);
    }
}

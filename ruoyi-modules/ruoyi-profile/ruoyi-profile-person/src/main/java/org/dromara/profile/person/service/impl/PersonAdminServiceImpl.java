package org.dromara.profile.person.service.impl;

import org.dromara.profile.person.service.IPersonAdminService;
import org.dromara.profile.person.service.IPersonApplicationService;

import org.dromara.profile.person.domain.exception.PersonAdminException;
import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.dromara.common.core.domain.PageResult;
import org.dromara.profile.api.domain.ProfileType;
import org.dromara.profile.api.material.ProfileMaterialPort;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialAttachCommand;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerKey;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerType;
import org.dromara.profile.person.domain.bo.*;
import org.dromara.profile.person.domain.vo.*;
import org.dromara.profile.person.domain.vo.PersonAdminRows.*;
import org.dromara.profile.person.domain.bo.PersonApplicationSaveBo;
import org.dromara.profile.person.domain.application.PersonIdentityFields;
import org.dromara.profile.person.domain.application.PersonPublication;
import org.dromara.profile.person.domain.application.PersonSubmission;
import org.dromara.profile.person.mapper.PersonAdminMapper;
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
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class PersonAdminServiceImpl implements IPersonAdminService {

    private static final Set<String> DECISIONS = Set.of("APPROVED", "REJECTED");
    private static final Set<String> BINDING_ACTIONS = Set.of("SUSPEND", "RESUME", "UNBIND");

    private final PersonAdminMapper mapper;
    private final JsonMapper jsonMapper;
    private final IPersonApplicationService applications;
    private final ProfileMaterialPort materials;
    private final WorkflowService workflow;
    private final UserService users;
    private final Clock clock;

    @Autowired
    public PersonAdminServiceImpl(PersonAdminMapper mapper, JsonMapper jsonMapper,
                                  IPersonApplicationService applications, ProfileMaterialPort materials,
                                  ObjectProvider<WorkflowService> workflowProvider, UserService users) {
        this(mapper, jsonMapper, applications, materials, workflowProvider.getIfAvailable(), users,
            Clock.systemUTC());
    }

    PersonAdminServiceImpl(PersonAdminMapper mapper, JsonMapper jsonMapper,
                           IPersonApplicationService applications, ProfileMaterialPort materials,
                           WorkflowService workflow, UserService users) {
        this(mapper, jsonMapper, applications, materials, workflow, users, Clock.systemUTC());
    }

    PersonAdminServiceImpl(PersonAdminMapper mapper, JsonMapper jsonMapper,
                           IPersonApplicationService applications, ProfileMaterialPort materials,
                           WorkflowService workflow, UserService users, Clock clock) {
        this.mapper = mapper;
        this.jsonMapper = jsonMapper;
        this.applications = applications;
        this.materials = materials;
        this.workflow = workflow;
        this.users = users;
        this.clock = clock;
    }

    public PageResult<PersonProfileSummaryVo> page(PersonAdminQueryBo query) {
        return loadPage(query == null ? new PersonAdminQueryBo(null, null, null, 1, 20) : query);
    }

    public List<PersonAccountCandidateVo> eligibleUsers(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        return users.searchActiveUsers(keyword.strip(), 50).stream()
            .filter(user -> user.getUserId() != null && "0".equals(user.getStatus()))
            .filter(user -> !hasEffectiveBinding(user.getUserId()))
            .limit(20)
            .map(user -> new PersonAccountCandidateVo(user.getUserId(), user.getUserName(), user.getNickName()))
            .toList();
    }

    public PersonProfileDetailVo detail(long profileId) {
        PersonProfileDetailVo detail = loadDetail(positive(profileId, "PERSON_PROFILE_INVALID"));
        List<ProfileMaterialPort.MaterialReferenceView> current = detail.versions().isEmpty()
            ? List.of() : materials.list(owner(MaterialOwnerType.VERSION,
            detail.versions().stream().filter(version -> "CURRENT".equals(version.status())).findFirst()
                .orElse(detail.versions().getFirst()).versionId()));
        return new PersonProfileDetailVo(detail.profile(), detail.versions(), detail.bindings(), detail.sources(), detail.audits(), current);
    }

    public PersonReviewContextVo review(long applicationId) {
        var data = loadReview(positive(applicationId, "PERSON_APPLICATION_INVALID"));
        var owner = owner(MaterialOwnerType.SUBMISSION, data.submissionId());
        return new PersonReviewContextVo(data.applicationId(), data.applicantUserId(), data.status(), data.submissionSeq(),
            data.decisionVersion(), data.version(), data.submissionId(), data.fieldSnapshotJson(),
            data.submittedTime(), materials.list(owner));
    }

    public org.dromara.system.api.OssService.OssAccessUrl reviewMaterial(long applicationId, long materialRefId) {
        return materials.accessUrl(owner(MaterialOwnerType.SUBMISSION, review(applicationId).submissionId()), materialRefId);
    }

    public org.dromara.system.api.OssService.OssAccessUrl material(long profileId, long materialRefId) {
        PersonProfileDetailVo detail = detail(profileId);
        PersonProfileVersionVo current = detail.versions().stream().filter(version -> "CURRENT".equals(version.status()))
            .findFirst().orElseThrow(() -> failure("PERSON_PROFILE_VERSION_NOT_FOUND"));
        return materials.accessUrl(owner(MaterialOwnerType.VERSION, current.versionId()), materialRefId);
    }

    @DSTransactional
    public PersonAdminResultVo decide(long operatorId, long applicationId, PersonAdminDecisionBo command) {
        String reason = reason(command == null ? null : command.reason());
        String decision = upper(command == null ? null : command.decision());
        if (!DECISIONS.contains(decision)) {
            throw failure("PERSON_ADMIN_DECISION_INVALID");
        }
        if (workflow == null) {
            throw failure("PERSON_ADMIN_WORKFLOW_UNAVAILABLE");
        }
        Instant now = clock.instant();
        var state = beginDecision(positive(applicationId, "PERSON_APPLICATION_INVALID"), decision,
            positive(operatorId, "PERSON_OPERATOR_INVALID"), reason, now);
        try {
            workflow.terminateInstance(Long.toString(applicationId), reason);
        } catch (RuntimeException exception) {
            throw new PersonAdminException("PERSON_ADMIN_WORKFLOW_TERMINATION_FAILED", exception);
        }
        if ("REJECTED".equals(decision)) {
            finalizeRejected(state, operatorId, reason, now);
            return new PersonAdminResultVo("REJECTED", 0L, null, null, state.decisionVersion());
        }
        resumeForApproval(state, operatorId);
        PersonSubmission submission = applications.requireSubmission(applicationId, state.snapshotVersion());
        PersonPublication publication = applications.publishApproved(applicationId, state.snapshotVersion(), now);
        materials.snapshotImmutable(owner(MaterialOwnerType.SUBMISSION, submission.personSubmissionId()),
            owner(MaterialOwnerType.VERSION, publication.personVersionId()));
        finalizeApproved(state, publication.personProfileId(), publication.personVersionId(), operatorId,
            reason, now);
        return new PersonAdminResultVo("APPROVED", publication.personProfileId(), publication.personVersionId(),
            publication.personBindingId(), state.decisionVersion());
    }

    @DSTransactional
    public PersonAdminResultVo create(long operatorId, PersonAdminCreateBo command) {
        if (command == null) {
            throw failure("PERSON_ADMIN_CREATE_REQUIRED");
        }
        String reason = reason(command.reason());
        PersonIdentityFields fields = fields(command.identity());
        Instant now = clock.instant();
        var state = beginCreate(fields, positive(operatorId, "PERSON_OPERATOR_INVALID"), reason, now);
        List<PersonAdminMaterialBo> inputs = command.materials() == null ? List.of() : List.copyOf(command.materials());
        for (PersonAdminMaterialBo input : inputs) {
            materials.attach(new MaterialAttachCommand(owner(MaterialOwnerType.SOURCE, state.sourceId()),
                input.ossId(), input.materialNodeId()));
        }
        MaterialOwnerKey source = owner(MaterialOwnerType.SOURCE, state.sourceId());
        materials.validateRequired(source, fields.documentTypeCode(), Set.of("ALWAYS"));
        if (command.bindUserId() != null) {
            requireEligibleUser(command.bindUserId());
        }
        PersonAdminResultVo result = completeCreate(state, command.bindUserId(), operatorId, reason, now);
        materials.snapshotImmutable(source, owner(MaterialOwnerType.VERSION, result.versionId()));
        return result;
    }

    @DSTransactional
    public PersonAdminResultVo revise(long operatorId, long profileId, PersonAdminReviseBo command) {
        if (command == null) {
            throw failure("PERSON_ADMIN_REVISION_REQUIRED");
        }
        String reason = reason(command.reason());
        PersonIdentityFields fields = fields(command.identity());
        Instant now = clock.instant();
        var state = beginRevise(positive(profileId, "PERSON_PROFILE_INVALID"), fields,
            command.expectedVersion(), positive(operatorId, "PERSON_OPERATOR_INVALID"), reason, now);
        MaterialOwnerKey source = owner(MaterialOwnerType.SOURCE, state.sourceId());
        materials.validateRequired(source, fields.documentTypeCode(), Set.of("ALWAYS"));
        PersonAdminResultVo result = completeRevise(state, operatorId, reason, now);
        materials.snapshotImmutable(source, owner(MaterialOwnerType.VERSION, result.versionId()));
        return result;
    }

    @DSTransactional
    public PersonAdminResultVo manageBinding(long operatorId, long profileId, PersonAdminBindingBo command) {
        String action = upper(command == null ? null : command.action());
        if (!BINDING_ACTIONS.contains(action)) {
            throw failure("PERSON_BINDING_ACTION_INVALID");
        }
        return manageBindingState(positive(profileId, "PERSON_PROFILE_INVALID"), action,
            command.expectedBindingVersion(), positive(operatorId, "PERSON_OPERATOR_INVALID"),
            reason(command.reason()), clock.instant());
    }

    @DSTransactional
    public PersonAdminResultVo assign(long operatorId, long profileId, PersonAdminAssignBo command) {
        if (command == null || command.userId() == null) {
            throw failure("PERSON_BINDING_TARGET_REQUIRED");
        }
        requireEligibleUser(command.userId());
        return assignBinding(positive(profileId, "PERSON_PROFILE_INVALID"), command.userId(),
            positive(operatorId, "PERSON_OPERATOR_INVALID"), reason(command.reason()), clock.instant());
    }

    @DSTransactional
    public PersonAdminResultVo revoke(long operatorId, long profileId, PersonAdminRevokeBo command) {
        if (command == null) {
            throw failure("PERSON_REVOKE_REQUIRED");
        }
        return revokeProfile(positive(profileId, "PERSON_PROFILE_INVALID"), command.expectedVersion(),
            positive(operatorId, "PERSON_OPERATOR_INVALID"), reason(command.reason()), clock.instant());
    }

    PageResult<PersonProfileSummaryVo> loadPage(PersonAdminQueryBo query) {
        int page = Math.max(1, query.pageNum());
        int size = query.pageSize() <= 0 ? 20 : Math.min(query.pageSize(), 200);
        String status = upper(query.status());
        if (!status.isEmpty() && !List.of("ACTIVE", "REVOKED").contains(status)) {
            throw failure("PERSON_QUERY_STATUS_INVALID");
        }
        String fullName = text(query.fullName());
        String documentNumber = upper(query.documentNumber());
        long total = mapper.countProfiles(fullName, documentNumber, status);
        List<PersonProfileSummaryVo> rows = mapper
            .selectProfiles(fullName, documentNumber, status, size, (page - 1) * size)
            .stream().map(this::summary).toList();
        return PageResult.build(rows, total);
    }

    PersonProfileDetailVo loadDetail(long profileId) {
        ProfileRow profile = requireProfile(mapper.selectProfile(profileId));
        List<PersonProfileVersionVo> versions = mapper.selectVersions(profileId).stream()
            .map(this::version).toList();
        return new PersonProfileDetailVo(summary(profile), versions,
            mapper.selectBindings(profileId).stream().map(this::binding).toList(),
            mapper.selectSources(profileId).stream().map(this::source).toList(),
            mapper.selectAudits(profileId).stream().map(this::audit).toList(), List.of());
    }

    ReviewData loadReview(long applicationId) {
        return reviewData(requireReview(mapper.selectReview(applicationId)));
    }

    DecisionState beginDecision(long applicationId, String decision, long operatorId,
                                String reason, Instant now) {
        ReviewRow row = requireReview(mapper.lockWaitingApplication(applicationId));
        int nextDecision = value(row.getDecisionVersion()) + 1;
        changed(mapper.markOverridePending(applicationId, decision, reason, value(row.getDecisionVersion()),
            value(row.getVersion()), operatorId, now), "PERSON_ADMIN_DECISION_CONFLICT");
        changed(mapper.insertDecision(IdWorker.getId(), applicationId, row.getSubmissionId(), nextDecision,
            decision, operatorId, reason, now), "PERSON_ADMIN_DECISION_CONFLICT");
        audit(null, applicationId, null, "ADMIN_DECISION_PENDING", operatorId, "profile:person:override",
            reason, "WAITING", "OVERRIDE_PENDING", now);
        return new DecisionState(applicationId, row.getSubmissionId(), value(row.getSubmissionSeq()), nextDecision);
    }

    void resumeForApproval(DecisionState state, long operatorId) {
        changed(mapper.resumeWaiting(state.applicationId(), state.decisionVersion(), operatorId),
            "PERSON_ADMIN_DECISION_CONFLICT");
    }

    void finalizeApproved(DecisionState state, long profileId, long versionId, long operatorId,
                          String reason, Instant now) {
        changed(mapper.markApproved(state.applicationId(), operatorId, reason, now),
            "PERSON_ADMIN_DECISION_CONFLICT");
        changed(mapper.finalizeDecision(state.applicationId(), state.decisionVersion(), operatorId, now),
            "PERSON_ADMIN_DECISION_CONFLICT");
        audit(profileId, state.applicationId(), null, "ADMIN_APPROVE", operatorId, "profile:person:override",
            reason, "OVERRIDE_PENDING", "FINISH", now);
    }

    void finalizeRejected(DecisionState state, long operatorId, String reason, Instant now) {
        changed(mapper.markRejected(state.applicationId(), state.decisionVersion(), operatorId, reason, now),
            "PERSON_ADMIN_DECISION_CONFLICT");
        changed(mapper.finalizeDecision(state.applicationId(), state.decisionVersion(), operatorId, now),
            "PERSON_ADMIN_DECISION_CONFLICT");
        audit(null, state.applicationId(), null, "ADMIN_REJECT", operatorId, "profile:person:override",
            reason, "OVERRIDE_PENDING", "INVALID", now);
    }

    CreateState beginCreate(PersonIdentityFields fields, long operatorId, String reason, Instant now) {
        long profileId = IdWorker.getId();
        long sourceId = IdWorker.getId();
        try {
            changed(mapper.insertProfile(profileId, fields.fullName(), fields.documentTypeCode(),
                fields.documentNumber(), fields.identityKey(), fields.gender(), fields.birthDate(), fields.validFrom(),
                fields.validUntil(), operatorId, now), "PERSON_ADMIN_CREATE_CONFLICT");
            insertSource(sourceId, profileId, "ADMIN_CREATE", fields, operatorId, reason, now);
        } catch (DuplicateKeyException exception) {
            throw new PersonAdminException("PERSON_ADMIN_IDENTITY_CONFLICT", exception);
        }
        return new CreateState(profileId, sourceId, fields);
    }

    PersonAdminResultVo completeCreate(CreateState state, Long bindUserId, long operatorId, String reason,
                                       Instant now) {
        long versionId = IdWorker.getId();
        try {
            insertVersion(versionId, state.profileId(), 1, "ADMIN_CREATE", state.sourceId(), state.fields(),
                operatorId, now);
            changed(mapper.updateProfileVersion(state.profileId(), versionId, state.fields().fullName(),
                state.fields().documentTypeCode(), state.fields().documentNumber(), state.fields().identityKey(),
                state.fields().gender(), state.fields().birthDate(), state.fields().validFrom(),
                state.fields().validUntil(), 0, operatorId, now), "PERSON_ADMIN_CREATE_CONFLICT");
            Long bindingId = bindUserId == null ? null
                : insertBinding(state.profileId(), bindUserId, "ADMIN_CREATE", state.sourceId(), operatorId,
                    reason, now);
            audit(state.profileId(), null, bindingId, "ADMIN_CREATE", operatorId, "profile:person:override",
                reason, null, "ACTIVE", now);
            return new PersonAdminResultVo("ACTIVE", state.profileId(), versionId, bindingId, 1);
        } catch (DuplicateKeyException exception) {
            throw new PersonAdminException("PERSON_ADMIN_CREATE_CONFLICT", exception);
        }
    }

    ReviseState beginRevise(long profileId, PersonIdentityFields fields, int expectedVersion,
                            long operatorId, String reason, Instant now) {
        ProfileRow profile = requireWritable(mapper.lockProfile(profileId));
        if (value(profile.getVersion()) != expectedVersion) {
            throw failure("PERSON_PROFILE_VERSION_CONFLICT");
        }
        VersionRow current = mapper.lockCurrentVersion(profileId);
        if (current == null) {
            throw failure("PERSON_PROFILE_VERSION_NOT_FOUND");
        }
        long sourceId = IdWorker.getId();
        insertSource(sourceId, profileId, "ADMIN_OVERRIDE", fields, operatorId, reason, now);
        mapper.cloneVersionMaterials(current.getVersionId(), sourceId, IdWorker.getId(), operatorId, now);
        return new ReviseState(profileId, sourceId, value(current.getVersionNo()) + 1,
            expectedVersion, fields);
    }

    PersonAdminResultVo completeRevise(ReviseState state, long operatorId, String reason, Instant now) {
        VersionRow current = mapper.lockCurrentVersion(state.profileId());
        if (current == null || value(current.getVersionNo()) + 1 != state.nextVersionNo()) {
            throw failure("PERSON_PROFILE_VERSION_CONFLICT");
        }
        long versionId = IdWorker.getId();
        try {
            changed(mapper.supersedeVersion(current.getVersionId(), operatorId, now),
                "PERSON_PROFILE_VERSION_CONFLICT");
            insertVersion(versionId, state.profileId(), state.nextVersionNo(), "ADMIN_OVERRIDE", state.sourceId(),
                state.fields(), operatorId, now);
            changed(mapper.updateProfileVersion(state.profileId(), versionId, state.fields().fullName(),
                state.fields().documentTypeCode(), state.fields().documentNumber(), state.fields().identityKey(),
                state.fields().gender(), state.fields().birthDate(), state.fields().validFrom(),
                state.fields().validUntil(), state.profileVersion(), operatorId, now),
                "PERSON_PROFILE_VERSION_CONFLICT");
        } catch (DuplicateKeyException exception) {
            throw new PersonAdminException("PERSON_ADMIN_IDENTITY_CONFLICT", exception);
        }
        audit(state.profileId(), null, null, "ADMIN_OVERRIDE", operatorId, "profile:person:override",
            reason, "ACTIVE", "ACTIVE", now);
        return new PersonAdminResultVo("ACTIVE", state.profileId(), versionId, null, state.profileVersion() + 1);
    }

    PersonAdminResultVo manageBindingState(long profileId, String action, int expectedBindingVersion,
                                           long operatorId, String reason, Instant now) {
        requireWritable(mapper.lockProfile(profileId));
        BindingRow binding = mapper.lockEffectiveBinding(profileId);
        if (binding == null || value(binding.getBindingVersion()) != expectedBindingVersion) {
            throw failure("PERSON_BINDING_VERSION_CONFLICT");
        }
        String source = switch (action) {
            case "SUSPEND" -> "ACTIVE";
            case "RESUME" -> "SUSPENDED";
            case "UNBIND" -> binding.getStatus();
            default -> throw failure("PERSON_BINDING_ACTION_INVALID");
        };
        String target = switch (action) {
            case "SUSPEND" -> "SUSPENDED";
            case "RESUME" -> "ACTIVE";
            default -> "UNBOUND";
        };
        if (!source.equals(binding.getStatus())) {
            throw failure("PERSON_BINDING_STATE_CONFLICT");
        }
        int nextVersion = expectedBindingVersion + 1;
        changed(mapper.updateBinding(binding.getBindingId(), source, target, expectedBindingVersion, operatorId, now),
            "PERSON_BINDING_VERSION_CONFLICT");
        event(binding.getBindingId(), profileId, binding.getUserId(), target, nextVersion, null, reason, operatorId,
            now);
        audit(profileId, null, binding.getBindingId(), "BINDING_" + action, operatorId,
            "profile:person:manage", reason, source, target, now);
        return new PersonAdminResultVo(target, profileId, null, binding.getBindingId(), nextVersion);
    }

    PersonAdminResultVo assignBinding(long profileId, long userId, long operatorId, String reason, Instant now) {
        requireWritable(mapper.lockProfile(profileId));
        if (mapper.lockEffectiveBinding(profileId) != null || mapper.countEffectiveBindingByUser(userId) != 0) {
            throw failure("PERSON_BINDING_TARGET_INELIGIBLE");
        }
        try {
            long bindingId = insertBinding(profileId, userId, "ADMIN_OVERRIDE", null, operatorId, reason, now);
            audit(profileId, null, bindingId, "BINDING_ASSIGN", operatorId, "profile:person:override",
                reason, null, "ACTIVE", now);
            return new PersonAdminResultVo("ACTIVE", profileId, null, bindingId, 1);
        } catch (DuplicateKeyException exception) {
            throw new PersonAdminException("PERSON_BINDING_TARGET_INELIGIBLE", exception);
        }
    }

    PersonAdminResultVo revokeProfile(long profileId, int expectedVersion, long operatorId, String reason,
                                      Instant now) {
        ProfileRow profile = requireWritable(mapper.lockProfile(profileId));
        if (value(profile.getVersion()) != expectedVersion) {
            throw failure("PERSON_PROFILE_VERSION_CONFLICT");
        }
        BindingRow binding = mapper.lockEffectiveBinding(profileId);
        changed(mapper.revokeProfile(profileId, expectedVersion, reason, operatorId, now),
            "PERSON_PROFILE_VERSION_CONFLICT");
        Long bindingId = null;
        if (binding != null) {
            bindingId = binding.getBindingId();
            int next = value(binding.getBindingVersion()) + 1;
            changed(mapper.updateBinding(bindingId, binding.getStatus(), "UNBOUND",
                value(binding.getBindingVersion()), operatorId, now), "PERSON_BINDING_VERSION_CONFLICT");
            event(bindingId, profileId, binding.getUserId(), "UNBOUND", next, null, reason, operatorId, now);
        }
        audit(profileId, null, bindingId, "REVOKE", operatorId, "profile:person:override", reason,
            "ACTIVE", "REVOKED", now);
        return new PersonAdminResultVo("REVOKED", profileId, profile.getCurrentVersionId(), bindingId,
            expectedVersion + 1);
    }

    boolean hasEffectiveBinding(long userId) {
        return mapper.countEffectiveBindingByUser(userId) != 0;
    }

    private void insertSource(long sourceId, long profileId, String sourceType, PersonIdentityFields fields,
                              long operatorId, String reason, Instant now) {
        changed(mapper.insertSource(sourceId, profileId, sourceType, operatorId, reason, fields.fullName(),
            fields.documentTypeCode(), fields.documentNumber(), fields.identityKey(), fields.gender(),
            fields.birthDate(), fields.validFrom(), fields.validUntil(), jsonMapper.writeValueAsString(fields), now),
            "PERSON_ADMIN_SOURCE_CONFLICT");
    }

    private void insertVersion(long versionId, long profileId, int versionNo, String sourceType, long sourceId,
                               PersonIdentityFields fields, long operatorId, Instant now) {
        changed(mapper.insertVersion(versionId, profileId, versionNo, sourceType, sourceId, fields.fullName(),
            fields.documentTypeCode(), fields.documentNumber(), fields.identityKey(), fields.gender(),
            fields.birthDate(), fields.validFrom(), fields.validUntil(), operatorId, now),
            "PERSON_PROFILE_VERSION_CONFLICT");
    }

    private long insertBinding(long profileId, long userId, String sourceType, Long sourceId, long operatorId,
                               String reason, Instant now) {
        long bindingId = IdWorker.getId();
        changed(mapper.insertBinding(bindingId, profileId, userId, sourceType, sourceId, operatorId, now),
            "PERSON_BINDING_CONFLICT");
        event(bindingId, profileId, userId, "ACTIVE", 1, sourceId, reason, operatorId, now);
        return bindingId;
    }

    private void event(long bindingId, long profileId, long userId, String type, int version, Long sourceId,
                       String reason, long operatorId, Instant now) {
        changed(mapper.insertBindingEvent(IdWorker.getId(), bindingId, profileId, userId, type, version,
            sourceId, reason, operatorId, now), "PERSON_BINDING_EVENT_CONFLICT");
    }

    private void audit(Long profileId, Long applicationId, Long bindingId, String operation, long operatorId,
                       String capability, String reason, String before, String after, Instant now) {
        changed(mapper.insertAudit(IdWorker.getId(), profileId, applicationId, bindingId, operation, operatorId,
            capability, reason, before, after, now), "PERSON_AUDIT_CONFLICT");
    }

    private PersonProfileSummaryVo summary(ProfileRow row) {
        return new PersonProfileSummaryVo(row.getProfileId(), row.getPreviousProfileId(), row.getFullName(),
            row.getDocumentTypeCode(), row.getDocumentNumber(), row.getGender(), row.getBirthDate(), row.getStatus(),
            row.getBindingUserId(), row.getBindingStatus(), row.getCreateTime());
    }

    private PersonProfileVersionVo version(VersionRow row) {
        return new PersonProfileVersionVo(row.getVersionId(), value(row.getVersionNo()), row.getSourceType(),
            row.getSourceId(), row.getFullName(), row.getDocumentTypeCode(), row.getDocumentNumber(), row.getGender(),
            row.getBirthDate(), row.getValidFrom(), row.getValidUntil(), row.getStatus(), row.getPublishedTime());
    }

    private PersonProfileBindingVo binding(BindingRow row) {
        return new PersonProfileBindingVo(row.getBindingId(), row.getUserId(), row.getStatus(),
            value(row.getBindingVersion()), row.getSourceType(), row.getSourceId(), row.getBoundTime(),
            row.getUnboundTime());
    }

    private PersonProfileSourceVo source(SourceRow row) {
        return new PersonProfileSourceVo(row.getSourceId(), row.getSourceType(), row.getOperatorUserId(),
            row.getReason(), row.getFieldSnapshotJson(), row.getOccurredTime());
    }

    private PersonProfileAuditVo audit(AuditRow row) {
        return new PersonProfileAuditVo(row.getAuditId(), row.getOperationType(), row.getOperatorUserId(),
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
            throw failure("PERSON_PROFILE_NOT_FOUND");
        }
        return row;
    }

    private ProfileRow requireWritable(ProfileRow row) {
        row = requireProfile(row);
        if ("REVOKED".equals(row.getStatus())) {
            throw failure("PERSON_PROFILE_REVOKED_READ_ONLY");
        }
        return row;
    }

    private ReviewRow requireReview(ReviewRow row) {
        if (row == null) {
            throw failure("PERSON_REVIEW_CONTEXT_NOT_FOUND");
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

    private void requireEligibleUser(long userId) {
        UserDTO user = users.selectById(userId);
        if (user == null || !"0".equals(user.getStatus()) || hasEffectiveBinding(userId)) {
            throw failure("PERSON_BINDING_TARGET_INELIGIBLE");
        }
    }

    private PersonIdentityFields fields(PersonAdminIdentityBo identity) {
        if (identity == null) {
            throw failure("PERSON_IDENTITY_REQUIRED");
        }
        PersonIdentityFields fields = PersonIdentityFields.normalize(new PersonApplicationSaveBo(identity.fullName(),
            identity.documentTypeCode(), identity.documentNumber(), identity.gender(), identity.birthDate(),
            identity.validFrom(), identity.validUntil(), 0));
        LocalDate today = LocalDate.now(clock);
        if (fields.fullName() == null || fields.documentTypeCode() == null || fields.documentNumber() == null
            || fields.gender() == null || fields.birthDate() == null || fields.birthDate().isAfter(today)
            || (fields.validFrom() != null && fields.validUntil() != null
            && fields.validFrom().isAfter(fields.validUntil()))) {
            throw failure("PERSON_IDENTITY_INVALID");
        }
        var rule = applications.findDocumentType(fields.documentTypeCode())
            .orElseThrow(() -> failure("PERSON_DOCUMENT_TYPE_UNAVAILABLE"));
        if (!Pattern.matches(rule.numberPattern(), fields.documentNumber())
            || (rule.validityRequired() && (fields.validFrom() == null || fields.validUntil() == null))
            || (fields.validFrom() != null && fields.validUntil() != null
            && (fields.validFrom().isAfter(today) || fields.validUntil().isBefore(today)))) {
            throw failure("PERSON_IDENTITY_INVALID");
        }
        return fields;
    }

    private MaterialOwnerKey owner(MaterialOwnerType type, long id) {
        return new MaterialOwnerKey(ProfileType.PERSON, type, id);
    }

    private long positive(long value, String category) {
        if (value <= 0) {
            throw failure(category);
        }
        return value;
    }

    private String reason(String value) {
        if (value == null || value.isBlank() || value.strip().length() > 500) {
            throw failure("PERSON_ADMIN_REASON_REQUIRED");
        }
        return value.strip();
    }

    private String upper(String value) {
        return value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
    }

    private PersonAdminException failure(String category) {
        return new PersonAdminException(category);
    }

    record ReviewData(long applicationId, long applicantUserId, String status, int submissionSeq,
                      int decisionVersion, int version, long submissionId, String fieldSnapshotJson,
                      Instant submittedTime) {
    }

    record DecisionState(long applicationId, long submissionId, int snapshotVersion, int decisionVersion) {
    }

    record CreateState(long profileId, long sourceId, PersonIdentityFields fields) {
    }

    record ReviseState(long profileId, long sourceId, int nextVersionNo, int profileVersion,
                       PersonIdentityFields fields) {
    }
}

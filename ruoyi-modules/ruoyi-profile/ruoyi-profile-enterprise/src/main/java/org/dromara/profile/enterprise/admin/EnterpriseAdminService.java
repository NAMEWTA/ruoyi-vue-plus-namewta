package org.dromara.profile.enterprise.admin;

import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import org.dromara.common.core.domain.PageResult;
import org.dromara.profile.api.domain.ProfileType;
import org.dromara.profile.api.ProfileService;
import org.dromara.profile.api.material.ProfileMaterialPort;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialAttachCommand;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerKey;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerType;
import org.dromara.profile.enterprise.admin.EnterpriseAdminContracts.*;
import org.dromara.profile.enterprise.application.EnterpriseApplicationRepository;
import org.dromara.profile.enterprise.application.EnterpriseDraftCommand;
import org.dromara.profile.enterprise.application.EnterpriseIdentityFields;
import org.dromara.profile.enterprise.application.EnterprisePublication;
import org.dromara.profile.enterprise.application.EnterpriseSubmission;
import org.dromara.system.api.UserService;
import org.dromara.system.api.domain.UserDTO;
import org.dromara.workflow.api.WorkflowService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class EnterpriseAdminService {

    private static final Set<String> DECISIONS = Set.of("APPROVED", "REJECTED");
    private static final Set<String> BINDING_ACTIONS = Set.of("SUSPEND", "RESUME", "UNBIND");

    private final EnterpriseAdminRepository repository;
    private final EnterpriseApplicationRepository applications;
    private final ProfileMaterialPort materials;
    private final WorkflowService workflow;
    private final UserService users;
    private final ProfileService profiles;
    private final Clock clock;

    @Autowired
    public EnterpriseAdminService(EnterpriseAdminRepository repository, EnterpriseApplicationRepository applications,
                                  ProfileMaterialPort materials,
                                  ObjectProvider<WorkflowService> workflowProvider, UserService users,
                                  ProfileService profiles) {
        this(repository, applications, materials, workflowProvider.getIfAvailable(), users, profiles,
            Clock.systemUTC());
    }

    EnterpriseAdminService(EnterpriseAdminRepository repository, EnterpriseApplicationRepository applications,
                           ProfileMaterialPort materials, WorkflowService workflow, UserService users,
                           ProfileService profiles) {
        this(repository, applications, materials, workflow, users, profiles, Clock.systemUTC());
    }

    EnterpriseAdminService(EnterpriseAdminRepository repository, EnterpriseApplicationRepository applications,
                       ProfileMaterialPort materials, WorkflowService workflow, UserService users,
                       ProfileService profiles, Clock clock) {
        this.repository = repository;
        this.applications = applications;
        this.materials = materials;
        this.workflow = workflow;
        this.users = users;
        this.profiles = profiles;
        this.clock = clock;
    }

    public PageResult<Summary> page(Query query) {
        return repository.page(query == null ? new Query(null, null, null, 1, 20) : query);
    }

    public List<AccountCandidate> eligibleUsers(String keyword) {
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
            .filter(user -> !repository.hasEffectiveBinding(user.getUserId()))
            .limit(20)
            .map(user -> new AccountCandidate(user.getUserId(), user.getUserName(), user.getNickName()))
            .toList();
    }

    public Detail detail(long profileId) {
        Detail detail = repository.detail(positive(profileId, "ENTERPRISE_PROFILE_INVALID"));
        List<ProfileMaterialPort.MaterialReferenceView> current = detail.versions().isEmpty()
            ? List.of() : materials.list(owner(MaterialOwnerType.VERSION,
            detail.versions().stream().filter(version -> "CURRENT".equals(version.status())).findFirst()
                .orElse(detail.versions().getFirst()).versionId()));
        return new Detail(detail.profile(), detail.versions(), detail.bindings(), detail.sources(), detail.audits(), current);
    }

    public ReviewContext review(long applicationId) {
        var data = repository.review(positive(applicationId, "ENTERPRISE_APPLICATION_INVALID"));
        var owner = owner(MaterialOwnerType.SUBMISSION, data.submissionId());
        return new ReviewContext(data.applicationId(), data.applicantUserId(), data.status(), data.submissionSeq(),
            data.decisionVersion(), data.version(), data.submissionId(), data.fieldSnapshotJson(),
            data.submittedTime(), materials.list(owner));
    }

    public org.dromara.system.api.OssService.OssAccessUrl reviewMaterial(long applicationId, long materialRefId) {
        return materials.accessUrl(owner(MaterialOwnerType.SUBMISSION, review(applicationId).submissionId()), materialRefId);
    }

    public org.dromara.system.api.OssService.OssAccessUrl material(long profileId, long materialRefId) {
        Detail detail = detail(profileId);
        Version current = detail.versions().stream().filter(version -> "CURRENT".equals(version.status()))
            .findFirst().orElseThrow(() -> failure("ENTERPRISE_PROFILE_VERSION_NOT_FOUND"));
        return materials.accessUrl(owner(MaterialOwnerType.VERSION, current.versionId()), materialRefId);
    }

    @DSTransactional
    public Result decide(long operatorId, long applicationId, DecisionCommand command) {
        String reason = reason(command == null ? null : command.reason());
        String decision = upper(command == null ? null : command.decision());
        if (!DECISIONS.contains(decision)) {
            throw failure("ENTERPRISE_ADMIN_DECISION_INVALID");
        }
        if (workflow == null) {
            throw failure("ENTERPRISE_ADMIN_WORKFLOW_UNAVAILABLE");
        }
        Instant now = clock.instant();
        var state = repository.beginDecision(positive(applicationId, "ENTERPRISE_APPLICATION_INVALID"), decision,
            positive(operatorId, "ENTERPRISE_OPERATOR_INVALID"), reason, now);
        try {
            workflow.terminateInstance(Long.toString(applicationId), reason);
        } catch (RuntimeException exception) {
            throw new EnterpriseAdminException("ENTERPRISE_ADMIN_WORKFLOW_TERMINATION_FAILED", exception);
        }
        if ("REJECTED".equals(decision)) {
            repository.finalizeRejected(state, operatorId, reason, now);
            return new Result("REJECTED", 0L, null, null, state.decisionVersion());
        }
        repository.resumeForApproval(state, operatorId);
        EnterpriseSubmission submission = applications.requireSubmission(applicationId, state.snapshotVersion());
        EnterprisePublication publication = applications.publishApproved(applicationId, state.snapshotVersion(), now);
        materials.snapshotImmutable(owner(MaterialOwnerType.SUBMISSION, submission.enterpriseSubmissionId()),
            owner(MaterialOwnerType.VERSION, publication.enterpriseVersionId()));
        repository.finalizeApproved(state, publication.enterpriseProfileId(), publication.enterpriseVersionId(), operatorId,
            reason, now);
        return new Result("APPROVED", publication.enterpriseProfileId(), publication.enterpriseVersionId(),
            publication.enterpriseBindingId(), state.decisionVersion());
    }

    @DSTransactional
    public Result create(long operatorId, CreateCommand command) {
        if (command == null) {
            throw failure("ENTERPRISE_ADMIN_CREATE_REQUIRED");
        }
        String reason = reason(command.reason());
        EnterpriseIdentityFields fields = fields(command.identity());
        Instant now = clock.instant();
        var state = repository.beginCreate(fields, positive(operatorId, "ENTERPRISE_OPERATOR_INVALID"), reason, now);
        List<MaterialInput> inputs = command.materials() == null ? List.of() : List.copyOf(command.materials());
        for (MaterialInput input : inputs) {
            materials.attach(new MaterialAttachCommand(owner(MaterialOwnerType.SOURCE, state.sourceId()),
                input.ossId(), input.materialNodeId()));
        }
        MaterialOwnerKey source = owner(MaterialOwnerType.SOURCE, state.sourceId());
        materials.validateRequired(source, fields.legalDocumentTypeCode(), Set.of("ALWAYS"));
        if (command.bindUserId() != null) {
            requireEligibleUser(command.bindUserId());
        }
        Result result = repository.completeCreate(state, command.bindUserId(), operatorId, reason, now);
        materials.snapshotImmutable(source, owner(MaterialOwnerType.VERSION, result.versionId()));
        return result;
    }

    @DSTransactional
    public Result revise(long operatorId, long profileId, ReviseCommand command) {
        if (command == null) {
            throw failure("ENTERPRISE_ADMIN_REVISION_REQUIRED");
        }
        String reason = reason(command.reason());
        EnterpriseIdentityFields fields = fields(command.identity());
        Instant now = clock.instant();
        var state = repository.beginRevise(positive(profileId, "ENTERPRISE_PROFILE_INVALID"), fields,
            command.expectedVersion(), positive(operatorId, "ENTERPRISE_OPERATOR_INVALID"), reason, now);
        MaterialOwnerKey source = owner(MaterialOwnerType.SOURCE, state.sourceId());
        materials.validateRequired(source, fields.legalDocumentTypeCode(), Set.of("ALWAYS"));
        Result result = repository.completeRevise(state, operatorId, reason, now);
        materials.snapshotImmutable(source, owner(MaterialOwnerType.VERSION, result.versionId()));
        return result;
    }

    @DSTransactional
    public Result manageBinding(long operatorId, long profileId, BindingCommand command) {
        String action = upper(command == null ? null : command.action());
        if (!BINDING_ACTIONS.contains(action)) {
            throw failure("ENTERPRISE_BINDING_ACTION_INVALID");
        }
        return repository.manageBinding(positive(profileId, "ENTERPRISE_PROFILE_INVALID"), action,
            command.expectedBindingVersion(), positive(operatorId, "ENTERPRISE_OPERATOR_INVALID"),
            reason(command.reason()), clock.instant());
    }

    @DSTransactional
    public Result assign(long operatorId, long profileId, AssignCommand command) {
        if (command == null || command.userId() == null) {
            throw failure("ENTERPRISE_BINDING_TARGET_REQUIRED");
        }
        requireEligibleUser(command.userId());
        return repository.assign(positive(profileId, "ENTERPRISE_PROFILE_INVALID"), command.userId(),
            positive(operatorId, "ENTERPRISE_OPERATOR_INVALID"), reason(command.reason()), clock.instant());
    }

    @DSTransactional
    public Result revoke(long operatorId, long profileId, RevokeCommand command) {
        if (command == null) {
            throw failure("ENTERPRISE_REVOKE_REQUIRED");
        }
        return repository.revoke(positive(profileId, "ENTERPRISE_PROFILE_INVALID"), command.expectedVersion(),
            positive(operatorId, "ENTERPRISE_OPERATOR_INVALID"), reason(command.reason()), clock.instant());
    }

    private void requireEligibleUser(long userId) {
        UserDTO user = users.selectById(userId);
        var profile = profiles.findByUserId(userId);
        if (user == null || !"0".equals(user.getStatus()) || repository.hasEffectiveBinding(userId)
            || profile == null || profile.person() == null) {
            throw failure("ENTERPRISE_BINDING_TARGET_INELIGIBLE");
        }
    }

    private EnterpriseIdentityFields fields(IdentityCommand identity) {
        if (identity == null) {
            throw failure("ENTERPRISE_IDENTITY_REQUIRED");
        }
        EnterpriseIdentityFields fields = EnterpriseIdentityFields.normalize(new EnterpriseDraftCommand(
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

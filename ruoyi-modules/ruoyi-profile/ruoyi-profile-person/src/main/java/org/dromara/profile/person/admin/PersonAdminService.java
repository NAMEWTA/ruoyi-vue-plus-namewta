package org.dromara.profile.person.admin;

import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import org.dromara.common.core.domain.PageResult;
import org.dromara.profile.api.domain.ProfileType;
import org.dromara.profile.api.material.ProfileMaterialPort;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialAttachCommand;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerKey;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerType;
import org.dromara.profile.person.admin.PersonAdminContracts.*;
import org.dromara.profile.person.application.PersonApplicationRepository;
import org.dromara.profile.person.application.PersonDraftCommand;
import org.dromara.profile.person.application.PersonIdentityFields;
import org.dromara.profile.person.application.PersonPublication;
import org.dromara.profile.person.application.PersonSubmission;
import org.dromara.system.api.UserService;
import org.dromara.system.api.domain.UserDTO;
import org.dromara.workflow.api.WorkflowService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class PersonAdminService {

    private static final Set<String> DECISIONS = Set.of("APPROVED", "REJECTED");
    private static final Set<String> BINDING_ACTIONS = Set.of("SUSPEND", "RESUME", "UNBIND");

    private final PersonAdminRepository repository;
    private final PersonApplicationRepository applications;
    private final ProfileMaterialPort materials;
    private final WorkflowService workflow;
    private final UserService users;
    private final Clock clock;

    public PersonAdminService(PersonAdminRepository repository, PersonApplicationRepository applications,
                              ProfileMaterialPort materials, WorkflowService workflow, UserService users) {
        this(repository, applications, materials, workflow, users, Clock.systemUTC());
    }

    PersonAdminService(PersonAdminRepository repository, PersonApplicationRepository applications,
                       ProfileMaterialPort materials, WorkflowService workflow, UserService users, Clock clock) {
        this.repository = repository;
        this.applications = applications;
        this.materials = materials;
        this.workflow = workflow;
        this.users = users;
        this.clock = clock;
    }

    public PageResult<Summary> page(Query query) {
        return repository.page(query == null ? new Query(null, null, null, 1, 20) : query);
    }

    public Detail detail(long profileId) {
        Detail detail = repository.detail(positive(profileId, "PERSON_PROFILE_INVALID"));
        List<ProfileMaterialPort.MaterialReferenceView> current = detail.versions().isEmpty()
            ? List.of() : materials.list(owner(MaterialOwnerType.VERSION,
            detail.versions().stream().filter(version -> "CURRENT".equals(version.status())).findFirst()
                .orElse(detail.versions().getFirst()).versionId()));
        return new Detail(detail.profile(), detail.versions(), detail.bindings(), detail.sources(), detail.audits(), current);
    }

    public ReviewContext review(long applicationId) {
        var data = repository.review(positive(applicationId, "PERSON_APPLICATION_INVALID"));
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
            .findFirst().orElseThrow(() -> failure("PERSON_PROFILE_VERSION_NOT_FOUND"));
        return materials.accessUrl(owner(MaterialOwnerType.VERSION, current.versionId()), materialRefId);
    }

    @DSTransactional
    public Result decide(long operatorId, long applicationId, DecisionCommand command) {
        String reason = reason(command == null ? null : command.reason());
        String decision = upper(command == null ? null : command.decision());
        if (!DECISIONS.contains(decision)) {
            throw failure("PERSON_ADMIN_DECISION_INVALID");
        }
        Instant now = clock.instant();
        var state = repository.beginDecision(positive(applicationId, "PERSON_APPLICATION_INVALID"), decision,
            positive(operatorId, "PERSON_OPERATOR_INVALID"), reason, now);
        try {
            workflow.terminateInstance(Long.toString(applicationId), reason);
        } catch (RuntimeException exception) {
            throw new PersonAdminException("PERSON_ADMIN_WORKFLOW_TERMINATION_FAILED", exception);
        }
        if ("REJECTED".equals(decision)) {
            repository.finalizeRejected(state, operatorId, reason, now);
            return new Result("REJECTED", 0L, null, null, state.decisionVersion());
        }
        repository.resumeForApproval(state, operatorId);
        PersonSubmission submission = applications.requireSubmission(applicationId, state.snapshotVersion());
        PersonPublication publication = applications.publishApproved(applicationId, state.snapshotVersion(), now);
        materials.snapshotImmutable(owner(MaterialOwnerType.SUBMISSION, submission.personSubmissionId()),
            owner(MaterialOwnerType.VERSION, publication.personVersionId()));
        repository.finalizeApproved(state, publication.personProfileId(), publication.personVersionId(), operatorId,
            reason, now);
        return new Result("APPROVED", publication.personProfileId(), publication.personVersionId(),
            publication.personBindingId(), state.decisionVersion());
    }

    @DSTransactional
    public Result create(long operatorId, CreateCommand command) {
        if (command == null) {
            throw failure("PERSON_ADMIN_CREATE_REQUIRED");
        }
        String reason = reason(command.reason());
        PersonIdentityFields fields = fields(command.identity());
        Instant now = clock.instant();
        var state = repository.beginCreate(fields, positive(operatorId, "PERSON_OPERATOR_INVALID"), reason, now);
        List<MaterialInput> inputs = command.materials() == null ? List.of() : List.copyOf(command.materials());
        for (MaterialInput input : inputs) {
            materials.attach(new MaterialAttachCommand(owner(MaterialOwnerType.SOURCE, state.sourceId()),
                input.ossId(), input.materialNodeId()));
        }
        MaterialOwnerKey source = owner(MaterialOwnerType.SOURCE, state.sourceId());
        materials.validateRequired(source, fields.documentTypeCode(), Set.of("ALWAYS"));
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
            throw failure("PERSON_ADMIN_REVISION_REQUIRED");
        }
        String reason = reason(command.reason());
        PersonIdentityFields fields = fields(command.identity());
        Instant now = clock.instant();
        var state = repository.beginRevise(positive(profileId, "PERSON_PROFILE_INVALID"), fields,
            command.expectedVersion(), positive(operatorId, "PERSON_OPERATOR_INVALID"), reason, now);
        MaterialOwnerKey source = owner(MaterialOwnerType.SOURCE, state.sourceId());
        materials.validateRequired(source, fields.documentTypeCode(), Set.of("ALWAYS"));
        Result result = repository.completeRevise(state, operatorId, reason, now);
        materials.snapshotImmutable(source, owner(MaterialOwnerType.VERSION, result.versionId()));
        return result;
    }

    @DSTransactional
    public Result manageBinding(long operatorId, long profileId, BindingCommand command) {
        String action = upper(command == null ? null : command.action());
        if (!BINDING_ACTIONS.contains(action)) {
            throw failure("PERSON_BINDING_ACTION_INVALID");
        }
        return repository.manageBinding(positive(profileId, "PERSON_PROFILE_INVALID"), action,
            command.expectedBindingVersion(), positive(operatorId, "PERSON_OPERATOR_INVALID"),
            reason(command.reason()), clock.instant());
    }

    @DSTransactional
    public Result assign(long operatorId, long profileId, AssignCommand command) {
        if (command == null || command.userId() == null) {
            throw failure("PERSON_BINDING_TARGET_REQUIRED");
        }
        requireEligibleUser(command.userId());
        return repository.assign(positive(profileId, "PERSON_PROFILE_INVALID"), command.userId(),
            positive(operatorId, "PERSON_OPERATOR_INVALID"), reason(command.reason()), clock.instant());
    }

    @DSTransactional
    public Result revoke(long operatorId, long profileId, RevokeCommand command) {
        if (command == null) {
            throw failure("PERSON_REVOKE_REQUIRED");
        }
        return repository.revoke(positive(profileId, "PERSON_PROFILE_INVALID"), command.expectedVersion(),
            positive(operatorId, "PERSON_OPERATOR_INVALID"), reason(command.reason()), clock.instant());
    }

    private void requireEligibleUser(long userId) {
        UserDTO user = users.selectById(userId);
        if (user == null || !"0".equals(user.getStatus()) || repository.hasEffectiveBinding(userId)) {
            throw failure("PERSON_BINDING_TARGET_INELIGIBLE");
        }
    }

    private PersonIdentityFields fields(IdentityCommand identity) {
        if (identity == null) {
            throw failure("PERSON_IDENTITY_REQUIRED");
        }
        PersonIdentityFields fields = PersonIdentityFields.normalize(new PersonDraftCommand(identity.fullName(),
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
}

package org.dromara.profile.person.admin;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.dromara.common.core.domain.PageResult;
import org.dromara.profile.person.admin.PersonAdminContracts.*;
import org.dromara.profile.person.admin.PersonAdminRows.*;
import org.dromara.profile.person.application.PersonIdentityFields;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Repository
public class MybatisPersonAdminRepository implements PersonAdminRepository {

    private final PersonAdminMapper mapper;
    private final JsonMapper jsonMapper;

    public MybatisPersonAdminRepository(PersonAdminMapper mapper, JsonMapper jsonMapper) {
        this.mapper = mapper;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public PageResult<Summary> page(Query query) {
        int page = Math.max(1, query.pageNum());
        int size = query.pageSize() <= 0 ? 20 : Math.min(query.pageSize(), 200);
        String status = upper(query.status());
        if (!status.isEmpty() && !List.of("ACTIVE", "REVOKED").contains(status)) {
            throw failure("PERSON_QUERY_STATUS_INVALID");
        }
        String fullName = text(query.fullName());
        String documentNumber = upper(query.documentNumber());
        long total = mapper.countProfiles(fullName, documentNumber, status);
        List<Summary> rows = mapper.selectProfiles(fullName, documentNumber, status, size, (page - 1) * size)
            .stream().map(this::summary).toList();
        return PageResult.build(rows, total);
    }

    @Override
    public Detail detail(long profileId) {
        ProfileRow profile = requireProfile(mapper.selectProfile(profileId));
        List<Version> versions = mapper.selectVersions(profileId).stream().map(this::version).toList();
        return new Detail(summary(profile), versions, mapper.selectBindings(profileId).stream().map(this::binding).toList(),
            mapper.selectSources(profileId).stream().map(this::source).toList(),
            mapper.selectAudits(profileId).stream().map(this::audit).toList(), List.of());
    }

    @Override
    public ReviewData review(long applicationId) {
        return reviewData(requireReview(mapper.selectReview(applicationId)));
    }

    @Override
    public DecisionState beginDecision(long applicationId, String decision, long operatorId,
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

    @Override
    public void resumeForApproval(DecisionState state, long operatorId) {
        changed(mapper.resumeWaiting(state.applicationId(), state.decisionVersion(), operatorId),
            "PERSON_ADMIN_DECISION_CONFLICT");
    }

    @Override
    public void finalizeApproved(DecisionState state, long profileId, long versionId, long operatorId,
                                 String reason, Instant now) {
        changed(mapper.markApproved(state.applicationId(), operatorId, reason, now),
            "PERSON_ADMIN_DECISION_CONFLICT");
        changed(mapper.finalizeDecision(state.applicationId(), state.decisionVersion(), operatorId, now),
            "PERSON_ADMIN_DECISION_CONFLICT");
        audit(profileId, state.applicationId(), null, "ADMIN_APPROVE", operatorId, "profile:person:override",
            reason, "OVERRIDE_PENDING", "FINISH", now);
    }

    @Override
    public void finalizeRejected(DecisionState state, long operatorId, String reason, Instant now) {
        changed(mapper.markRejected(state.applicationId(), state.decisionVersion(), operatorId, reason, now),
            "PERSON_ADMIN_DECISION_CONFLICT");
        changed(mapper.finalizeDecision(state.applicationId(), state.decisionVersion(), operatorId, now),
            "PERSON_ADMIN_DECISION_CONFLICT");
        audit(null, state.applicationId(), null, "ADMIN_REJECT", operatorId, "profile:person:override",
            reason, "OVERRIDE_PENDING", "INVALID", now);
    }

    @Override
    public CreateState beginCreate(PersonIdentityFields fields, long operatorId, String reason, Instant now) {
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

    @Override
    public Result completeCreate(CreateState state, Long bindUserId, long operatorId, String reason, Instant now) {
        long versionId = IdWorker.getId();
        try {
            insertVersion(versionId, state.profileId(), 1, "ADMIN_CREATE", state.sourceId(), state.fields(),
                operatorId, now);
            changed(mapper.updateProfileVersion(state.profileId(), versionId, state.fields().fullName(),
                state.fields().documentTypeCode(), state.fields().documentNumber(), state.fields().identityKey(),
                state.fields().gender(), state.fields().birthDate(), state.fields().validFrom(),
                state.fields().validUntil(), 0, operatorId, now), "PERSON_ADMIN_CREATE_CONFLICT");
            Long bindingId = bindUserId == null ? null
                : insertBinding(state.profileId(), bindUserId, "ADMIN_CREATE", state.sourceId(), operatorId, reason, now);
            audit(state.profileId(), null, bindingId, "ADMIN_CREATE", operatorId, "profile:person:override",
                reason, null, "ACTIVE", now);
            return new Result("ACTIVE", state.profileId(), versionId, bindingId, 1);
        } catch (DuplicateKeyException exception) {
            throw new PersonAdminException("PERSON_ADMIN_CREATE_CONFLICT", exception);
        }
    }

    @Override
    public ReviseState beginRevise(long profileId, PersonIdentityFields fields, int expectedVersion,
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

    @Override
    public Result completeRevise(ReviseState state, long operatorId, String reason, Instant now) {
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
        return new Result("ACTIVE", state.profileId(), versionId, null, state.profileVersion() + 1);
    }

    @Override
    public Result manageBinding(long profileId, String action, int expectedBindingVersion, long operatorId,
                                String reason, Instant now) {
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
        event(binding.getBindingId(), profileId, binding.getUserId(), target, nextVersion, null, reason, operatorId, now);
        audit(profileId, null, binding.getBindingId(), "BINDING_" + action, operatorId,
            "profile:person:manage", reason, source, target, now);
        return new Result(target, profileId, null, binding.getBindingId(), nextVersion);
    }

    @Override
    public Result assign(long profileId, long userId, long operatorId, String reason, Instant now) {
        requireWritable(mapper.lockProfile(profileId));
        if (mapper.lockEffectiveBinding(profileId) != null || mapper.countEffectiveBindingByUser(userId) != 0) {
            throw failure("PERSON_BINDING_TARGET_INELIGIBLE");
        }
        try {
            long bindingId = insertBinding(profileId, userId, "ADMIN_OVERRIDE", null, operatorId, reason, now);
            audit(profileId, null, bindingId, "BINDING_ASSIGN", operatorId, "profile:person:override",
                reason, null, "ACTIVE", now);
            return new Result("ACTIVE", profileId, null, bindingId, 1);
        } catch (DuplicateKeyException exception) {
            throw new PersonAdminException("PERSON_BINDING_TARGET_INELIGIBLE", exception);
        }
    }

    @Override
    public Result revoke(long profileId, int expectedVersion, long operatorId, String reason, Instant now) {
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
            changed(mapper.updateBinding(bindingId, binding.getStatus(), "UNBOUND", value(binding.getBindingVersion()),
                operatorId, now), "PERSON_BINDING_VERSION_CONFLICT");
            event(bindingId, profileId, binding.getUserId(), "UNBOUND", next, null, reason, operatorId, now);
        }
        audit(profileId, null, bindingId, "REVOKE", operatorId, "profile:person:override", reason,
            "ACTIVE", "REVOKED", now);
        return new Result("REVOKED", profileId, profile.getCurrentVersionId(), bindingId, expectedVersion + 1);
    }

    @Override
    public boolean hasEffectiveBinding(long userId) {
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

    private Summary summary(ProfileRow row) {
        return new Summary(row.getProfileId(), row.getPreviousProfileId(), row.getFullName(),
            row.getDocumentTypeCode(), row.getDocumentNumber(), row.getGender(), row.getBirthDate(), row.getStatus(),
            row.getBindingUserId(), row.getBindingStatus(), row.getCreateTime());
    }

    private Version version(VersionRow row) {
        return new Version(row.getVersionId(), value(row.getVersionNo()), row.getSourceType(), row.getSourceId(),
            row.getFullName(), row.getDocumentTypeCode(), row.getDocumentNumber(), row.getGender(), row.getBirthDate(),
            row.getValidFrom(), row.getValidUntil(), row.getStatus(), row.getPublishedTime());
    }

    private Binding binding(BindingRow row) {
        return new Binding(row.getBindingId(), row.getUserId(), row.getStatus(), value(row.getBindingVersion()),
            row.getSourceType(), row.getSourceId(), row.getBoundTime(), row.getUnboundTime());
    }

    private Source source(SourceRow row) {
        return new Source(row.getSourceId(), row.getSourceType(), row.getOperatorUserId(), row.getReason(),
            row.getFieldSnapshotJson(), row.getOccurredTime());
    }

    private Audit audit(AuditRow row) {
        return new Audit(row.getAuditId(), row.getOperationType(), row.getOperatorUserId(), row.getCapability(),
            row.getReason(), row.getBeforeStatus(), row.getAfterStatus(), row.getResult(), row.getFailureCategory(),
            row.getOccurredTime());
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

    private String upper(String value) {
        String text = text(value);
        return text == null ? "" : text.toUpperCase(Locale.ROOT);
    }

    private PersonAdminException failure(String category) {
        return new PersonAdminException(category);
    }
}

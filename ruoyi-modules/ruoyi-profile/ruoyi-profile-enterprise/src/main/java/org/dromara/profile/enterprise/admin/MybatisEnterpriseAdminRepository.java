package org.dromara.profile.enterprise.admin;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.dromara.common.core.domain.PageResult;
import org.dromara.profile.enterprise.admin.EnterpriseAdminContracts.*;
import org.dromara.profile.enterprise.admin.EnterpriseAdminRows.*;
import org.dromara.profile.enterprise.application.EnterpriseIdentityFields;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Repository
public class MybatisEnterpriseAdminRepository implements EnterpriseAdminRepository {

    private final EnterpriseAdminMapper mapper;
    private final JsonMapper jsonMapper;

    public MybatisEnterpriseAdminRepository(EnterpriseAdminMapper mapper, JsonMapper jsonMapper) {
        this.mapper = mapper;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public PageResult<Summary> page(Query query) {
        int page = Math.max(1, query.pageNum());
        int size = query.pageSize() <= 0 ? 20 : Math.min(query.pageSize(), 200);
        String status = upper(query.status());
        if (!status.isEmpty() && !List.of("ACTIVE", "REVOKED").contains(status)) {
            throw failure("ENTERPRISE_QUERY_STATUS_INVALID");
        }
        String enterpriseName = text(query.enterpriseName());
        String unifiedCreditCode = upper(query.unifiedCreditCode());
        long total = mapper.countProfiles(enterpriseName, unifiedCreditCode, status);
        List<Summary> rows = mapper.selectProfiles(enterpriseName, unifiedCreditCode, status, size, (page - 1) * size)
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
            value(row.getVersion()), operatorId, now), "ENTERPRISE_ADMIN_DECISION_CONFLICT");
        changed(mapper.insertDecision(IdWorker.getId(), applicationId, row.getSubmissionId(), nextDecision,
            decision, operatorId, reason, now), "ENTERPRISE_ADMIN_DECISION_CONFLICT");
        audit(null, applicationId, null, "ADMIN_DECISION_PENDING", operatorId, "profile:enterprise:override",
            reason, "WAITING", "OVERRIDE_PENDING", now);
        return new DecisionState(applicationId, row.getSubmissionId(), value(row.getSubmissionSeq()), nextDecision);
    }

    @Override
    public void resumeForApproval(DecisionState state, long operatorId) {
        changed(mapper.resumeWaiting(state.applicationId(), state.decisionVersion(), operatorId),
            "ENTERPRISE_ADMIN_DECISION_CONFLICT");
    }

    @Override
    public void finalizeApproved(DecisionState state, long profileId, long versionId, long operatorId,
                                 String reason, Instant now) {
        changed(mapper.markApproved(state.applicationId(), operatorId, reason, now),
            "ENTERPRISE_ADMIN_DECISION_CONFLICT");
        changed(mapper.finalizeDecision(state.applicationId(), state.decisionVersion(), operatorId, now),
            "ENTERPRISE_ADMIN_DECISION_CONFLICT");
        audit(profileId, state.applicationId(), null, "ADMIN_APPROVE", operatorId, "profile:enterprise:override",
            reason, "OVERRIDE_PENDING", "FINISH", now);
    }

    @Override
    public void finalizeRejected(DecisionState state, long operatorId, String reason, Instant now) {
        changed(mapper.markRejected(state.applicationId(), state.decisionVersion(), operatorId, reason, now),
            "ENTERPRISE_ADMIN_DECISION_CONFLICT");
        changed(mapper.finalizeDecision(state.applicationId(), state.decisionVersion(), operatorId, now),
            "ENTERPRISE_ADMIN_DECISION_CONFLICT");
        audit(null, state.applicationId(), null, "ADMIN_REJECT", operatorId, "profile:enterprise:override",
            reason, "OVERRIDE_PENDING", "INVALID", now);
    }

    @Override
    public CreateState beginCreate(EnterpriseIdentityFields fields, long operatorId, String reason, Instant now) {
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

    @Override
    public Result completeCreate(CreateState state, Long bindUserId, long operatorId, String reason, Instant now) {
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
                : insertBinding(state.profileId(), bindUserId, "ADMIN_CREATE", state.sourceId(), operatorId, reason, now);
            audit(state.profileId(), null, bindingId, "ADMIN_CREATE", operatorId, "profile:enterprise:override",
                reason, null, "ACTIVE", now);
            return new Result("ACTIVE", state.profileId(), versionId, bindingId, 1);
        } catch (DuplicateKeyException exception) {
            throw new EnterpriseAdminException("ENTERPRISE_ADMIN_CREATE_CONFLICT", exception);
        }
    }

    @Override
    public ReviseState beginRevise(long profileId, EnterpriseIdentityFields fields, int expectedVersion,
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
        return new ReviseState(profileId, sourceId, value(current.getVersionNo()) + 1,
            expectedVersion, fields);
    }

    @Override
    public Result completeRevise(ReviseState state, long operatorId, String reason, Instant now) {
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
        return new Result("ACTIVE", state.profileId(), versionId, null, state.profileVersion() + 1);
    }

    @Override
    public Result manageBinding(long profileId, String action, int expectedBindingVersion, long operatorId,
                                String reason, Instant now) {
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
        event(binding.getBindingId(), profileId, binding.getUserId(), target, nextVersion, null, reason, operatorId, now);
        audit(profileId, null, binding.getBindingId(), "BINDING_" + action, operatorId,
            "profile:enterprise:manage", reason, source, target, now);
        return new Result(target, profileId, null, binding.getBindingId(), nextVersion);
    }

    @Override
    public Result assign(long profileId, long userId, long operatorId, String reason, Instant now) {
        requireWritable(mapper.lockProfile(profileId));
        if (mapper.lockEffectiveBinding(profileId) != null || mapper.countEffectiveBindingByUser(userId) != 0) {
            throw failure("ENTERPRISE_BINDING_TARGET_INELIGIBLE");
        }
        try {
            long bindingId = insertBinding(profileId, userId, "ADMIN_OVERRIDE", null, operatorId, reason, now);
            audit(profileId, null, bindingId, "BINDING_ASSIGN", operatorId, "profile:enterprise:override",
                reason, null, "ACTIVE", now);
            return new Result("ACTIVE", profileId, null, bindingId, 1);
        } catch (DuplicateKeyException exception) {
            throw new EnterpriseAdminException("ENTERPRISE_BINDING_TARGET_INELIGIBLE", exception);
        }
    }

    @Override
    public Result revoke(long profileId, int expectedVersion, long operatorId, String reason, Instant now) {
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
        return new Result("REVOKED", profileId, profile.getCurrentVersionId(), bindingId, expectedVersion + 1);
    }

    @Override
    public boolean hasEffectiveBinding(long userId) {
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
            jsonMapper.writeValueAsString(fields), now),
            "ENTERPRISE_ADMIN_SOURCE_CONFLICT");
    }

    private void insertVersion(long versionId, long profileId, int versionNo, String sourceType, long sourceId,
                               EnterpriseIdentityFields fields, long operatorId, Instant now) {
        changed(mapper.insertVersion(versionId, profileId, versionNo, sourceType, sourceId, fields.enterpriseName(),
            fields.unifiedCreditCode(), fields.enterpriseType(), fields.legalRepresentativeName(),
            fields.legalDocumentTypeCode(), fields.legalDocumentNumber(), fields.establishedDate(),
            fields.businessTermFrom(), fields.businessTermUntil(), fields.registeredAddress(),
            fields.businessScope(), fields.contactName(), fields.contactPhone(), fields.email(),
            fields.registeredCapital(), fields.industryCode(), fields.website(), operatorId, now),
            "ENTERPRISE_PROFILE_VERSION_CONFLICT");
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

    private Summary summary(ProfileRow row) {
        return new Summary(row.getProfileId(), row.getPreviousProfileId(), row.getEnterpriseName(),
            row.getUnifiedCreditCode(), row.getEnterpriseType(), row.getLegalRepresentativeName(), row.getStatus(),
            row.getBindingUserId(), row.getBindingStatus(), row.getCreateTime());
    }

    private Version version(VersionRow row) {
        return new Version(row.getVersionId(), value(row.getVersionNo()), row.getSourceType(), row.getSourceId(),
            row.getEnterpriseName(), row.getUnifiedCreditCode(), row.getEnterpriseType(),
            row.getLegalRepresentativeName(), row.getLegalDocumentTypeCode(), row.getLegalDocumentNumber(),
            row.getEstablishedDate(), row.getBusinessTermFrom(), row.getBusinessTermUntil(),
            row.getRegisteredAddress(), row.getBusinessScope(), row.getContactName(), row.getContactPhone(),
            row.getEmail(), row.getRegisteredCapital(), row.getIndustryCode(), row.getWebsite(),
            row.getStatus(), row.getPublishedTime());
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

    private String upper(String value) {
        String text = text(value);
        return text == null ? "" : text.toUpperCase(Locale.ROOT);
    }

    private EnterpriseAdminException failure(String category) {
        return new EnterpriseAdminException(category);
    }
}

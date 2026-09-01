package org.dromara.profile.enterprise.verification;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.dromara.profile.enterprise.verification.mapper.EnterpriseVerificationAttemptMapper;
import org.dromara.profile.enterprise.verification.persistence.EnterpriseVerificationApplicationRow;
import org.dromara.profile.enterprise.verification.persistence.EnterpriseVerificationAttemptRow;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MybatisEnterpriseVerificationAttemptRepository implements EnterpriseVerificationAttemptRepository {

    private final EnterpriseVerificationAttemptMapper mapper;
    private final EnterpriseVerificationEvidenceCodec evidenceCodec;

    public MybatisEnterpriseVerificationAttemptRepository(EnterpriseVerificationAttemptMapper mapper,
                                                           EnterpriseVerificationEvidenceCodec evidenceCodec) {
        this.mapper = mapper;
        this.evidenceCodec = evidenceCodec;
    }

    @Override
    public EnterpriseApplicationVerificationState lockApplication(long applicationId) {
        EnterpriseVerificationApplicationRow row = mapper.lockApplication(applicationId);
        if (row == null) {
            throw new EnterpriseVerificationException(
                EnterpriseVerificationFailureCategory.APPLICATION_NOT_FOUND,
                "Enterprise verification application or current submission was not found");
        }
        return new EnterpriseApplicationVerificationState(
            row.getApplicationId(), row.getSubmissionId(), row.getProviderCode(), row.getStatus());
    }

    @Override
    public int nextAttemptNo(long applicationId) {
        return mapper.nextAttemptNo(applicationId);
    }

    @Override
    public EnterpriseVerificationAttempt append(EnterpriseVerificationAttempt attempt) {
        EnterpriseVerificationAttemptRow row = toRow(attempt);
        row.setVerificationAttemptId(IdWorker.getId());
        try {
            if (mapper.insertAttempt(row) != 1) {
                throw providerFailure("Enterprise verification attempt could not be appended");
            }
        } catch (DuplicateKeyException failure) {
            throw new EnterpriseVerificationException(
                EnterpriseVerificationFailureCategory.PROVIDER_FAILURE,
                "Enterprise verification attempt conflicts with an existing provider request",
                failure);
        }
        return toDomain(row);
    }

    @Override
    public Optional<EnterpriseVerificationAttempt> lockByProviderRequest(String providerCode,
                                                                          String providerRequestId) {
        return Optional.ofNullable(mapper.lockByProviderRequest(providerCode, providerRequestId))
            .map(this::toDomain);
    }

    @Override
    public void complete(long verificationAttemptId, EnterpriseVerifiedCallback callback) {
        String storedEvidence = evidenceCodec.encode(
            callback.callbackDigest(), callback.providerEvidenceJson());
        int updated = mapper.completeAttempt(
            verificationAttemptId,
            callback.status().name(),
            callback.normalizedResultJson(),
            storedEvidence,
            callback.errorCode(),
            callback.completedAt());
        if (updated != 1) {
            throw providerFailure("Enterprise verification attempt completion lost its pending fence");
        }
    }

    @Override
    public void appendSecurityAudit(EnterpriseVerificationSecurityAudit audit) {
        String result = audit.category() == EnterpriseVerificationFailureCategory.LATE_CALLBACK
            ? "IGNORED"
            : "FAILED";
        if (mapper.insertSecurityAudit(
            IdWorker.getId(), audit.applicationId(), result, audit.category().name(), audit.occurredAt()) != 1) {
            throw providerFailure("Enterprise verification security audit could not be appended");
        }
    }

    private EnterpriseVerificationAttemptRow toRow(EnterpriseVerificationAttempt attempt) {
        EnterpriseVerificationAttemptRow row = new EnterpriseVerificationAttemptRow();
        row.setVerificationAttemptId(attempt.verificationAttemptId());
        row.setApplicationId(attempt.applicationId());
        row.setSubmissionId(attempt.submissionId());
        row.setProviderCode(attempt.providerCode());
        row.setProviderRequestId(attempt.providerRequestId());
        row.setRequestFingerprint(attempt.requestFingerprint());
        row.setAttemptNo(attempt.attemptNo());
        row.setStatus(attempt.status().name());
        row.setNormalizedResultJson(attempt.normalizedResultJson());
        row.setProviderEvidenceJson(evidenceCodec.encode(
            attempt.callbackDigest(), attempt.providerEvidenceJson()));
        row.setErrorCode(attempt.errorCode());
        row.setCompletedTime(attempt.completedAt());
        return row;
    }

    private EnterpriseVerificationAttempt toDomain(EnterpriseVerificationAttemptRow row) {
        EnterpriseVerificationEvidenceCodec.DecodedEvidence evidence =
            evidenceCodec.decode(row.getProviderEvidenceJson());
        return new EnterpriseVerificationAttempt(
            row.getVerificationAttemptId(),
            row.getApplicationId(),
            row.getSubmissionId(),
            row.getProviderCode(),
            row.getProviderRequestId(),
            row.getRequestFingerprint(),
            evidence.callbackDigest(),
            row.getAttemptNo(),
            EnterpriseProviderAttemptStatus.valueOf(row.getStatus()),
            row.getNormalizedResultJson(),
            evidence.providerEvidenceJson(),
            row.getErrorCode(),
            row.getCompletedTime());
    }

    private EnterpriseVerificationException providerFailure(String message) {
        return new EnterpriseVerificationException(EnterpriseVerificationFailureCategory.PROVIDER_FAILURE, message);
    }
}

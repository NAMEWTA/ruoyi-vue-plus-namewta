package org.dromara.profile.person.verification;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.dromara.profile.person.verification.mapper.PersonVerificationAttemptMapper;
import org.dromara.profile.person.verification.persistence.PersonVerificationApplicationRow;
import org.dromara.profile.person.verification.persistence.PersonVerificationAttemptRow;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MybatisPersonVerificationAttemptRepository implements PersonVerificationAttemptRepository {

    private final PersonVerificationAttemptMapper mapper;
    private final PersonVerificationEvidenceCodec evidenceCodec;

    public MybatisPersonVerificationAttemptRepository(PersonVerificationAttemptMapper mapper,
                                                       PersonVerificationEvidenceCodec evidenceCodec) {
        this.mapper = mapper;
        this.evidenceCodec = evidenceCodec;
    }

    @Override
    public PersonApplicationVerificationState lockApplication(long applicationId) {
        PersonVerificationApplicationRow row = mapper.lockApplication(applicationId);
        if (row == null) {
            throw new PersonVerificationException(
                PersonVerificationFailureCategory.APPLICATION_NOT_FOUND,
                "Person verification application or current submission was not found");
        }
        return new PersonApplicationVerificationState(
            row.getApplicationId(), row.getSubmissionId(), row.getProviderCode(), row.getStatus());
    }

    @Override
    public int nextAttemptNo(long applicationId) {
        return mapper.nextAttemptNo(applicationId);
    }

    @Override
    public PersonVerificationAttempt append(PersonVerificationAttempt attempt) {
        PersonVerificationAttemptRow row = toRow(attempt);
        row.setVerificationAttemptId(IdWorker.getId());
        try {
            if (mapper.insertAttempt(row) != 1) {
                throw providerFailure("Person verification attempt could not be appended");
            }
        } catch (DuplicateKeyException failure) {
            throw new PersonVerificationException(
                PersonVerificationFailureCategory.PROVIDER_FAILURE,
                "Person verification attempt conflicts with an existing provider request",
                failure);
        }
        return toDomain(row);
    }

    @Override
    public Optional<PersonVerificationAttempt> lockByProviderRequest(String providerCode,
                                                                      String providerRequestId) {
        return Optional.ofNullable(mapper.lockByProviderRequest(providerCode, providerRequestId))
            .map(this::toDomain);
    }

    @Override
    public void complete(long verificationAttemptId, PersonVerifiedCallback callback) {
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
            throw providerFailure("Person verification attempt completion lost its pending fence");
        }
    }

    @Override
    public void appendSecurityAudit(PersonVerificationSecurityAudit audit) {
        String result = audit.category() == PersonVerificationFailureCategory.LATE_CALLBACK
            ? "IGNORED"
            : "FAILED";
        if (mapper.insertSecurityAudit(
            IdWorker.getId(), audit.applicationId(), result, audit.category().name(), audit.occurredAt()) != 1) {
            throw providerFailure("Person verification security audit could not be appended");
        }
    }

    private PersonVerificationAttemptRow toRow(PersonVerificationAttempt attempt) {
        PersonVerificationAttemptRow row = new PersonVerificationAttemptRow();
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

    private PersonVerificationAttempt toDomain(PersonVerificationAttemptRow row) {
        PersonVerificationEvidenceCodec.DecodedEvidence evidence =
            evidenceCodec.decode(row.getProviderEvidenceJson());
        return new PersonVerificationAttempt(
            row.getVerificationAttemptId(),
            row.getApplicationId(),
            row.getSubmissionId(),
            row.getProviderCode(),
            row.getProviderRequestId(),
            row.getRequestFingerprint(),
            evidence.callbackDigest(),
            row.getAttemptNo(),
            PersonProviderAttemptStatus.valueOf(row.getStatus()),
            row.getNormalizedResultJson(),
            evidence.providerEvidenceJson(),
            row.getErrorCode(),
            row.getCompletedTime());
    }

    private PersonVerificationException providerFailure(String message) {
        return new PersonVerificationException(PersonVerificationFailureCategory.PROVIDER_FAILURE, message);
    }
}

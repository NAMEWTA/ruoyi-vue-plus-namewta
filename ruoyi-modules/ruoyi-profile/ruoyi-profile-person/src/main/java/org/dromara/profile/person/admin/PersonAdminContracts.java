package org.dromara.profile.person.admin;

import org.dromara.profile.api.material.ProfileMaterialPort.MaterialReferenceView;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class PersonAdminContracts {

    private PersonAdminContracts() {
    }

    public record Query(String fullName, String documentNumber, String status, int pageNum, int pageSize) {
    }

    public record MaterialInput(Long ossId, Long materialNodeId) {
    }

    public record IdentityCommand(String fullName, String documentTypeCode, String documentNumber, String gender,
                                  LocalDate birthDate, LocalDate validFrom, LocalDate validUntil) {
    }

    public record DecisionCommand(String decision, String reason) {
    }

    public record CreateCommand(IdentityCommand identity, Long bindUserId, String reason,
                                List<MaterialInput> materials) {
    }

    public record ReviseCommand(IdentityCommand identity, String reason, int expectedVersion) {
    }

    public record BindingCommand(String action, String reason, int expectedBindingVersion) {
    }

    public record AssignCommand(Long userId, String reason) {
    }

    public record AccountCandidate(long userId, String userName, String nickName) {
    }

    public record RevokeCommand(String reason, int expectedVersion) {
    }

    public record Summary(long profileId, Long previousProfileId, String fullName, String documentTypeCode,
                          String documentNumber, String gender, LocalDate birthDate, String status,
                          Long bindingUserId, String bindingStatus, Instant createTime) {
    }

    public record Version(long versionId, int versionNo, String sourceType, long sourceId, String fullName,
                          String documentTypeCode, String documentNumber, String gender, LocalDate birthDate,
                          LocalDate validFrom, LocalDate validUntil, String status, Instant publishedTime) {
    }

    public record Binding(long bindingId, long userId, String status, int bindingVersion, String sourceType,
                          Long sourceId, Instant boundTime, Instant unboundTime) {
    }

    public record Source(long sourceId, String sourceType, long operatorUserId, String reason,
                         String fieldSnapshotJson, Instant occurredTime) {
    }

    public record Audit(long auditId, String operationType, long operatorUserId, String capability, String reason,
                        String beforeStatus, String afterStatus, String result, String failureCategory,
                        Instant occurredTime) {
    }

    public record Detail(Summary profile, List<Version> versions, List<Binding> bindings, List<Source> sources,
                         List<Audit> audits, List<MaterialReferenceView> currentMaterials) {
    }

    public record ReviewContext(long applicationId, long applicantUserId, String status, int submissionSeq,
                                int decisionVersion, int version, long submissionId, String fieldSnapshotJson,
                                Instant submittedTime, List<MaterialReferenceView> materials) {
    }

    public record Result(String status, long profileId, Long versionId, Long bindingId, int version) {
    }
}

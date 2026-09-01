package org.dromara.profile.enterprise.admin;

import org.dromara.profile.api.material.ProfileMaterialPort.MaterialReferenceView;

import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;

public final class EnterpriseAdminContracts {

    private EnterpriseAdminContracts() {
    }

    public record Query(String enterpriseName, String unifiedCreditCode, String status, int pageNum, int pageSize) {
    }

    public record MaterialInput(Long ossId, Long materialNodeId) {
    }

    public record IdentityCommand(String enterpriseName, String unifiedCreditCode, String enterpriseType,
                                  String legalRepresentativeName, String legalDocumentTypeCode,
                                  String legalDocumentNumber, LocalDate establishedDate,
                                  LocalDate businessTermFrom, LocalDate businessTermUntil,
                                  String registeredAddress, String businessScope, String contactName,
                                  String contactPhone, String email, BigDecimal registeredCapital,
                                  String industryCode, String website) {
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

    public record RevokeCommand(String reason, int expectedVersion) {
    }

    public record Summary(long profileId, Long previousProfileId, String enterpriseName, String unifiedCreditCode,
                          String enterpriseType, String legalRepresentativeName, String status,
                          Long bindingUserId, String bindingStatus, Instant createTime) {
    }

    public record Version(long versionId, int versionNo, String sourceType, long sourceId,
                          String enterpriseName, String unifiedCreditCode, String enterpriseType,
                          String legalRepresentativeName, String legalDocumentTypeCode,
                          String legalDocumentNumber, LocalDate establishedDate, LocalDate businessTermFrom,
                          LocalDate businessTermUntil, String registeredAddress, String businessScope,
                          String contactName, String contactPhone, String email, BigDecimal registeredCapital,
                          String industryCode, String website, String status, Instant publishedTime) {
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

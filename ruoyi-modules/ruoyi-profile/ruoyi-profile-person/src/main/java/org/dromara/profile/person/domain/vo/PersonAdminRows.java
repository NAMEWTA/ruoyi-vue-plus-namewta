package org.dromara.profile.person.domain.vo;

import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

public final class PersonAdminRows {

    private PersonAdminRows() {
    }

    @Data
    public static class ProfileRow {
        private Long profileId;
        private Long previousProfileId;
        private Long currentVersionId;
        private String fullName;
        private String documentTypeCode;
        private String documentNumber;
        private String identityKey;
        private String gender;
        private LocalDate birthDate;
        private LocalDate validFrom;
        private LocalDate validUntil;
        private String status;
        private Long bindingUserId;
        private String bindingStatus;
        private Integer version;
        private Instant createTime;
    }

    @Data
    public static class VersionRow {
        private Long versionId;
        private Long profileId;
        private Integer versionNo;
        private String sourceType;
        private Long sourceId;
        private String fullName;
        private String documentTypeCode;
        private String documentNumber;
        private String identityKey;
        private String gender;
        private LocalDate birthDate;
        private LocalDate validFrom;
        private LocalDate validUntil;
        private String status;
        private Instant publishedTime;
    }

    @Data
    public static class BindingRow {
        private Long bindingId;
        private Long profileId;
        private Long userId;
        private String status;
        private Integer bindingVersion;
        private String sourceType;
        private Long sourceId;
        private Instant boundTime;
        private Instant unboundTime;
    }

    @Data
    public static class SourceRow {
        private Long sourceId;
        private String sourceType;
        private Long operatorUserId;
        private String reason;
        private String fieldSnapshotJson;
        private Instant occurredTime;
    }

    @Data
    public static class AuditRow {
        private Long auditId;
        private String operationType;
        private Long operatorUserId;
        private String capability;
        private String reason;
        private String beforeStatus;
        private String afterStatus;
        private String result;
        private String failureCategory;
        private Instant occurredTime;
    }

    @Data
    public static class ReviewRow {
        private Long applicationId;
        private Long applicantUserId;
        private String status;
        private Integer submissionSeq;
        private Integer decisionVersion;
        private Integer version;
        private Long submissionId;
        private String fieldSnapshotJson;
        private Instant submittedTime;
    }
}

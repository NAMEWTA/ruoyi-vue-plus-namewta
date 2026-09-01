package org.dromara.profile.enterprise.persistence.row;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class EnterpriseProfileRow {
    private Long enterpriseProfileId;
    private Long previousProfileId;
    private Long currentVersionId;
    private String enterpriseName;
    private String unifiedCreditCode;
    private String enterpriseType;
    private String legalRepresentativeName;
    private String legalDocumentTypeCode;
    private String legalDocumentNumber;
    private LocalDate establishedDate;
    private LocalDate businessTermFrom;
    private LocalDate businessTermUntil;
    private String registeredAddress;
    private String businessScope;
    private String contactName;
    private String contactPhone;
    private String email;
    private BigDecimal registeredCapital;
    private String industryCode;
    private String website;
    private String status;
    private Integer version;
}

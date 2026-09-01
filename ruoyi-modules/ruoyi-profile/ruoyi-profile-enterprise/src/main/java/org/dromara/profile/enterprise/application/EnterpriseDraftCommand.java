package org.dromara.profile.enterprise.application;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EnterpriseDraftCommand(
    String enterpriseName,
    String unifiedCreditCode,
    String enterpriseType,
    String legalRepresentativeName,
    String legalDocumentTypeCode,
    String legalDocumentNumber,
    boolean handlerIsLegalRepresentative,
    LocalDate establishedDate,
    LocalDate businessTermFrom,
    LocalDate businessTermUntil,
    String registeredAddress,
    String businessScope,
    String contactName,
    String contactPhone,
    String email,
    BigDecimal registeredCapital,
    String industryCode,
    String website,
    int expectedVersion
) {
}

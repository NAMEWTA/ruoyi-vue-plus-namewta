package org.dromara.profile.enterprise.domain.application;

import org.dromara.profile.enterprise.domain.bo.EnterpriseApplicationSaveBo;
import org.dromara.profile.enterprise.domain.exception.EnterpriseApplicationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

public record EnterpriseIdentityFields(
    String enterpriseName,
    String unifiedCreditCode,
    String identityKey,
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
    String website
) {

    public static EnterpriseIdentityFields normalize(EnterpriseApplicationSaveBo command) {
        if (command == null) {
            throw new EnterpriseApplicationException("ENTERPRISE_DRAFT_REQUIRED");
        }
        String creditCode = upper(command.unifiedCreditCode());
        return new EnterpriseIdentityFields(text(command.enterpriseName()), creditCode, creditCode,
            upper(command.enterpriseType()), text(command.legalRepresentativeName()),
            upper(command.legalDocumentTypeCode()), upper(command.legalDocumentNumber()),
            command.handlerIsLegalRepresentative(), command.establishedDate(), command.businessTermFrom(),
            command.businessTermUntil(), text(command.registeredAddress()), text(command.businessScope()),
            text(command.contactName()), text(command.contactPhone()), lower(command.email()),
            command.registeredCapital(), upper(command.industryCode()), text(command.website()));
    }

    private static String upper(String value) {
        String normalized = text(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private static String lower(String value) {
        String normalized = text(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private static String text(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}

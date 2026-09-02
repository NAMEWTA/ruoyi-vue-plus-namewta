package org.dromara.profile.enterprise.domain.application;

public record EnterpriseDocumentTypeRule(String documentTypeCode, String numberPattern, boolean validityRequired) {
}

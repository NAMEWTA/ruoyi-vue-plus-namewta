package org.dromara.profile.person.domain.application;

public record PersonDocumentTypeRule(String documentTypeCode, String numberPattern, boolean validityRequired) {
}

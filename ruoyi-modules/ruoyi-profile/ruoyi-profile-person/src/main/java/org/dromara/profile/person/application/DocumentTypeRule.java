package org.dromara.profile.person.application;

public record DocumentTypeRule(String documentTypeCode, String numberPattern, boolean validityRequired) {
}

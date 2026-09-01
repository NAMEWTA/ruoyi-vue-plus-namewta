package org.dromara.profile.enterprise.application;

public record DocumentTypeRule(String documentTypeCode, String numberPattern, boolean validityRequired) {
}

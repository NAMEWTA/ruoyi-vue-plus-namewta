package org.dromara.system.password;

/**
 * 稳定、可公开的密码策略违规项。
 */
public record PasswordViolation(String reason, String message) {
}

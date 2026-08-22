package org.dromara.system.notify.support;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 通知列表目标脱敏。完整目标只允许从详情权限入口返回。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class NotifyTargetMasker {

    public static String mask(String type, String value) {
        if (value == null || value.isBlank()) {
            return "***";
        }
        if ("PHONE".equalsIgnoreCase(type) && value.length() >= 7) {
            return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
        }
        if ("EMAIL".equalsIgnoreCase(type)) {
            int separator = value.indexOf('@');
            if (separator > 0) {
                return value.substring(0, 1) + "***" + value.substring(separator);
            }
        }
        if (value.length() <= 4) {
            return "***";
        }
        return value.substring(0, 2) + "***" + value.substring(value.length() - 2);
    }
}

package org.dromara.system.notify.support;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.regex.Pattern;

/**
 * 清除错误文本中的常见凭据和 URL，同时保留可诊断的错误摘要。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class NotifyLogSanitizer {

    private static final Pattern URL = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);
    private static final Pattern SECRET = Pattern.compile(
        "(?i)(authorization|access[_-]?key|secret[_-]?key|token)\\s*[:=]\\s*[^\\s,;]+"
    );

    public static String error(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String sanitized = URL.matcher(value).replaceAll("[url omitted]");
        sanitized = SECRET.matcher(sanitized).replaceAll("$1=***");
        sanitized = sanitized.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "");
        return sanitized.length() <= 2000 ? sanitized : sanitized.substring(0, 2000);
    }
}

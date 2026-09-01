package org.dromara.common.nacos;

import org.springframework.boot.convert.ApplicationConversionService;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class NacosCandidateValidator {

    private static final Set<String> BOOLEAN_KEYS = Set.of("captcha.enable");
    private static final Set<String> POSITIVE_INTEGER_KEYS = Set.of(
        "captcha.numberlength",
        "captcha.charlength"
    );
    private static final Set<String> POSITIVE_DURATION_KEYS = Set.of(
        "notify.idempotency.defaultwindow",
        "notify.idempotency.minwindow",
        "notify.idempotency.maxwindow",
        "oss.lifecycle.downloadttl"
    );

    private NacosCandidateValidator() {
    }

    static void validate(Map<String, Object> candidate) {
        candidate.forEach((key, value) -> {
            String normalized = normalize(key);
            if (normalized.equals("nacos.config") || normalized.startsWith("nacos.config.")
                || normalized.equals("spring.profiles") || normalized.startsWith("spring.profiles.")) {
                throw new NacosConfigValidationException("PROTECTED_KEY");
            }
            validateKnownScalar(normalized, value);
        });
    }

    private static void validateKnownScalar(String key, Object value) {
        try {
            if (BOOLEAN_KEYS.contains(key)) {
                String text = String.valueOf(value);
                if (!"true".equalsIgnoreCase(text) && !"false".equalsIgnoreCase(text)) {
                    throw new IllegalArgumentException("not a boolean");
                }
            } else if (POSITIVE_INTEGER_KEYS.contains(key)) {
                if (Integer.parseInt(String.valueOf(value)) <= 0) {
                    throw new IllegalArgumentException("not positive");
                }
            } else if (POSITIVE_DURATION_KEYS.contains(key)) {
                Duration duration = ApplicationConversionService.getSharedInstance()
                    .convert(value, Duration.class);
                if (duration == null || duration.isZero() || duration.isNegative()) {
                    throw new IllegalArgumentException("not positive");
                }
            }
        } catch (RuntimeException ex) {
            throw new NacosConfigValidationException("KNOWN_TYPE_INVALID", ex);
        }
    }

    private static String normalize(String key) {
        return key.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
    }
}

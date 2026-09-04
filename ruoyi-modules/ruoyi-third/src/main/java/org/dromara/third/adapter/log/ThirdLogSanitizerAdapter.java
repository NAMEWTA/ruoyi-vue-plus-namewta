package org.dromara.third.adapter.log;

import org.dromara.common.json.utils.JsonUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ThirdLogSanitizerAdapter {
    private static final int MAX_BYTES = 16 * 1024;
    private static final Set<String> BLOCKED = Set.of("authorization", "proxy-authorization", "cookie", "set-cookie", "x-api-key", "api-key", "secret", "private-key", "signature", "token", "password");

    private ThirdLogSanitizerAdapter() {
    }

    public static Map<String, String> headers(Map<String, String> input) {
        java.util.LinkedHashMap<String, String> result = new java.util.LinkedHashMap<>();
        input.forEach((key, value) -> result.put(key, blocked(key) ? "***" : truncate(value)));
        return result;
    }

    public static String json(JsonNode input) {
        if (input == null) return null;
        JsonNode copy = input.deepCopy();
        redact(copy);
        return truncate(JsonUtils.toJsonString(copy));
    }

    public static String text(Object input) {
        return input == null ? null : truncate(String.valueOf(input));
    }

    private static void redact(JsonNode node) {
        if (node == null) return;
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            for (String name : new HashSet<>(object.propertyNames())) {
                if (blocked(name)) object.put(name, "***"); else redact(object.get(name));
            }
    } else if (node.isArray()) node.forEach(ThirdLogSanitizerAdapter::redact);
    }

    private static boolean blocked(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return BLOCKED.stream().anyMatch(lower::contains);
    }

    private static String truncate(String value) {
        if (value == null) return null;
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= MAX_BYTES) return value;
        return new String(bytes, 0, MAX_BYTES, StandardCharsets.UTF_8) + "...[truncated]";
    }
}

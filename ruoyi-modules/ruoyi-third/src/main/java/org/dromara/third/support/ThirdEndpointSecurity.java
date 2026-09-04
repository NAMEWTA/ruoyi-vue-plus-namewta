package org.dromara.third.support;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.util.Locale;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Endpoint 元数据的不可绕过校验。 */
public final class ThirdEndpointSecurity {
    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");
    private static final Set<String> REQUEST_MODES = Set.of("JSON", "QUERY", "FORM");
    private static final Set<String> RESPONSE_MODES = Set.of("JSON", "TEXT", "BYTES");
    private static final Pattern PATH_TEMPLATE = Pattern.compile("\\{[A-Za-z][A-Za-z0-9_-]{0,63}\\}");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{0,63}");
    private static final Pattern HEADER_NAME = Pattern.compile("[A-Za-z0-9!#$%&'*+.^_`|~-]{1,128}");
    private static final Set<String> CALLER_BLOCKED_HEADERS = Set.of(
        "host", "content-length", "transfer-encoding", "connection", "upgrade", "cookie", "set-cookie",
        "authorization", "proxy-authorization", "proxy-authenticate", "proxy-connection", "te", "trailer"
    );

    private ThirdEndpointSecurity() { }

    public static String validateMethod(String method) {
        String value = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
        if (!METHODS.contains(value)) throw new ServiceException("Unsupported HTTP method");
        return value;
    }

    public static String validateRelativePath(String path) {
        if (path == null || path.isBlank() || path.length() > 512 || path.contains("\\") || path.contains("..")
            || path.startsWith("//") || path.matches(".*[\\r\\n<>\\$].*")) {
            throw new ServiceException("Endpoint path must be a safe relative path");
        }
        if (!path.startsWith("/")) throw new ServiceException("Endpoint path must start with /");
        String withoutTemplates = PATH_TEMPLATE.matcher(path).replaceAll("");
        if (withoutTemplates.contains("{") || withoutTemplates.contains("}")) {
            throw new ServiceException("Endpoint path contains an invalid template variable");
        }
        try {
            URI uri = URI.create(PATH_TEMPLATE.matcher(path).replaceAll("template"));
            String decoded = java.net.URLDecoder.decode(path, java.nio.charset.StandardCharsets.UTF_8);
            if (decoded.contains("..") || decoded.contains("\\") || decoded.startsWith("//")
                || uri.isAbsolute() || uri.getHost() != null || uri.getRawAuthority() != null
                || uri.getQuery() != null || uri.getRawFragment() != null) {
                throw new ServiceException("Endpoint path cannot contain an authority or query");
            }
        } catch (IllegalArgumentException ex) {
            throw new ServiceException("Endpoint path is invalid");
        }
        return path;
    }

    public static String validateRequestMode(String mode) {
        String value = mode == null ? "" : mode.trim().toUpperCase(Locale.ROOT);
        if (!REQUEST_MODES.contains(value)) throw new ServiceException("Unsupported request mode");
        return value;
    }

    public static String validateResponseMode(String mode) {
        String value = mode == null ? "" : mode.trim().toUpperCase(Locale.ROOT);
        if (!RESPONSE_MODES.contains(value)) throw new ServiceException("Unsupported response mode");
        return value;
    }

    public static String validateIdentifier(String value, String field) {
        String normalized = value == null ? "" : value.trim();
        if (!IDENTIFIER.matcher(normalized).matches()) throw new ServiceException(field + " is invalid");
        return normalized;
    }

    public static String validateHeaderName(String value) {
        String normalized = value == null ? "" : value.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (!HEADER_NAME.matcher(normalized).matches() || isConnectionHeader(lower)
            || CALLER_BLOCKED_HEADERS.contains(lower) || lower.startsWith("proxy-") || lower.startsWith("sec-")) {
            throw new ServiceException("Header is not allowed");
        }
        return normalized;
    }

    public static String validateConfiguredHeaderName(String value) {
        String normalized = value == null ? "" : value.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (!HEADER_NAME.matcher(normalized).matches() || isConnectionHeader(lower) || lower.startsWith("proxy-") || lower.startsWith("sec-")) {
            throw new ServiceException("Configured header is not allowed");
        }
        return normalized;
    }

    public static void validateMetadataJson(String value, String field) {
        if (value == null || value.isBlank()) return;
        try {
            JsonNode node = JsonUtils.getJsonMapper().readTree(value);
            if (node == null || (!node.isObject() && !node.isArray())) throw new IllegalArgumentException();
            rejectExecutableMetadata(node);
        } catch (RuntimeException ex) {
            throw new ServiceException(field + " must be valid constrained JSON");
        }
    }

    public static void validateSharedHeadersJson(String value) {
        if (value == null || value.isBlank()) return;
        try {
            JsonNode node = JsonUtils.getJsonMapper().readTree(value);
            if (node == null || !node.isObject()) throw new IllegalArgumentException();
            node.properties().forEach(entry -> validateConfiguredHeaderName(entry.getKey()));
        } catch (RuntimeException ex) {
            throw new ServiceException("Shared headers must be a safe JSON object");
        }
    }

    private static void rejectExecutableMetadata(JsonNode node) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                String name = entry.getKey().toLowerCase(Locale.ROOT);
                if (name.contains("spel") || name.contains("script") || name.contains("expression")
                    || name.contains("reflect") || name.equals("class") || name.equals("classname")) {
                    throw new ServiceException("Executable metadata is not allowed");
                }
                rejectExecutableMetadata(entry.getValue());
            }
        } else if (node.isArray()) node.forEach(ThirdEndpointSecurity::rejectExecutableMetadata);
        else if (node.isTextual()) {
            String text = node.asText().toLowerCase(Locale.ROOT);
            if (text.contains("#{") || text.contains("${") || text.contains("spel:") || text.contains("javascript:")) {
                throw new ServiceException("Executable metadata is not allowed");
            }
        }
    }

    public static Set<String> parseAllowedNames(String schema) {
        if (schema == null || schema.isBlank()) return Set.of();
        try {
            JsonNode node = JsonUtils.getJsonMapper().readTree(schema);
            Set<String> names = new HashSet<>();
            JsonNode allowed = node != null && node.isObject() ? node.get("allowed") : node;
            if (allowed == null || !allowed.isArray()) throw new IllegalArgumentException();
            allowed.forEach(value -> names.add(validateIdentifier(value.asText(), "Parameter name").toLowerCase(Locale.ROOT)));
            return names;
        } catch (RuntimeException ex) {
            throw new ServiceException("Parameter schema must declare an allowed array");
        }
    }

    public static Set<String> parseSensitiveFields(String schema) {
        if (schema == null || schema.isBlank()) return Set.of();
        try {
            JsonNode node = JsonUtils.getJsonMapper().readTree(schema);
            if (node == null || !node.isArray()) throw new IllegalArgumentException();
            Set<String> fields = new HashSet<>();
            node.forEach(value -> fields.add(validateIdentifier(value.asText(), "Sensitive field")));
            return fields;
        } catch (RuntimeException ex) {
            throw new ServiceException("Sensitive fields must be an array of names");
        }
    }

    private static boolean isConnectionHeader(String lower) {
        return Set.of("host", "content-length", "transfer-encoding", "connection", "upgrade", "proxy-connection", "te", "trailer").contains(lower);
    }
}

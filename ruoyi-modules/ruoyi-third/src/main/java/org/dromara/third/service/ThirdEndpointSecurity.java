package org.dromara.third.service;

import org.dromara.common.core.exception.ServiceException;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/** Endpoint 元数据的不可绕过校验。 */
public final class ThirdEndpointSecurity {
    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");
    private static final Set<String> REQUEST_MODES = Set.of("JSON", "QUERY", "FORM");
    private static final Set<String> RESPONSE_MODES = Set.of("JSON", "TEXT", "BYTES");

    private ThirdEndpointSecurity() { }

    public static String validateMethod(String method) {
        String value = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
        if (!METHODS.contains(value)) throw new ServiceException("Unsupported HTTP method");
        return value;
    }

    public static String validateRelativePath(String path) {
        if (path == null || path.isBlank() || path.length() > 512 || path.contains("\\") || path.contains("..")
            || path.startsWith("//") || path.matches(".*[\\r\\n<>\\$\\{\\}].*")) {
            throw new ServiceException("Endpoint path must be a safe relative path");
        }
        if (!path.startsWith("/")) throw new ServiceException("Endpoint path must start with /");
        try {
            URI uri = URI.create(path);
            if (uri.isAbsolute() || uri.getHost() != null || uri.getRawAuthority() != null || uri.getQuery() != null) {
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
}

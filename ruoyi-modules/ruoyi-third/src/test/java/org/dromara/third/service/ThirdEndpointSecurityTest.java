package org.dromara.third.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.dromara.third.support.ThirdEndpointSecurity;

import static org.junit.jupiter.api.Assertions.*;

@Tag("local")
class ThirdEndpointSecurityTest {
    @Test
    void acceptsOnlySafeRelativePaths() {
        assertEquals("/company/{id}", ThirdEndpointSecurity.validateRelativePath("/company/{id}"));
        assertThrows(RuntimeException.class, () -> ThirdEndpointSecurity.validateRelativePath("https://evil.example/x"));
        assertThrows(RuntimeException.class, () -> ThirdEndpointSecurity.validateRelativePath("//evil.example/x"));
        assertThrows(RuntimeException.class, () -> ThirdEndpointSecurity.validateRelativePath("/../secret"));
        assertThrows(RuntimeException.class, () -> ThirdEndpointSecurity.validateRelativePath("/x?target=evil"));
    }

    @Test
    void limitsMethodsAndModes() {
        assertEquals("POST", ThirdEndpointSecurity.validateMethod("post"));
        assertThrows(RuntimeException.class, () -> ThirdEndpointSecurity.validateMethod("OPTIONS"));
        assertThrows(RuntimeException.class, () -> ThirdEndpointSecurity.validateRequestMode("SCRIPT"));
        assertThrows(RuntimeException.class, () -> ThirdEndpointSecurity.validateResponseMode("XML"));
    }

    @Test
    void rejectsCallerConnectionAndExecutableMetadata() {
        assertThrows(RuntimeException.class, () -> ThirdEndpointSecurity.validateHeaderName("Authorization"));
        assertThrows(RuntimeException.class, () -> ThirdEndpointSecurity.validateHeaderName("Host"));
        assertEquals("Authorization", ThirdEndpointSecurity.validateConfiguredHeaderName("Authorization"));
        assertThrows(RuntimeException.class, () -> ThirdEndpointSecurity.validateMetadataJson("{\"expression\":\"#{evil}\"}", "schema"));
        assertThrows(RuntimeException.class, () -> ThirdEndpointSecurity.validateRelativePath("/%2e%2e/secret"));
        assertThrows(RuntimeException.class, () -> ThirdEndpointSecurity.validateRelativePath("/resource#fragment"));
    }

    @Test
    void validatesTrustedHeaderValuesAndParameterSchema() {
        assertEquals("Bearer test", ThirdEndpointSecurity.validateConfiguredHeaderValue("Bearer test"));
        assertThrows(RuntimeException.class, () -> ThirdEndpointSecurity.validateConfiguredHeaderValue("bad\r\nvalue"));
        assertThrows(RuntimeException.class, () -> ThirdEndpointSecurity.validateSharedHeadersJson("{\"X-Test\":\"bad\\nvalue\"}"));
        assertEquals(java.util.Set.of("id"), ThirdEndpointSecurity.parseAllowedNames("{\"allowed\":[\"id\"]}"));
        assertThrows(RuntimeException.class, () -> ThirdEndpointSecurity.parseAllowedNames("{}"));
    }
}

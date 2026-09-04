package org.dromara.third.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

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
}

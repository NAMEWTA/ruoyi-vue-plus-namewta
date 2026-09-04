package org.dromara.third.service;

import org.dromara.third.config.ThirdCryptoProperties;
import org.dromara.third.domain.ThirdCredential;
import org.dromara.third.adapter.security.ThirdCredentialCryptoAdapter;
import org.dromara.third.port.ThirdCredentialCryptoPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@Tag("local")
class ThirdCredentialCryptoTest {
    @Test
    void encryptsAndDecryptsWithAuthenticatedCiphertext() {
        ThirdCryptoProperties properties = new ThirdCryptoProperties();
        properties.setMasterKey(Base64.getEncoder().encodeToString("01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)));
        ThirdCredentialCryptoAdapter crypto = new ThirdCredentialCryptoAdapter(properties);
        ThirdCredentialCryptoPort.EncryptedSecret encrypted = crypto.encrypt("PROVIDER", "API_KEY", "{\"headers\":{\"X-Api-Key\":\"secret\"}}");
        ThirdCredential credential = new ThirdCredential();
        credential.setScopeType("PROVIDER"); credential.setCredentialType("API_KEY");
        credential.setCiphertext(encrypted.ciphertext()); credential.setNonce(encrypted.nonce()); credential.setAuthTag(encrypted.authTag());
        assertEquals("{\"headers\":{\"X-Api-Key\":\"secret\"}}", crypto.decrypt(credential));
        credential.getAuthTag()[0] ^= 1;
        assertThrows(RuntimeException.class, () -> crypto.decrypt(credential));
    }

    @Test
    void rejectsMissingMasterKey() {
        ThirdCryptoProperties properties = new ThirdCryptoProperties();
        ThirdCredentialCryptoAdapter crypto = new ThirdCredentialCryptoAdapter(properties);
        assertThrows(RuntimeException.class, () -> crypto.encrypt("PROVIDER", "API_KEY", "{}"));
    }
}

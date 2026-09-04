package org.dromara.third.port;

import org.dromara.third.domain.ThirdCredential;

/** Authenticated credential encryption boundary. */
public interface ThirdCredentialCryptoPort {
    EncryptedSecret encrypt(String scopeType, String credentialType, String json);

    String decrypt(ThirdCredential credential);

    record EncryptedSecret(byte[] ciphertext, byte[] nonce, byte[] authTag) {
    }
}

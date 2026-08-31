package org.dromara.system.openapi.credential.service;

import org.dromara.common.openapi.protocol.OpenApiAuthenticationException;
import org.dromara.common.openapi.spi.OpenApiCredential;
import org.dromara.common.openapi.spi.OpenApiCredentialResolver;
import org.dromara.system.openapi.credential.crypto.OpenApiCredentialCrypto;
import org.dromara.system.openapi.credential.crypto.OpenApiCredentialCrypto.EncryptedSecret;
import org.dromara.system.openapi.credential.domain.SysOpenApiCredential;
import org.dromara.system.openapi.credential.mapper.SysOpenApiCredentialMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * The system-owned implementation of the common credential resolver SPI.
 */
@Service
@ConditionalOnProperty(prefix = "openapi", name = "enabled", havingValue = "true")
public class SystemOpenApiCredentialResolver implements OpenApiCredentialResolver {

    private final SysOpenApiCredentialMapper mapper;
    private final OpenApiCredentialCrypto crypto;

    public SystemOpenApiCredentialResolver(SysOpenApiCredentialMapper mapper,
                                           OpenApiCredentialCrypto crypto) {
        this.mapper = mapper;
        this.crypto = crypto;
    }

    @Override
    public OpenApiCredential resolve(String appKey) {
        try {
            SysOpenApiCredential credential = mapper.selectUsableByAppKey(appKey);
            if (credential == null || !"0".equals(credential.getStatus()) || isExpired(credential)) {
                throw new OpenApiAuthenticationException();
            }
            String secret = crypto.decrypt(credential.getOwnerUserId(), credential.getAppKey(),
                new EncryptedSecret(credential.getSecretCiphertext(), credential.getSecretNonce(),
                    credential.getSecretTag(), credential.getKekVersion()));
            return new OpenApiCredential(credential.getOpenApiCredentialId(), credential.getOwnerUserId(),
                credential.getAppKey(), secret, credential.getExpiresAt() == null ? null
                    : credential.getExpiresAt().atZone(ZoneId.systemDefault()).toInstant());
        } catch (OpenApiAuthenticationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new OpenApiAuthenticationException(e);
        }
    }

    private static boolean isExpired(SysOpenApiCredential credential) {
        return credential.getExpiresAt() != null && !credential.getExpiresAt().isAfter(LocalDateTime.now());
    }
}

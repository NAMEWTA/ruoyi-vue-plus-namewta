package org.dromara.third.service;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.utils.IdGeneratorUtil;
import org.dromara.third.dao.ThirdCredentialDao;
import org.dromara.third.dao.ThirdEndpointDao;
import org.dromara.third.dao.ThirdProviderDao;
import org.dromara.third.domain.ThirdCredential;
import org.dromara.third.domain.ThirdEndpoint;
import org.dromara.third.domain.ThirdProvider;
import org.dromara.third.domain.bo.ThirdCredentialBo;
import org.dromara.third.domain.vo.ThirdCredentialVo;
import org.dromara.third.port.ThirdConfigSnapshotPort;
import org.dromara.third.port.ThirdCredentialCryptoPort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ThirdCredentialService {
    private final ThirdCredentialDao credentialDao;
    private final ThirdProviderDao providerDao;
    private final ThirdEndpointDao endpointDao;
    private final ThirdCredentialCryptoPort crypto;
    private final ThirdConfigSnapshotPort configCache;

    public List<ThirdCredentialVo> list(String providerCode, String endpointCode) {
        ThirdProvider provider = provider(providerCode);
        Long endpointId = endpointCode == null || endpointCode.isBlank() ? null : endpoint(provider.getProviderId(), endpointCode).getEndpointId();
        return credentialDao.findByScope(provider.getProviderId(), endpointId).stream()
            .map(c -> new ThirdCredentialVo(c.getCredentialId(), providerCode, endpointCode, c.getScopeType(), c.getCredentialType(),
                c.getKekVersion(), c.getExpiresAt(), c.getVersion(), c.getDelFlag())).toList();
    }

    public void save(ThirdCredentialBo bo) {
        ThirdProvider provider = provider(bo.getProviderCode());
        ThirdEndpoint endpoint = bo.getEndpointCode() == null || bo.getEndpointCode().isBlank() ? null : endpoint(provider.getProviderId(), bo.getEndpointCode());
        String scope = endpoint == null ? "PROVIDER" : "ENDPOINT";
        ThirdCredential credential = bo.getCredentialId() == null ? new ThirdCredential() : credentialDao.findById(bo.getCredentialId());
        if (bo.getCredentialId() != null && credential == null) throw new ServiceException("Credential not found");
        if (credential.getCredentialId() == null) credential.setCredentialId(IdGeneratorUtil.nextLongId());
        credential.setProviderId(provider.getProviderId());
        credential.setEndpointId(endpoint == null ? null : endpoint.getEndpointId());
        credential.setScopeType(scope);
        credential.setCredentialType(bo.getCredentialType().trim());
        ThirdCredentialCryptoPort.EncryptedSecret encrypted = crypto.encrypt(scope, bo.getCredentialType().trim(), bo.getSecretJson());
        credential.setCiphertext(encrypted.ciphertext());
        credential.setNonce(encrypted.nonce());
        credential.setAuthTag(encrypted.authTag());
        credential.setKekVersion("v1");
        credential.setExpiresAt(bo.getExpiresAt());
        credential.setVersion(credential.getVersion() == null ? 1 : credential.getVersion() + 1);
        credential.setDelFlag(Boolean.TRUE.equals(bo.getEnabled()) ? "0" : "1");
        int changed = bo.getCredentialId() == null ? credentialDao.insert(credential) : credentialDao.update(credential);
        if (changed != 1) throw new ServiceException("Credential save failed");
        configCache.evict(provider.getProviderCode(), endpoint == null ? null : endpoint.getEndpointCode());
    }

    public void remove(Long credentialId) {
        ThirdCredential credential = credentialDao.findById(credentialId);
        if (credential == null) throw new ServiceException("Credential not found");
        credential.setDelFlag("1");
        credentialDao.update(credential);
        ThirdProvider provider = providerDao.findActiveById(credential.getProviderId());
        configCache.evict(provider == null ? null : provider.getProviderCode(), null);
    }

    private ThirdProvider provider(String code) {
        if (code == null || code.isBlank()) throw new ServiceException("Provider code is required");
        ThirdProvider provider = providerDao.findActiveByCode(code.trim());
        if (provider == null) throw new ServiceException("Provider not found");
        return provider;
    }

    private ThirdEndpoint endpoint(Long providerId, String code) {
        ThirdEndpoint endpoint = endpointDao.findActiveByProviderAndCode(providerId, code.trim());
        if (endpoint == null) throw new ServiceException("Endpoint not found");
        return endpoint;
    }
}

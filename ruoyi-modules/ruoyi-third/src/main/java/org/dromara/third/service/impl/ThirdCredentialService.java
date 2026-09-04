package org.dromara.third.service.impl;

import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.utils.IdGeneratorUtil;
import org.dromara.third.domain.ThirdCredential;
import org.dromara.third.domain.ThirdEndpoint;
import org.dromara.third.domain.ThirdProvider;
import org.dromara.third.domain.bo.ThirdCredentialBo;
import org.dromara.third.domain.vo.ThirdCredentialVo;
import org.dromara.third.mapper.ThirdCredentialMapper;
import org.dromara.third.mapper.ThirdEndpointMapper;
import org.dromara.third.mapper.ThirdProviderMapper;
import org.dromara.third.service.ThirdCredentialCrypto;
import org.dromara.third.service.ThirdCredentialUseCase;
import org.dromara.third.service.ThirdConfigCache;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ThirdCredentialService implements ThirdCredentialUseCase {
    private static final String ENABLED = "0";
    private final ThirdCredentialMapper credentialMapper;
    private final ThirdProviderMapper providerMapper;
    private final ThirdEndpointMapper endpointMapper;
    private final ThirdCredentialCrypto crypto;
    private final ThirdConfigCache configCache;

    @Override
    public List<ThirdCredentialVo> list(String providerCode, String endpointCode) {
        ThirdProvider provider = provider(providerCode);
        Long endpointId = endpointCode == null || endpointCode.isBlank() ? null : endpoint(provider.getProviderId(), endpointCode).getEndpointId();
        LambdaQueryWrapper<ThirdCredential> query = new LambdaQueryWrapper<ThirdCredential>()
            .eq(ThirdCredential::getDelFlag, "0").eq(ThirdCredential::getProviderId, provider.getProviderId());
        if (endpointId == null) query.isNull(ThirdCredential::getEndpointId);
        else query.eq(ThirdCredential::getEndpointId, endpointId);
        return credentialMapper.selectList(query).stream().map(c -> new ThirdCredentialVo(c.getCredentialId(), providerCode,
            endpointCode, c.getScopeType(), c.getCredentialType(), c.getKekVersion(), c.getExpiresAt(), c.getVersion(), c.getDelFlag())).toList();
    }

    @Override
    @DSTransactional
    public void save(ThirdCredentialBo bo) {
        ThirdProvider provider = provider(bo.getProviderCode());
        ThirdEndpoint endpoint = bo.getEndpointCode() == null || bo.getEndpointCode().isBlank() ? null : endpoint(provider.getProviderId(), bo.getEndpointCode());
        String scope = endpoint == null ? "PROVIDER" : "ENDPOINT";
        ThirdCredential credential = bo.getCredentialId() == null ? new ThirdCredential() : credentialMapper.selectById(bo.getCredentialId());
        if (credential == null) throw new ServiceException("凭据不存在");
        if (credential.getCredentialId() == null) credential.setCredentialId(IdGeneratorUtil.nextLongId());
        credential.setProviderId(provider.getProviderId());
        credential.setEndpointId(endpoint == null ? null : endpoint.getEndpointId());
        credential.setScopeType(scope);
        credential.setCredentialType(bo.getCredentialType().trim());
        ThirdCredentialCrypto.EncryptedSecret encrypted = crypto.encrypt(scope, bo.getCredentialType().trim(), bo.getSecretJson());
        credential.setCiphertext(encrypted.ciphertext());
        credential.setNonce(encrypted.nonce());
        credential.setAuthTag(encrypted.authTag());
        credential.setKekVersion("v1");
        credential.setExpiresAt(bo.getExpiresAt());
        credential.setVersion(credential.getVersion() == null ? 1 : credential.getVersion() + 1);
        credential.setDelFlag(Boolean.TRUE.equals(bo.getEnabled()) ? "0" : "1");
        if (bo.getCredentialId() == null) credentialMapper.insert(credential); else credentialMapper.updateById(credential);
        configCache.evict(provider.getProviderCode(), endpoint == null ? null : endpoint.getEndpointCode());
    }

    @Override
    @DSTransactional
    public void remove(Long credentialId) {
        ThirdCredential credential = credentialMapper.selectById(credentialId);
        if (credential == null) throw new ServiceException("凭据不存在");
        credential.setDelFlag("1");
        credentialMapper.updateById(credential);
        ThirdProvider provider = providerMapper.selectById(credential.getProviderId());
        configCache.evict(provider == null ? null : provider.getProviderCode(), null);
    }

    private ThirdProvider provider(String code) {
        if (code == null || code.isBlank()) throw new ServiceException("供应商编码不能为空");
        ThirdProvider provider = providerMapper.selectOne(new LambdaQueryWrapper<ThirdProvider>().eq(ThirdProvider::getProviderCode, code.trim()).eq(ThirdProvider::getDelFlag, "0"));
        if (provider == null) throw new ServiceException("供应商不存在");
        return provider;
    }

    private ThirdEndpoint endpoint(Long providerId, String code) {
        ThirdEndpoint endpoint = endpointMapper.selectOne(new LambdaQueryWrapper<ThirdEndpoint>().eq(ThirdEndpoint::getProviderId, providerId).eq(ThirdEndpoint::getEndpointCode, code.trim()).eq(ThirdEndpoint::getDelFlag, "0"));
        if (endpoint == null) throw new ServiceException("接口不存在");
        return endpoint;
    }
}

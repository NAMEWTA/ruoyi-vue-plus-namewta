package org.dromara.third.service;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.utils.IdGeneratorUtil;
import org.dromara.third.dao.ThirdEndpointDao;
import org.dromara.third.dao.ThirdProviderDao;
import org.dromara.third.domain.ThirdEndpoint;
import org.dromara.third.domain.ThirdProvider;
import org.dromara.third.domain.bo.ThirdEndpointBo;
import org.dromara.third.domain.vo.ThirdEndpointVo;
import org.dromara.third.port.ThirdConfigSnapshotPort;
import org.dromara.third.support.ThirdEndpointSecurity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ThirdEndpointService {
    private final ThirdEndpointDao endpointDao;
    private final ThirdProviderDao providerDao;
    private final ThirdConfigSnapshotPort configCache;

    public List<ThirdEndpointVo> list(Long providerId, String keyword) {
        return endpointDao.findActive(providerId, keyword).stream().map(ThirdEndpointService::toVo).toList();
    }

    public ThirdEndpointVo get(Long endpointId) { return toVo(required(endpointId)); }

    public void save(ThirdEndpointBo bo) {
        ThirdProvider provider = providerDao.findActiveById(bo.getProviderId());
        if (provider == null) throw new ServiceException("Provider not found");
        if (!provider.getProviderCode().equals(bo.getProviderCode())) throw new ServiceException("Provider code does not match provider id");
        if (endpointDao.existsCode(bo.getProviderCode(), bo.getEndpointCode(), bo.getEndpointId())) throw new ServiceException("Endpoint code already exists for provider");
        ThirdEndpoint entity = bo.getEndpointId() == null ? new ThirdEndpoint() : required(bo.getEndpointId());
        if (bo.getEndpointId() == null) { entity.setEndpointId(IdGeneratorUtil.nextLongId()); entity.setVersion(0); entity.setDelFlag("0"); }
        entity.setProviderId(provider.getProviderId());
        entity.setProviderCode(provider.getProviderCode());
        entity.setEndpointCode(bo.getEndpointCode().trim());
        entity.setEndpointName(bo.getEndpointName().trim());
        entity.setHttpMethod(ThirdEndpointSecurity.validateMethod(bo.getHttpMethod()));
        entity.setRelativePath(ThirdEndpointSecurity.validateRelativePath(bo.getRelativePath()));
        entity.setRequestMode(ThirdEndpointSecurity.validateRequestMode(bo.getRequestMode()));
        entity.setResponseMode(ThirdEndpointSecurity.validateResponseMode(bo.getResponseMode()));
        entity.setPathSchemaJson(bo.getPathSchemaJson());
        entity.setQuerySchemaJson(bo.getQuerySchemaJson());
        entity.setHeaderSchemaJson(bo.getHeaderSchemaJson());
        entity.setBodySchemaJson(bo.getBodySchemaJson());
        entity.setResponseSchemaJson(bo.getResponseSchemaJson());
        entity.setOverrideJson(bo.getOverrideJson());
        entity.setStatus(bo.getStatus());
        entity.setIdempotent(bo.getIdempotent());
        if (bo.getRetryCount() != null && bo.getRetryCount() > 0 && !Boolean.TRUE.equals(bo.getIdempotent())) throw new ServiceException("Only idempotent endpoints may retry");
        entity.setRateLimit(Math.min(nonNegative(bo.getRateLimit()), nonNegative(provider.getRateLimit())));
        entity.setConcurrencyLimit(Math.min(nonNegative(bo.getConcurrencyLimit()), nonNegative(provider.getConcurrencyLimit())));
        entity.setRetryCount(Math.min(nonNegative(bo.getRetryCount()), Boolean.TRUE.equals(bo.getIdempotent()) ? 3 : 0));
        entity.setSensitiveFieldsJson(bo.getSensitiveFieldsJson());
        entity.setAdapterCode(bo.getAdapterCode());
        int changed = bo.getEndpointId() == null ? endpointDao.insert(entity) : endpointDao.update(entity);
        if (changed != 1) throw new ServiceException("Endpoint save failed");
        configCache.evict(entity.getProviderCode(), entity.getEndpointCode());
    }

    public void changeStatus(Long endpointId, String status) {
        ThirdEndpoint entity = required(endpointId);
        entity.setStatus("1".equals(status) ? "1" : "0");
        endpointDao.update(entity);
        configCache.evict(entity.getProviderCode(), entity.getEndpointCode());
    }

    public void remove(Long endpointId) {
        ThirdEndpoint entity = required(endpointId);
        if (!"1".equals(entity.getStatus())) throw new ServiceException("Disable endpoint before deleting");
        entity.setDelFlag("1");
        endpointDao.update(entity);
        configCache.evict(entity.getProviderCode(), entity.getEndpointCode());
    }

    private ThirdEndpoint required(Long id) {
        ThirdEndpoint value = endpointDao.findActiveById(id);
        if (value == null) throw new ServiceException("Endpoint not found");
        return value;
    }

    private static int nonNegative(Integer value) { return value == null ? 0 : Math.max(value, 0); }

    private static ThirdEndpointVo toVo(ThirdEndpoint x) {
        return new ThirdEndpointVo(x.getEndpointId(), x.getProviderId(), x.getProviderCode(), x.getEndpointCode(), x.getEndpointName(),
            x.getHttpMethod(), x.getRelativePath(), x.getRequestMode(), x.getResponseMode(), x.getPathSchemaJson(), x.getQuerySchemaJson(),
            x.getHeaderSchemaJson(), x.getBodySchemaJson(), x.getResponseSchemaJson(), x.getOverrideJson(), x.getStatus(), x.getIdempotent(),
            x.getRateLimit(), x.getConcurrencyLimit(), x.getRetryCount(), x.getSensitiveFieldsJson(), x.getAdapterCode(), x.getVersion(),
            x.getCreateTime(), x.getUpdateTime());
    }
}

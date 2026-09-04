package org.dromara.third.service;

import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.utils.IdGeneratorUtil;
import org.dromara.third.dao.ThirdProviderDao;
import org.dromara.third.domain.ThirdProvider;
import org.dromara.third.domain.bo.ThirdProviderBo;
import org.dromara.third.domain.vo.ThirdProviderVo;
import org.dromara.third.usecase.ThirdProviderUseCase;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ThirdProviderService implements ThirdProviderUseCase {
    private static final String ENABLED = "0";
    private final ThirdProviderDao providerDao;
    private final ThirdConfigCache configCache;

    public List<ThirdProviderVo> list(String keyword) {
        return providerDao.findActive(keyword).stream().map(ThirdProviderService::toVo).toList();
    }

    public ThirdProviderVo get(Long providerId) { return toVo(required(providerId)); }

    @DSTransactional
    public void save(ThirdProviderBo bo) {
        ThirdProvider current = bo.getProviderId() == null ? null : required(bo.getProviderId());
        if (providerDao.existsCode(bo.getProviderCode(), bo.getProviderId())) throw new ServiceException("Provider code already exists");
        ThirdProvider entity = current == null ? new ThirdProvider() : current;
        if (current == null) { entity.setProviderId(IdGeneratorUtil.nextLongId()); entity.setVersion(0); entity.setDelFlag("0"); }
        entity.setProviderCode(bo.getProviderCode().trim());
        entity.setProviderName(bo.getProviderName().trim());
        entity.setBaseUrl(normalizeBaseUrl(bo.getBaseUrl()));
        entity.setStatus(bo.getStatus());
        entity.setTimeoutConnectMs(bo.getTimeoutConnectMs());
        entity.setTimeoutReadMs(bo.getTimeoutReadMs());
        entity.setRateLimit(bo.getRateLimit());
        entity.setConcurrencyLimit(bo.getConcurrencyLimit());
        entity.setSharedHeadersJson(bo.getSharedHeadersJson());
        entity.setRemark(bo.getRemark());
        int changed = current == null ? providerDao.insert(entity) : providerDao.update(entity);
        if (changed != 1) throw new ServiceException("Provider save failed");
        configCache.evict(entity.getProviderCode(), null);
    }

    @DSTransactional
    public void changeStatus(Long providerId, String status) {
        ThirdProvider entity = required(providerId);
        entity.setStatus("1".equals(status) ? "1" : ENABLED);
        providerDao.update(entity);
        configCache.evict(entity.getProviderCode(), null);
    }

    @DSTransactional
    public void remove(Long providerId) {
        ThirdProvider entity = required(providerId);
        if (!"1".equals(entity.getStatus())) throw new ServiceException("Disable provider before deleting");
        if (providerDao.countActiveEndpoints(providerId) > 0) throw new ServiceException("Remove endpoints before deleting provider");
        entity.setDelFlag("1");
        providerDao.update(entity);
        configCache.evict(entity.getProviderCode(), null);
    }

    private ThirdProvider required(Long id) {
        ThirdProvider value = providerDao.findActiveById(id);
        if (value == null) throw new ServiceException("Provider not found");
        return value;
    }

    private static String normalizeBaseUrl(String url) {
        try {
            URI uri = URI.create(url.trim());
            if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null
                || uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getUserInfo() != null) {
                throw new ServiceException("Base URL must be an http(s) origin");
            }
            return url.trim().replaceAll("/+$", "");
        } catch (IllegalArgumentException e) {
            throw new ServiceException("Base URL is invalid");
        }
    }

    private static ThirdProviderVo toVo(ThirdProvider x) {
        return new ThirdProviderVo(x.getProviderId(), x.getProviderCode(), x.getProviderName(), x.getBaseUrl(), x.getStatus(),
            x.getTimeoutConnectMs(), x.getTimeoutReadMs(), x.getRateLimit(), x.getConcurrencyLimit(), x.getSharedHeadersJson(),
            x.getRemark(), x.getVersion(), x.getCreateTime(), x.getUpdateTime());
    }
}

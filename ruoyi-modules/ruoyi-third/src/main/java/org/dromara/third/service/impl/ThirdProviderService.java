package org.dromara.third.service.impl;

import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.utils.IdGeneratorUtil;
import org.dromara.third.domain.ThirdEndpoint;
import org.dromara.third.domain.ThirdProvider;
import org.dromara.third.domain.bo.ThirdProviderBo;
import org.dromara.third.domain.vo.ThirdProviderVo;
import org.dromara.third.mapper.ThirdEndpointMapper;
import org.dromara.third.mapper.ThirdProviderMapper;
import org.dromara.third.service.ThirdProviderUseCase;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ThirdProviderService implements ThirdProviderUseCase {
    private static final String ENABLED = "0";
    private final ThirdProviderMapper providerMapper;
    private final ThirdEndpointMapper endpointMapper;

    @Override
    public List<ThirdProviderVo> list(String keyword) {
        LambdaQueryWrapper<ThirdProvider> query = new LambdaQueryWrapper<ThirdProvider>()
            .eq(ThirdProvider::getDelFlag, "0").orderByAsc(ThirdProvider::getProviderId);
        if (keyword != null && !keyword.isBlank()) query.and(q -> q.like(ThirdProvider::getProviderCode, keyword.trim()).or().like(ThirdProvider::getProviderName, keyword.trim()));
        return providerMapper.selectList(query).stream().map(ThirdProviderService::toVo).toList();
    }

    @Override
    public ThirdProviderVo get(Long providerId) {
        return toVo(required(providerId));
    }

    @Override
    @DSTransactional
    public void save(ThirdProviderBo bo) {
        ThirdProvider current = bo.getProviderId() == null ? null : required(bo.getProviderId());
        boolean duplicate = providerMapper.selectCount(new LambdaQueryWrapper<ThirdProvider>()
            .eq(ThirdProvider::getProviderCode, bo.getProviderCode()).eq(ThirdProvider::getDelFlag, "0")
            .ne(bo.getProviderId() != null, ThirdProvider::getProviderId, bo.getProviderId())) > 0;
        if (duplicate) throw new ServiceException("Provider code already exists");
        ThirdProvider entity = current == null ? new ThirdProvider() : current;
        if (current == null) { entity.setProviderId(IdGeneratorUtil.nextLongId()); entity.setVersion(0); entity.setDelFlag("0"); }
        entity.setProviderCode(bo.getProviderCode().trim()); entity.setProviderName(bo.getProviderName().trim()); entity.setBaseUrl(normalizeBaseUrl(bo.getBaseUrl()));
        entity.setStatus(bo.getStatus()); entity.setTimeoutConnectMs(bo.getTimeoutConnectMs()); entity.setTimeoutReadMs(bo.getTimeoutReadMs());
        entity.setRateLimit(bo.getRateLimit()); entity.setConcurrencyLimit(bo.getConcurrencyLimit()); entity.setSharedHeadersJson(bo.getSharedHeadersJson()); entity.setRemark(bo.getRemark());
        if ((current == null ? providerMapper.insert(entity) : providerMapper.updateById(entity)) != 1) throw new ServiceException("Provider save failed");
    }

    @Override
    @DSTransactional
    public void changeStatus(Long providerId, String status) { ThirdProvider entity = required(providerId); entity.setStatus("1".equals(status) ? "1" : ENABLED); providerMapper.updateById(entity); }

    @Override
    @DSTransactional
    public void remove(Long providerId) {
        ThirdProvider entity = required(providerId);
        if (!"1".equals(entity.getStatus())) throw new ServiceException("Disable provider before deleting");
        long activeEndpoints = endpointMapper.selectCount(new LambdaQueryWrapper<ThirdEndpoint>().eq(ThirdEndpoint::getProviderId, providerId).eq(ThirdEndpoint::getDelFlag, "0"));
        if (activeEndpoints > 0) throw new ServiceException("Remove endpoints before deleting provider");
        entity.setDelFlag("1"); providerMapper.updateById(entity);
    }

    private ThirdProvider required(Long id) { ThirdProvider value = providerMapper.selectOne(new LambdaQueryWrapper<ThirdProvider>().eq(ThirdProvider::getProviderId, id).eq(ThirdProvider::getDelFlag, "0")); if (value == null) throw new ServiceException("Provider not found"); return value; }
    private static String normalizeBaseUrl(String url) { try { java.net.URI uri = java.net.URI.create(url.trim()); if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null || uri.getRawQuery() != null || uri.getRawFragment() != null) throw new ServiceException("Base URL must be an http(s) origin"); return url.trim().replaceAll("/+$", ""); } catch (IllegalArgumentException e) { throw new ServiceException("Base URL is invalid"); } }
    private static ThirdProviderVo toVo(ThirdProvider x) { return new ThirdProviderVo(x.getProviderId(), x.getProviderCode(), x.getProviderName(), x.getBaseUrl(), x.getStatus(), x.getTimeoutConnectMs(), x.getTimeoutReadMs(), x.getRateLimit(), x.getConcurrencyLimit(), x.getSharedHeadersJson(), x.getRemark(), x.getVersion(), x.getCreateTime(), x.getUpdateTime()); }
}

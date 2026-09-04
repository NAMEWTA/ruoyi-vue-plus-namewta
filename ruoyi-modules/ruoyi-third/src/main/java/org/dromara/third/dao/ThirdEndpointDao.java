package org.dromara.third.dao;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.third.domain.ThirdEndpoint;
import org.dromara.third.mapper.ThirdEndpointMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ThirdEndpointDao {
    private final ThirdEndpointMapper endpointMapper;

    public List<ThirdEndpoint> findActive(Long providerId, String keyword) {
        LambdaQueryWrapper<ThirdEndpoint> query = new LambdaQueryWrapper<ThirdEndpoint>()
            .eq(ThirdEndpoint::getDelFlag, "0").orderByAsc(ThirdEndpoint::getEndpointId);
        query.eq(providerId != null, ThirdEndpoint::getProviderId, providerId);
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            query.and(q -> q.like(ThirdEndpoint::getEndpointCode, value).or().like(ThirdEndpoint::getEndpointName, value));
        }
        return endpointMapper.selectList(query);
    }

    public List<ThirdEndpoint> findAllByProviderCode(String providerCode) {
        return endpointMapper.selectList(new LambdaQueryWrapper<ThirdEndpoint>()
            .eq(ThirdEndpoint::getProviderCode, providerCode));
    }

    public ThirdEndpoint findActiveById(Long endpointId) {
        return endpointMapper.selectOne(new LambdaQueryWrapper<ThirdEndpoint>()
            .eq(ThirdEndpoint::getEndpointId, endpointId).eq(ThirdEndpoint::getDelFlag, "0"));
    }

    public ThirdEndpoint findActiveByProviderAndCode(Long providerId, String endpointCode) {
        return endpointMapper.selectOne(new LambdaQueryWrapper<ThirdEndpoint>()
            .eq(ThirdEndpoint::getProviderId, providerId).eq(ThirdEndpoint::getEndpointCode, endpointCode)
            .eq(ThirdEndpoint::getDelFlag, "0"));
    }

    public boolean existsCode(String providerCode, String endpointCode, Long excludedId) {
        return endpointMapper.selectCount(new LambdaQueryWrapper<ThirdEndpoint>()
            .eq(ThirdEndpoint::getProviderCode, providerCode).eq(ThirdEndpoint::getEndpointCode, endpointCode)
            .eq(ThirdEndpoint::getDelFlag, "0").ne(excludedId != null, ThirdEndpoint::getEndpointId, excludedId)) > 0;
    }

    public int insert(ThirdEndpoint endpoint) { return endpointMapper.insert(endpoint); }

    public int update(ThirdEndpoint endpoint) { return endpointMapper.updateById(endpoint); }
}

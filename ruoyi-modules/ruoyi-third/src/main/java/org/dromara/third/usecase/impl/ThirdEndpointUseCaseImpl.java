package org.dromara.third.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.third.domain.bo.ThirdEndpointBo;
import org.dromara.third.domain.vo.ThirdEndpointVo;
import org.dromara.third.service.ThirdEndpointService;
import org.dromara.third.usecase.ThirdEndpointUseCase;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ThirdEndpointUseCaseImpl implements ThirdEndpointUseCase {
    private final ThirdEndpointService service;

    public List<ThirdEndpointVo> list(Long providerId, String keyword) { return service.list(providerId, keyword); }
    public ThirdEndpointVo get(Long endpointId) { return service.get(endpointId); }
    public void save(ThirdEndpointBo bo) { service.save(bo); }
    public void changeStatus(Long endpointId, String status) { service.changeStatus(endpointId, status); }
    public void remove(Long endpointId) { service.remove(endpointId); }
}

package org.dromara.third.usecase;

import org.dromara.third.domain.bo.ThirdEndpointBo;
import org.dromara.third.domain.vo.ThirdEndpointVo;

import java.util.List;

public interface ThirdEndpointUseCase {
    List<ThirdEndpointVo> list(Long providerId, String keyword);
    ThirdEndpointVo get(Long endpointId);
    void save(ThirdEndpointBo bo);
    void changeStatus(Long endpointId, String status);
    void remove(Long endpointId);
}

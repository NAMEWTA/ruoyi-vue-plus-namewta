package org.dromara.third.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.third.domain.vo.ThirdInvocationVo;
import org.dromara.third.domain.vo.ThirdStatisticVo;
import org.dromara.third.service.ThirdObservabilityService;
import org.dromara.third.usecase.ThirdObservabilityUseCase;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ThirdObservabilityUseCaseImpl implements ThirdObservabilityUseCase {
    private final ThirdObservabilityService service;

    @Override
    public List<ThirdInvocationVo> invocations(String providerCode) {
        return service.invocations(providerCode);
    }

    @Override
    public List<ThirdStatisticVo> statistics(String providerCode) {
        return service.statistics(providerCode);
    }
}

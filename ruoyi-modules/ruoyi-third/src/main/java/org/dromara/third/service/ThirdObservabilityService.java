package org.dromara.third.service;

import lombok.RequiredArgsConstructor;
import org.dromara.third.domain.vo.ThirdInvocationVo;
import org.dromara.third.domain.vo.ThirdStatisticVo;
import org.dromara.third.mapper.ThirdInvocationMapper;
import org.dromara.third.mapper.ThirdStatisticMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ThirdObservabilityService {
    private final ThirdInvocationMapper invocationMapper;
    private final ThirdStatisticMapper statisticMapper;

    public List<ThirdInvocationVo> invocations(String providerCode) {
        return invocationMapper.selectRecent(providerCode, LocalDateTime.now().minusDays(7)).stream()
            .map(x -> new ThirdInvocationVo(x.getInvocationId(), x.getRequestId(), x.getProviderCode(), x.getEndpointCode(), x.getAttemptCount(), x.getLogicalStatus(), x.getFailureCategory(), x.getHttpStatus(), x.getDurationMs(), x.getSanitizedRequestJson(), x.getSanitizedResponseJson(), x.getCreateTime())).toList();
    }

    public List<ThirdStatisticVo> statistics(String providerCode) {
        return statisticMapper.selectRecent(providerCode).stream().map(x -> new ThirdStatisticVo(x.getProviderCode(), x.getEndpointCode(), x.getStatDate(), x.getAttemptCount(), x.getSuccessCount(), x.getFailureCount(), x.getTimeoutCount(), x.getRejectedCount(), x.getQuotaValue())).toList();
    }
}

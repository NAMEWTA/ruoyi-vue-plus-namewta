package org.dromara.third.service;

import lombok.RequiredArgsConstructor;
import org.dromara.common.mybatis.utils.IdGeneratorUtil;
import org.dromara.common.web.logging.SysLogEventSink;
import org.dromara.third.api.ThirdPartyFailureCategory;
import org.dromara.third.api.ThirdPartyRequest;
import org.dromara.third.api.ThirdPartyResponse;
import org.dromara.third.domain.ThirdInvocation;
import org.dromara.third.domain.ThirdStatistic;
import org.dromara.third.mapper.ThirdInvocationMapper;
import org.dromara.third.mapper.ThirdStatisticMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ThirdInvocationRecorder {
    private final ThirdInvocationMapper invocationMapper;
    private final ThirdStatisticMapper statisticMapper;
    private final ObjectProvider<SysLogEventSink> logSink;

    public void record(ThirdPartyRequest request, ThirdPartyResponse<?> response, long durationMs, int attempts) {
        ThirdInvocation invocation = new ThirdInvocation();
        invocation.setInvocationId(IdGeneratorUtil.nextLongId()); invocation.setRequestId(response.requestId());
        invocation.setProviderCode(request.providerCode()); invocation.setEndpointCode(request.endpointCode());
        invocation.setAttemptCount(attempts); invocation.setLogicalStatus(response.isSuccess() ? "SUCCESS" : "FAILURE");
        invocation.setFailureCategory(response.category().name()); invocation.setHttpStatus(response.httpStatus());
        invocation.setDurationMs(durationMs); invocation.setSanitizedRequestJson(ThirdLogSanitizer.json(request.body()));
        invocation.setSanitizedResponseJson(ThirdLogSanitizer.text(response.providerMessage())); invocation.setCreateTime(LocalDateTime.now());
        invocationMapper.upsert(invocation);
        ThirdStatistic statistic = new ThirdStatistic(); statistic.setStatisticId(IdGeneratorUtil.nextLongId());
        statistic.setProviderCode(request.providerCode()); statistic.setEndpointCode(request.endpointCode()); statistic.setStatDate(LocalDate.now());
        statistic.setAttemptCount((long) attempts); statistic.setSuccessCount(response.isSuccess() ? 1L : 0L);
        statistic.setFailureCount(response.isSuccess() ? 0L : 1L); statistic.setTimeoutCount(response.category() == ThirdPartyFailureCategory.TIMEOUT ? 1L : 0L);
        statistic.setRejectedCount(response.category() == ThirdPartyFailureCategory.RATE_LIMITED || response.category() == ThirdPartyFailureCategory.REJECTED ? 1L : 0L);
        statistic.setQuotaValue(0L); statisticMapper.upsert(statistic);
        Map<String, Object> event = new LinkedHashMap<>(); event.put("event", "HTTP_REQUEST"); event.put("requestId", response.requestId());
        event.put("providerCode", request.providerCode()); event.put("endpointCode", request.endpointCode()); event.put("status", response.category().name()); event.put("durationMs", durationMs);
        logSink.ifAvailable(sink -> sink.write(event));
    }
}

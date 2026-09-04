package org.dromara.third.service;

import lombok.RequiredArgsConstructor;
import org.dromara.common.mybatis.utils.IdGeneratorUtil;
import org.dromara.common.web.logging.SysLogEventSink;
import org.dromara.third.api.ThirdPartyFailureCategory;
import org.dromara.third.api.ThirdPartyRequest;
import org.dromara.third.api.ThirdPartyResponse;
import org.dromara.third.dao.ThirdInvocationDao;
import org.dromara.third.dao.ThirdStatisticDao;
import org.dromara.third.domain.ThirdInvocation;
import org.dromara.third.domain.ThirdStatistic;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ThirdInvocationRecorder {
    private final ThirdInvocationDao invocationDao;
    private final ThirdStatisticDao statisticDao;
    private final ObjectProvider<SysLogEventSink> logSink;

    public void record(ThirdPartyRequest request, ThirdPartyResponse<?> response, long durationMs, int attempts) {
        ThirdInvocation invocation = new ThirdInvocation();
        invocation.setInvocationId(IdGeneratorUtil.nextLongId()); invocation.setRequestId(response.requestId());
        invocation.setProviderCode(request.providerCode()); invocation.setEndpointCode(request.endpointCode());
        invocation.setAttemptCount(attempts); invocation.setLogicalStatus(response.isSuccess() ? "SUCCESS" : "FAILURE");
        invocation.setFailureCategory(response.category().name()); invocation.setHttpStatus(response.httpStatus());
        invocation.setDurationMs(durationMs); invocation.setSanitizedRequestJson(ThirdLogSanitizer.json(request.body()));
        invocation.setSanitizedResponseJson(ThirdLogSanitizer.text(response.providerMessage())); invocation.setCreateTime(LocalDateTime.now());
        invocationDao.upsert(invocation);
        ThirdStatistic statistic = new ThirdStatistic(); statistic.setStatisticId(IdGeneratorUtil.nextLongId());
        statistic.setProviderCode(request.providerCode()); statistic.setEndpointCode(request.endpointCode()); statistic.setStatDate(LocalDate.now());
        statistic.setAttemptCount((long) attempts); statistic.setSuccessCount(response.isSuccess() ? 1L : 0L);
        statistic.setFailureCount(response.isSuccess() ? 0L : 1L); statistic.setTimeoutCount(response.category() == ThirdPartyFailureCategory.TIMEOUT ? 1L : 0L);
        statistic.setRejectedCount(response.category() == ThirdPartyFailureCategory.RATE_LIMITED || response.category() == ThirdPartyFailureCategory.REJECTED ? 1L : 0L);
        statistic.setQuotaValue(0L); statisticDao.upsert(statistic);
        Map<String, Object> event = new LinkedHashMap<>(); event.put("event", "HTTP_REQUEST"); event.put("requestId", response.requestId());
        event.put("providerCode", request.providerCode()); event.put("endpointCode", request.endpointCode()); event.put("status", response.category().name()); event.put("durationMs", durationMs);
        event.put("parameters", request.query()); event.put("requestHeaders", ThirdLogSanitizer.headers(request.headers()));
        event.put("body", ThirdLogSanitizer.json(request.body())); event.put("completed", response.category() != ThirdPartyFailureCategory.NONE || response.isSuccess());
        logSink.ifAvailable(sink -> sink.write(event));
        if (response.isSuccess() || response.category() != ThirdPartyFailureCategory.NONE) {
            ThirdStatistic providerStatistic = new ThirdStatistic(); providerStatistic.setStatisticId(IdGeneratorUtil.nextLongId());
            providerStatistic.setProviderCode(request.providerCode()); providerStatistic.setEndpointCode(null); providerStatistic.setStatDate(LocalDate.now());
            providerStatistic.setAttemptCount((long) attempts); providerStatistic.setSuccessCount(response.isSuccess() ? 1L : 0L);
            providerStatistic.setFailureCount(response.isSuccess() ? 0L : 1L); providerStatistic.setTimeoutCount(response.category() == ThirdPartyFailureCategory.TIMEOUT ? 1L : 0L);
            providerStatistic.setRejectedCount(response.category() == ThirdPartyFailureCategory.RATE_LIMITED || response.category() == ThirdPartyFailureCategory.REJECTED ? 1L : 0L);
            providerStatistic.setQuotaValue(0L); statisticDao.upsert(providerStatistic);
        }
    }
}

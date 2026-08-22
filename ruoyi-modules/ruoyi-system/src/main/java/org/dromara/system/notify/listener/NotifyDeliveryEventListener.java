package org.dromara.system.notify.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.notify.event.NotifyDeliveryEvent;
import org.dromara.system.notify.service.ISysNotifyMonitorService;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Best-effort 通知监控消费者。持久化失败不得改变 Provider 同步结果。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotifyDeliveryEventListener {

    private final ISysNotifyMonitorService monitorService;

    @Async
    @EventListener
    public void onNotifyDelivery(NotifyDeliveryEvent event) {
        try {
            monitorService.record(event);
        } catch (RuntimeException exception) {
            String requestId = event.request() == null ? null : event.request().requestId();
            log.error("通知监控落库失败，requestId={}, exception={}", requestId,
                exception.getClass().getSimpleName());
        }
    }
}

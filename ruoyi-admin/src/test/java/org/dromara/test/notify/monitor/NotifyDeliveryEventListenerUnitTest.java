package org.dromara.test.notify.monitor;

import org.dromara.common.notify.event.NotifyDeliveryEvent;
import org.dromara.common.notify.model.*;
import org.dromara.system.notify.listener.NotifyDeliveryEventListener;
import org.dromara.system.notify.service.ISysNotifyMonitorService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("dev")
class NotifyDeliveryEventListenerUnitTest {

    @Test
    void listenerIsAsyncBestEffortAndSwallowsPersistenceFailure() throws Exception {
        ISysNotifyMonitorService service = mock(ISysNotifyMonitorService.class);
        NotifyDeliveryEvent event = event();
        doThrow(new IllegalStateException("db unavailable")).when(service).record(event);
        NotifyDeliveryEventListener listener = new NotifyDeliveryEventListener(service);

        assertDoesNotThrow(() -> listener.onNotifyDelivery(event));

        Method method = NotifyDeliveryEventListener.class.getDeclaredMethod("onNotifyDelivery", NotifyDeliveryEvent.class);
        assertNotNull(method.getAnnotation(Async.class));
        assertNotNull(method.getAnnotation(EventListener.class));
    }

    private NotifyDeliveryEvent event() {
        NotifyTarget target = NotifyTarget.phone("13812345678");
        NotifyRequest request = NotifyRequest.builder()
            .requestId("request-listener")
            .channel(NotifyChannel.SMS)
            .targets(List.of(target))
            .content(new NotifyTextContent(null, "content"))
            .build();
        NotifyResult result = new NotifyResult(request.requestId(), NotifyChannel.SMS, "sms-main",
            NotifyStatus.ACCEPTED,
            List.of(NotifyTargetResult.accepted(target, "message-1", 1)));
        return new NotifyDeliveryEvent(request, new NotifyContext(1L, null, "trace"), result, Instant.now());
    }
}

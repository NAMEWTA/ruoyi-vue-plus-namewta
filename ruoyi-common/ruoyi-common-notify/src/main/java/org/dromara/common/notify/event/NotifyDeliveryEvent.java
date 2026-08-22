package org.dromara.common.notify.event;

import org.dromara.common.notify.model.NotifyContext;
import org.dromara.common.notify.model.NotifyRequest;
import org.dromara.common.notify.model.NotifyResult;

import java.time.Instant;

/**
 * Provider 同步调用后的不可变监控事件。
 */
public record NotifyDeliveryEvent(
    NotifyRequest request,
    NotifyContext context,
    NotifyResult result,
    String originalRequestId,
    Instant occurredAt
) {

    public NotifyDeliveryEvent(NotifyRequest request, NotifyContext context, NotifyResult result,
                               Instant occurredAt) {
        this(request, context, result, null, occurredAt);
    }
}

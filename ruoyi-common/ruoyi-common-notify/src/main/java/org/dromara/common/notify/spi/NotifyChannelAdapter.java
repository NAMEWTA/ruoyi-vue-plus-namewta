package org.dromara.common.notify.spi;

import org.dromara.common.notify.model.NotifyAdapterRequest;
import org.dromara.common.notify.model.NotifyAdapterResult;

import java.util.Set;

/**
 * 外部通知渠道扩展点。
 */
public interface NotifyChannelAdapter {

    String channel();

    default Set<String> supportedTargetTypes() {
        return Set.of();
    }

    NotifyAdapterResult send(NotifyAdapterRequest request);
}

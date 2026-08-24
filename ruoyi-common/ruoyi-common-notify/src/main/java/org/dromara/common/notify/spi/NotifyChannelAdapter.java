package org.dromara.common.notify.spi;

import org.dromara.common.notify.model.NotifyAdapterRequest;
import org.dromara.common.notify.model.NotifyAdapterResult;
import org.dromara.common.notify.model.NotifyChannel;

import java.util.Set;

/**
 * 外部通知渠道扩展点。
 */
public interface NotifyChannelAdapter {

    NotifyChannel channel();

    default Set<String> supportedTargetTypes() {
        return Set.of();
    }

    NotifyAdapterResult send(NotifyAdapterRequest request);
}

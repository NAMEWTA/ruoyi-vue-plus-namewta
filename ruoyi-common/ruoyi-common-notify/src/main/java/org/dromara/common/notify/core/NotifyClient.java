package org.dromara.common.notify.core;

import org.dromara.common.notify.model.NotifyRequest;
import org.dromara.common.notify.model.NotifyResult;

/**
 * 统一通知同步入口。
 */
@FunctionalInterface
public interface NotifyClient {

    NotifyResult send(NotifyRequest request);
}

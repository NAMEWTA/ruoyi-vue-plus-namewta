package org.dromara.common.notify.spi;

import org.dromara.common.notify.model.NotifyContext;

/**
 * 当前发送线程审计上下文解析器。
 */
@FunctionalInterface
public interface NotifyContextResolver {

    NotifyContext resolve();
}

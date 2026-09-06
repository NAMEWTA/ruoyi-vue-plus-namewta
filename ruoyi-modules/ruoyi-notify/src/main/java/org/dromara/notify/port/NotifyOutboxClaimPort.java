package org.dromara.notify.port;

import org.dromara.notify.domain.entity.NotifyOutbox;

import java.util.List;

/** Outbox 领取端口，事务由实现用例负责。 */
public interface NotifyOutboxClaimPort {
    List<NotifyOutbox> claim(String owner);
}

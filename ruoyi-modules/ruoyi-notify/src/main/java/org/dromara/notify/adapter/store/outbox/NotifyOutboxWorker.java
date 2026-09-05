package org.dromara.notify.adapter.store.outbox;

import lombok.RequiredArgsConstructor;
import org.dromara.notify.domain.entity.NotifyOutbox;
import org.dromara.notify.mapper.NotifyOutboxMapper;
import org.dromara.notify.service.DispatchNotificationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbox 轮询 Worker，仅负责领取任务并委托投递用例。
 */
@Component
@RequiredArgsConstructor
public class NotifyOutboxWorker {
    private final NotifyOutboxMapper outboxMapper;
    private final DispatchNotificationService dispatchService;
    private final String owner = UUID.randomUUID().toString();

    /** 每秒领取一批到期任务。 */
    @Scheduled(fixedDelayString = "${notify.outbox.poll-delay-ms:1000}")
    public void poll() {
        for (NotifyOutbox outbox : outboxMapper.selectClaimable(Instant.now().toString(), 50)) {
            outbox.setStatus("PROCESSING");
            outbox.setLeaseOwner(owner);
            outbox.setLeaseUntil(Instant.now().plusSeconds(60).toString());
            outboxMapper.updateById(outbox);
            dispatchService.dispatch(outbox);
        }
    }
}

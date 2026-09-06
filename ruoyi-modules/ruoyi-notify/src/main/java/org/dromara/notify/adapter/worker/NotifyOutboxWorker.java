package org.dromara.notify.adapter.worker;

import lombok.RequiredArgsConstructor;
import org.dromara.notify.domain.entity.NotifyOutbox;
import org.dromara.notify.port.NotifyDispatchPort;
import org.dromara.notify.port.NotifyOutboxClaimPort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Outbox 轮询 Worker，仅负责领取任务并委托投递用例。
 */
@Component
@RequiredArgsConstructor
public class NotifyOutboxWorker {
    private final NotifyOutboxClaimPort claimService;
    private final NotifyDispatchPort dispatchService;
    private final String owner = UUID.randomUUID().toString();

    /** 每秒领取一批到期任务。 */
    @Scheduled(fixedDelayString = "${notify.outbox.poll-delay-ms:1000}")
    public void poll() {
        for (NotifyOutbox outbox : claimService.claim(owner)) {
            dispatchService.dispatch(outbox);
        }
    }
}


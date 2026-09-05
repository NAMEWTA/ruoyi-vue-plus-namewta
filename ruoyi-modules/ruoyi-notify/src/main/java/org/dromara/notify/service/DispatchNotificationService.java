package org.dromara.notify.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.notify.core.NotifyClient;
import org.dromara.common.notify.model.NotifyRequest;
import org.dromara.common.notify.model.NotifyResult;
import org.dromara.common.notify.model.NotifyTarget;
import org.dromara.common.notify.model.NotifyTextContent;
import org.dromara.notify.api.NotificationChannel;
import org.dromara.notify.api.NotificationStatus;
import org.dromara.notify.domain.entity.NotifyAttempt;
import org.dromara.notify.domain.entity.NotifyDelivery;
import org.dromara.notify.domain.entity.NotifyIntent;
import org.dromara.notify.domain.entity.NotifyOutbox;
import org.dromara.notify.mapper.NotifyAttemptMapper;
import org.dromara.notify.mapper.NotifyDeliveryMapper;
import org.dromara.notify.mapper.NotifyIntentMapper;
import org.dromara.notify.mapper.NotifyOutboxMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 单条通知投递服务。
 *
 * <p>Provider 调用在数据库事务外执行，完成后以短事务更新 Attempt、Delivery 和 Intent。</p>
 */
@Service
@RequiredArgsConstructor
public class DispatchNotificationService {
    private final NotifyIntentMapper intentMapper;
    private final NotifyDeliveryMapper deliveryMapper;
    private final NotifyAttemptMapper attemptMapper;
    private final NotifyOutboxMapper outboxMapper;
    private final NotifyClient notifyClient;

    /** 执行一个 Outbox 任务。 */
    public void dispatch(NotifyOutbox outbox) {
        NotifyIntent intent = intentMapper.selectById(outbox.getIntentId());
        NotifyDelivery delivery = deliveryMapper.selectById(outbox.getDeliveryId());
        if (intent == null || delivery == null || !"PENDING".equals(delivery.getStatus())) {
            outbox.setStatus("DONE");
            outboxMapper.updateById(outbox);
            return;
        }
        long started = System.nanoTime();
        NotifyResult result = null;
        String errorCode = null;
        String errorMessage = null;
        try {
            if (NotificationChannel.IN_APP.name().equals(delivery.getChannel())) {
                delivery.setStatus("ACCEPTED");
            } else {
                NotifyRequest request = NotifyRequest.builder()
                    .requestId(String.valueOf(delivery.getDeliveryId()))
                    .bizType(intent.getBizType())
                    .bizId(intent.getBizId())
                    .channel(org.dromara.common.notify.model.NotifyChannel.of(delivery.getChannel().toLowerCase()))
                    .targets(List.of(target(delivery)))
                    .content(new NotifyTextContent(intent.getTitleSnapshot(), intent.getContentSnapshot()))
                    .idempotencyKey(String.valueOf(delivery.getDeliveryId()))
                    .build();
                result = notifyClient.send(request);
                delivery.setStatus(result.status().name());
                if (!result.deliveries().isEmpty()) {
                    delivery.setProviderMessageId(result.deliveries().getFirst().providerMessageId());
                    errorCode = result.deliveries().getFirst().errorCode();
                    errorMessage = result.deliveries().getFirst().errorMessage();
                }
            }
        } catch (RuntimeException exception) {
            delivery.setStatus("UNKNOWN");
            errorCode = "DISPATCH_ERROR";
            errorMessage = exception.getClass().getSimpleName();
        }
        delivery.setAttemptCount((delivery.getAttemptCount() == null ? 0 : delivery.getAttemptCount()) + 1);
        delivery.setErrorCode(errorCode);
        delivery.setErrorMessage(errorMessage);
        deliveryMapper.updateById(delivery);

        NotifyAttempt attempt = new NotifyAttempt();
        attempt.setAttemptId(org.dromara.common.mybatis.utils.IdGeneratorUtil.nextLongId());
        attempt.setIntentId(intent.getIntentId());
        attempt.setDeliveryId(delivery.getDeliveryId());
        attempt.setAttemptNo(delivery.getAttemptCount());
        attempt.setProviderKey(result == null ? "in-app" : result.providerKey());
        attempt.setStatus(delivery.getStatus());
        attempt.setProviderMessageId(delivery.getProviderMessageId());
        attempt.setErrorCategory(errorCode);
        attempt.setErrorCode(errorCode);
        attempt.setErrorMessage(errorMessage);
        attempt.setCostTime(java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        attemptMapper.insert(attempt);

        outbox.setStatus(isSuccess(delivery.getStatus()) ? "DONE" : "READY");
        outbox.setAttemptCount((outbox.getAttemptCount() == null ? 0 : outbox.getAttemptCount()) + 1);
        outbox.setLastErrorCode(errorCode);
        outbox.setLastErrorMessage(errorMessage);
        if (!isSuccess(delivery.getStatus())) {
            outbox.setNextAttemptAt(Instant.now().plusSeconds(backoff(outbox.getAttemptCount())).toString());
            if (outbox.getAttemptCount() >= outbox.getMaxAttempts()) {
                outbox.setStatus("DEAD_LETTER");
                delivery.setStatus("FAILED");
                deliveryMapper.updateById(delivery);
            }
        }
        outboxMapper.updateById(outbox);
        refreshIntent(intent.getIntentId());
    }

    private void refreshIntent(Long intentId) {
        NotifyIntent intent = intentMapper.selectById(intentId);
        List<NotifyDelivery> all = deliveryMapper.selectList(new LambdaQueryWrapper<NotifyDelivery>()
            .eq(NotifyDelivery::getIntentId, intentId));
        if (all.isEmpty()) return;
        long pending = all.stream().filter(d -> "PENDING".equals(d.getStatus())).count();
        long success = all.stream().filter(d -> isSuccess(d.getStatus())).count();
        intent.setStatus(pending > 0 ? "PROCESSING" : success == all.size() ? "DELIVERED" : success > 0 ? "PARTIAL_FAILURE" : "FAILED");
        intentMapper.updateById(intent);
    }

    private NotifyTarget target(NotifyDelivery delivery) {
        return switch (delivery.getChannel()) {
            case "SMS" -> NotifyTarget.phone(delivery.getTargetValue());
            case "MAIL" -> NotifyTarget.email(delivery.getTargetValue());
            default -> NotifyTarget.user(String.valueOf(delivery.getUserId()));
        };
    }

    private boolean isSuccess(String status) { return "ACCEPTED".equals(status) || "DELIVERED".equals(status); }
    private long backoff(int attempt) { return Math.min(3600L, 1L << Math.min(attempt, 10)); }
}

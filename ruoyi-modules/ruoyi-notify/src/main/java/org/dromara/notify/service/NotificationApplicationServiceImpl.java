package org.dromara.notify.service;

import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.mybatis.utils.IdGeneratorUtil;
import org.dromara.notify.api.*;
import org.dromara.notify.domain.entity.NotifyDelivery;
import org.dromara.notify.domain.entity.NotifyIntent;
import org.dromara.notify.domain.entity.NotifyOutbox;
import org.dromara.notify.domain.entity.NotifyRecipient;
import org.dromara.notify.mapper.NotifyDeliveryMapper;
import org.dromara.notify.mapper.NotifyIntentMapper;
import org.dromara.notify.mapper.NotifyOutboxMapper;
import org.dromara.notify.mapper.NotifyRecipientMapper;
import org.dromara.system.api.UserService;
import org.dromara.system.api.domain.UserDTO;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 统一通知应用服务实现。
 *
 * <p>提交阶段只写业务事实和 Outbox，不执行 Provider I/O，保证业务事务可恢复。</p>
 */
@Service
@RequiredArgsConstructor
public class NotificationApplicationServiceImpl implements NotificationApplicationService {

    private final NotifyIntentMapper intentMapper;
    private final NotifyRecipientMapper recipientMapper;
    private final NotifyDeliveryMapper deliveryMapper;
    private final NotifyOutboxMapper outboxMapper;
    private final UserService userService;
    private final DispatchNotificationService dispatchService;

    @Override
    @DSTransactional
    public NotificationReceipt submit(NotificationCommand command) {
        validate(command);
        NotifyIntent duplicated = findDuplicate(command);
        if (duplicated != null) {
            return receipt(duplicated, deliveryMapper.selectList(new LambdaQueryWrapper<NotifyDelivery>()
                .eq(NotifyDelivery::getIntentId, duplicated.getIntentId())));
        }

        long intentId = IdGeneratorUtil.nextLongId();
        NotifyIntent intent = new NotifyIntent();
        intent.setIntentId(intentId);
        intent.setAppId(command.appId());
        intent.setSceneCode(command.sceneCode());
        intent.setBizType(command.bizType());
        intent.setBizId(command.bizId());
        intent.setTemplateCode(command.templateCode());
        intent.setTemplateParamsJson(JsonUtils.toJsonString(command.templateParams()));
        intent.setStrategy(command.strategy().name());
        intent.setMode(command.mode().name());
        intent.setPriority(command.priority());
        intent.setScheduledAt(format(command.scheduledAt()));
        intent.setExpiresAt(format(command.expiresAt()));
        intent.setIdempotencyKey(command.idempotencyKey());
        intent.setStatus(NotificationStatus.QUEUED.name());
        intent.setTitleSnapshot(stringValue(command.templateParams(), "title", command.templateCode()));
        intent.setContentSnapshot(stringValue(command.templateParams(), "content", command.templateCode()));
        intent.setPathSnapshot(stringValue(command.templateParams(), "path", null));
        intent.setMetadataJson(JsonUtils.toJsonString(command.metadata()));
        intent.setVersion(0);
        intentMapper.insert(intent);

        List<ResolvedRecipient> users = resolveUsers(command);
        List<NotifyDelivery> deliveries = new ArrayList<>();
        List<NotifyOutbox> outboxes = new ArrayList<>();
        for (ResolvedRecipient user : users) {
            NotifyRecipient recipient = new NotifyRecipient();
            recipient.setRecipientId(IdGeneratorUtil.nextLongId());
            recipient.setIntentId(intentId);
            recipient.setRecipientType(command.recipientType());
            recipient.setRecipientKey(user.key());
            recipient.setUserId(user.userId());
            recipient.setTargetSnapshotJson(JsonUtils.toJsonString(Map.of(
                "phone", Objects.toString(user.phone(), ""),
                "email", Objects.toString(user.email(), ""))));
            recipient.setStatus("ACTIVE");
            recipientMapper.insert(recipient);
            for (NotificationChannel channel : command.channels()) {
                NotifyDelivery delivery = new NotifyDelivery();
                delivery.setDeliveryId(IdGeneratorUtil.nextLongId());
                delivery.setIntentId(intentId);
                delivery.setRecipientId(recipient.getRecipientId());
                delivery.setUserId(user.userId());
                delivery.setChannel(channel.name());
                delivery.setTargetValue(targetValue(channel, user));
                delivery.setStatus("PENDING");
                delivery.setAttemptCount(0);
                delivery.setVersion(0);
                deliveryMapper.insert(delivery);
                deliveries.add(delivery);

                NotifyOutbox outbox = new NotifyOutbox();
                outbox.setOutboxId(IdGeneratorUtil.nextLongId());
                outbox.setIntentId(intentId);
                outbox.setDeliveryId(delivery.getDeliveryId());
                outbox.setStatus("READY");
                outbox.setAvailableAt(format(command.scheduledAt() == null ? Instant.now() : command.scheduledAt()));
                outbox.setAttemptCount(0);
                outbox.setNextAttemptAt(outbox.getAvailableAt());
                outbox.setMaxAttempts(5);
                outboxMapper.insert(outbox);
                outboxes.add(outbox);
            }
        }
        if (command.mode() == NotificationMode.SYNC) {
            outboxes.forEach(dispatchService::dispatch);
        }
        NotificationStatus aggregate = command.mode() == NotificationMode.SYNC
            ? status(intentMapper.selectById(intentId).getStatus()) : NotificationStatus.QUEUED;
        return new NotificationReceipt(String.valueOf(intentId), aggregate, command.mode() == NotificationMode.ASYNC, true,
            deliveries.stream().map(item -> new NotificationReceipt.DeliveryReceipt(
                    String.valueOf(item.getUserId()), NotificationChannel.valueOf(item.getChannel()),
                NotificationStatus.QUEUED, null)).toList());
    }

    @Override
    public NotificationSnapshot query(NotificationQuery query) {
        if (query == null || query.notificationId() == null) {
            throw new ServiceException("通知编号不能为空");
        }
        NotifyIntent intent = intentMapper.selectById(Long.valueOf(query.notificationId()));
        if (intent == null) {
            throw new ServiceException("通知不存在");
        }
        List<NotifyDelivery> deliveries = deliveryMapper.selectList(new LambdaQueryWrapper<NotifyDelivery>()
            .eq(NotifyDelivery::getIntentId, intent.getIntentId()));
        return new NotificationSnapshot(String.valueOf(intent.getIntentId()),
            NotificationStatus.valueOf(intent.getStatus()), parseTime(intent.getCreateTime()),
            deliveries.stream().map(item -> new NotificationReceipt.DeliveryReceipt(
                String.valueOf(item.getUserId()), NotificationChannel.valueOf(item.getChannel()),
                status(item.getStatus()), item.getProviderMessageId())).toList());
    }

    @Override
    @DSTransactional
    public RetryReceipt retry(NotificationRetryCommand command) {
        if (command == null || command.notificationId() == null) {
            throw new ServiceException("通知编号不能为空");
        }
        NotifyIntent intent = intentMapper.selectById(Long.valueOf(command.notificationId()));
        if (intent == null) {
            throw new ServiceException("通知不存在");
        }
        List<NotifyDelivery> deliveries = deliveryMapper.selectList(new LambdaQueryWrapper<NotifyDelivery>()
            .eq(NotifyDelivery::getIntentId, intent.getIntentId())
            .in(NotifyDelivery::getStatus, List.of("FAILED", "UNKNOWN", "UNDELIVERABLE")));
        for (NotifyDelivery delivery : deliveries) {
            delivery.setStatus("PENDING");
            deliveryMapper.updateById(delivery);
            NotifyOutbox outbox = new NotifyOutbox();
            outbox.setOutboxId(IdGeneratorUtil.nextLongId());
            outbox.setIntentId(intent.getIntentId());
            outbox.setDeliveryId(delivery.getDeliveryId());
            outbox.setStatus("READY");
            outbox.setAvailableAt(format(Instant.now()));
            outbox.setNextAttemptAt(outbox.getAvailableAt());
            outbox.setAttemptCount(0);
            outbox.setMaxAttempts(5);
            outboxMapper.insert(outbox);
        }
        intent.setStatus(NotificationStatus.QUEUED.name());
        intentMapper.updateById(intent);
        return new RetryReceipt(command.notificationId(), NotificationStatus.QUEUED);
    }

    @Override
    @DSTransactional
    public CancelReceipt cancel(NotificationCancelCommand command) {
        if (command == null || command.notificationId() == null) {
            throw new ServiceException("通知编号不能为空");
        }
        NotifyIntent intent = intentMapper.selectById(Long.valueOf(command.notificationId()));
        if (intent == null) {
            throw new ServiceException("通知不存在");
        }
        intent.setStatus(NotificationStatus.CANCELLED.name());
        intentMapper.updateById(intent);
        deliveryMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<NotifyDelivery>()
            .eq(NotifyDelivery::getIntentId, intent.getIntentId())
            .eq(NotifyDelivery::getStatus, "PENDING")
            .set(NotifyDelivery::getStatus, "CANCELLED"));
        return new CancelReceipt(command.notificationId(), NotificationStatus.CANCELLED);
    }

    private void validate(NotificationCommand command) {
        if (command == null || blank(command.appId()) || blank(command.sceneCode())
            || blank(command.templateCode()) || command.channels().isEmpty()) {
            throw new ServiceException("通知应用、场景、模板和渠道不能为空");
        }
        if (command.expiresAt() != null && command.scheduledAt() != null
            && command.expiresAt().isBefore(command.scheduledAt())) {
            throw new ServiceException("通知截止时间不能早于计划时间");
        }
    }

    private NotifyIntent findDuplicate(NotificationCommand command) {
        if (blank(command.idempotencyKey())) {
            return null;
        }
        return intentMapper.selectOne(new LambdaQueryWrapper<NotifyIntent>()
            .eq(NotifyIntent::getAppId, command.appId())
            .eq(NotifyIntent::getIdempotencyKey, command.idempotencyKey())
            .last("limit 1"));
    }

    private List<ResolvedRecipient> resolveUsers(NotificationCommand command) {
        if ("USER".equalsIgnoreCase(command.recipientType())) {
            List<Long> ids = command.recipientIds().stream().map(Long::valueOf).toList();
            return userService.selectListByIds(ids).stream().filter(Objects::nonNull)
                .map(user -> new ResolvedRecipient(user.getUserId(), String.valueOf(user.getUserId()), user.getPhoneNumber(), user.getEmail()))
                .toList();
        }
        if ("ALL".equalsIgnoreCase(command.recipientType())) {
            if (command.recipientIds().isEmpty()) {
                return userService.selectAllActiveUsers(100_000).stream()
                    .map(user -> new ResolvedRecipient(user.getUserId(), String.valueOf(user.getUserId()), user.getPhoneNumber(), user.getEmail()))
                    .toList();
            }
            return command.recipientIds().stream().map(Long::valueOf)
                .map(userService::selectById).filter(Objects::nonNull)
                .map(user -> new ResolvedRecipient(user.getUserId(), String.valueOf(user.getUserId()), user.getPhoneNumber(), user.getEmail()))
                .toList();
        }
        if ("PHONE".equalsIgnoreCase(command.recipientType()) || "SMS".equalsIgnoreCase(command.recipientType())) {
            return command.recipientIds().stream().map(value -> new ResolvedRecipient(null, value, value, null)).toList();
        }
        if ("EMAIL".equalsIgnoreCase(command.recipientType()) || "MAIL".equalsIgnoreCase(command.recipientType())) {
            return command.recipientIds().stream().map(value -> new ResolvedRecipient(null, value, null, value)).toList();
        }
        throw new ServiceException("接收者类型仅支持 USER、ALL、PHONE 和 EMAIL");
    }

    private String targetValue(NotificationChannel channel, ResolvedRecipient user) {
        return switch (channel) {
            case IN_APP -> String.valueOf(user.userId());
            case SMS -> user.phone();
            case MAIL -> user.email();
        };
    }

    private NotificationReceipt receipt(NotifyIntent intent, List<NotifyDelivery> deliveries) {
        return new NotificationReceipt(String.valueOf(intent.getIntentId()), status(intent.getStatus()),
            "ASYNC".equals(intent.getMode()), true, deliveries.stream().map(item ->
            new NotificationReceipt.DeliveryReceipt(String.valueOf(item.getUserId()),
                NotificationChannel.valueOf(item.getChannel()), status(item.getStatus()), item.getProviderMessageId())).toList());
    }

    private NotificationStatus status(String value) { return NotificationStatus.valueOf(value); }
    private String stringValue(Map<String, Object> values, String key, String fallback) {
        Object value = values.get(key);
        return value == null ? fallback : String.valueOf(value);
    }
    private String format(Instant value) { return value == null ? null : value.toString(); }
    private Instant parseTime(java.time.LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }

    private record ResolvedRecipient(Long userId, String key, String phone, String email) {
    }
}

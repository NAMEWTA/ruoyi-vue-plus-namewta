package org.dromara.common.notify.core;

import org.dromara.common.notify.event.NotifyDeliveryEvent;
import org.dromara.common.notify.event.NotifyEventPublisher;
import org.dromara.common.notify.exception.NotifyDeliveryException;
import org.dromara.common.notify.exception.NotifyValidationException;
import org.dromara.common.notify.model.*;
import org.dromara.common.notify.registry.NotifyChannelRegistry;
import org.dromara.common.notify.spi.NotifyChannelAdapter;
import org.dromara.common.notify.spi.NotifyContextResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 统一通知同步调度器。
 */
public final class NotifyDispatcher implements NotifyClient {

    private static final Logger log = LoggerFactory.getLogger(NotifyDispatcher.class);

    private final NotifyChannelRegistry registry;
    private final NotifyContextResolver contextResolver;
    private final NotifyEventPublisher eventPublisher;

    public NotifyDispatcher(NotifyChannelRegistry registry, NotifyContextResolver contextResolver,
                            NotifyEventPublisher eventPublisher) {
        this.registry = registry;
        this.contextResolver = contextResolver;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public NotifyResult send(NotifyRequest request) {
        validateRequest(request);
        NotifyChannelAdapter adapter = registry.require(request.channel());
        validateTargets(request.targets(), adapter.supportedTargetTypes());
        NotifyContext context = resolveContext();
        NotifyAdapterResult adapterResult;
        try {
            adapterResult = adapter.send(new NotifyAdapterRequest(request, context));
            validateAdapterResult(request, adapterResult);
        } catch (NotifyValidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            adapterResult = providerFailure(request);
            log.warn("通知渠道调用异常，channel={}, exception={}", request.channel(),
                exception.getClass().getSimpleName());
        }

        NotifyResult result = aggregate(request, adapterResult);
        publish(request, context, result);
        if (result.status() != NotifyStatus.ACCEPTED) {
            throw new NotifyDeliveryException(result);
        }
        return result;
    }

    private void validateRequest(NotifyRequest request) {
        if (request == null) {
            throw new NotifyValidationException("REQUEST_REQUIRED", "通知请求不能为空");
        }
        if (isBlank(request.channel())) {
            throw new NotifyValidationException("CHANNEL_REQUIRED", "通知渠道不能为空");
        }
        if (request.targets().isEmpty()) {
            throw new NotifyValidationException("TARGET_REQUIRED", "通知目标不能为空");
        }
        if (request.content() == null || request.content().contentSnapshot() == null) {
            throw new NotifyValidationException("CONTENT_REQUIRED", "通知内容不能为空");
        }
        if (request.content() instanceof NotifyTemplateContent template && isBlank(template.contentSnapshot())) {
            throw new NotifyValidationException("CONTENT_SNAPSHOT_REQUIRED", "模板通知必须提供完整内容快照");
        }
        if (!request.attachmentOssIds().isEmpty()) {
            throw new NotifyValidationException("ATTACHMENT_SNAPSHOT_NOT_CONFIGURED", "附件快照能力尚未装配");
        }
        if (!isBlank(request.idempotencyKey())) {
            throw new NotifyValidationException("IDEMPOTENCY_NOT_CONFIGURED", "通知幂等能力尚未装配");
        }
    }

    private void validateTargets(List<NotifyTarget> targets, Set<String> supportedTypes) {
        for (NotifyTarget target : targets) {
            if (target == null || isBlank(target.type()) || isBlank(target.value())) {
                throw new NotifyValidationException("INVALID_TARGET", "通知目标类型和值不能为空");
            }
            if (NotifyTargetType.USER.equalsIgnoreCase(target.type())) {
                throw new NotifyValidationException("LOGICAL_TARGET_NOT_SUPPORTED", "common-notify 只接受物理目标");
            }
            if (!supportedTypes.isEmpty() && supportedTypes.stream().noneMatch(target.type()::equalsIgnoreCase)) {
                throw new NotifyValidationException("UNSUPPORTED_TARGET_TYPE", "渠道不支持目标类型: " + target.type());
            }
        }
    }

    private NotifyContext resolveContext() {
        NotifyContext context = contextResolver == null ? null : contextResolver.resolve();
        return context == null ? NotifyContext.empty() : context;
    }

    private void validateAdapterResult(NotifyRequest request, NotifyAdapterResult result) {
        if (result == null || isBlank(result.providerKey())) {
            throw new IllegalStateException("通知渠道未返回 Provider 标识");
        }
        if (result.deliveries().size() != request.targets().size()) {
            throw new IllegalStateException("通知渠道返回的目标结果数量不完整");
        }
        for (int index = 0; index < request.targets().size(); index++) {
            NotifyTargetResult item = result.deliveries().get(index);
            if (item == null || !request.targets().get(index).equals(item.target()) || item.status() == null) {
                throw new IllegalStateException("通知渠道返回的目标结果与请求不匹配");
            }
        }
    }

    private NotifyAdapterResult providerFailure(NotifyRequest request) {
        String provider = isBlank(request.providerKey()) ? "unresolved" : request.providerKey();
        List<NotifyTargetResult> failures = request.targets().stream()
            .map(target -> NotifyTargetResult.failed(target, "PROVIDER_ERROR", "Provider 调用失败", 0L))
            .toList();
        return new NotifyAdapterResult(provider, failures);
    }

    private NotifyResult aggregate(NotifyRequest request, NotifyAdapterResult adapterResult) {
        long accepted = adapterResult.deliveries().stream()
            .filter(item -> item.status() == NotifyDeliveryStatus.ACCEPTED)
            .count();
        NotifyStatus status = accepted == adapterResult.deliveries().size()
            ? NotifyStatus.ACCEPTED
            : accepted == 0 ? NotifyStatus.FAILED : NotifyStatus.PARTIAL_FAILURE;
        return new NotifyResult(request.requestId(), request.channel(), adapterResult.providerKey(), status,
            adapterResult.deliveries());
    }

    private void publish(NotifyRequest request, NotifyContext context, NotifyResult result) {
        try {
            eventPublisher.publish(new NotifyDeliveryEvent(request, context, result, Instant.now()));
        } catch (RuntimeException exception) {
            log.warn("通知监控事件发布失败，requestId={}, exception={}", request.requestId(),
                exception.getClass().getSimpleName());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

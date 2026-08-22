package org.dromara.common.notify.config;

import org.dromara.common.notify.attachment.NotifyAttachmentSnapshotService;
import org.dromara.common.notify.attachment.NotifyLogIdGenerator;
import org.dromara.common.notify.core.NotifyClient;
import org.dromara.common.notify.core.NotifyDispatcher;
import org.dromara.common.notify.event.NotifyEventPublisher;
import org.dromara.common.notify.idempotency.NotifyIdempotencyCoordinator;
import org.dromara.common.notify.idempotency.NotifyIdempotencyProperties;
import org.dromara.common.notify.idempotency.NotifyIdempotencyStore;
import org.dromara.common.notify.idempotency.RedisNotifyIdempotencyStore;
import org.dromara.common.notify.model.NotifyContext;
import org.dromara.common.notify.registry.NotifyChannelRegistry;
import org.dromara.common.notify.spi.NotifyChannelAdapter;
import org.dromara.common.notify.spi.NotifyContextResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.redisson.api.RedissonClient;

import java.util.List;

/**
 * 统一通知自动装配。
 */
@AutoConfiguration
@EnableConfigurationProperties(NotifyIdempotencyProperties.class)
public class NotifyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public NotifyContextResolver notifyContextResolver() {
        return NotifyContext::empty;
    }

    @Bean
    @ConditionalOnMissingBean
    public NotifyEventPublisher notifyEventPublisher(ApplicationEventPublisher publisher) {
        return publisher::publishEvent;
    }

    @Bean
    @ConditionalOnMissingBean
    public NotifyChannelRegistry notifyChannelRegistry(ObjectProvider<NotifyChannelAdapter> adapters) {
        List<NotifyChannelAdapter> adapterList = adapters.orderedStream().toList();
        return new NotifyChannelRegistry(adapterList);
    }

    @Bean
    @ConditionalOnBean(RedissonClient.class)
    @ConditionalOnMissingBean(NotifyIdempotencyStore.class)
    public NotifyIdempotencyStore notifyIdempotencyStore(RedissonClient redissonClient) {
        return new RedisNotifyIdempotencyStore(redissonClient);
    }

    @Bean
    @ConditionalOnMissingBean
    public NotifyIdempotencyCoordinator notifyIdempotencyCoordinator(
        ObjectProvider<NotifyIdempotencyStore> store,
        NotifyIdempotencyProperties properties) {
        return new NotifyIdempotencyCoordinator(store.getIfAvailable(), properties);
    }

    @Bean
    @ConditionalOnMissingBean(NotifyClient.class)
    public NotifyClient notifyClient(NotifyChannelRegistry registry, NotifyContextResolver contextResolver,
                                     NotifyEventPublisher eventPublisher,
                                     NotifyIdempotencyCoordinator idempotencyCoordinator,
                                     ObjectProvider<NotifyAttachmentSnapshotService> attachmentSnapshotService,
                                     ObjectProvider<NotifyLogIdGenerator> notifyLogIdGenerator) {
        return new NotifyDispatcher(registry, contextResolver, eventPublisher, idempotencyCoordinator,
            attachmentSnapshotService.getIfAvailable(), notifyLogIdGenerator.getIfAvailable());
    }
}

package org.dromara.common.notify.config;

import org.dromara.common.notify.core.NotifyClient;
import org.dromara.common.notify.core.NotifyDispatcher;
import org.dromara.common.notify.event.NotifyEventPublisher;
import org.dromara.common.notify.model.NotifyContext;
import org.dromara.common.notify.registry.NotifyChannelRegistry;
import org.dromara.common.notify.spi.NotifyChannelAdapter;
import org.dromara.common.notify.spi.NotifyContextResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * 统一通知自动装配。
 */
@AutoConfiguration
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
    @ConditionalOnMissingBean(NotifyClient.class)
    public NotifyClient notifyClient(NotifyChannelRegistry registry, NotifyContextResolver contextResolver,
                                     NotifyEventPublisher eventPublisher) {
        return new NotifyDispatcher(registry, contextResolver, eventPublisher);
    }
}

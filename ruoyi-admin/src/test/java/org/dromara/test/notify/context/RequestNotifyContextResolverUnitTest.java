package org.dromara.test.notify.context;

import org.dromara.common.notify.model.NotifyContext;
import org.dromara.common.notify.config.NotifyAutoConfiguration;
import org.dromara.common.notify.spi.NotifyContextResolver;
import org.dromara.system.api.model.LoginUser;
import org.dromara.web.config.NotifyContextConfiguration;
import org.dromara.web.config.RequestNotifyContextResolver;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class RequestNotifyContextResolverUnitTest {

    @Test
    void applicationConfigurationShouldReplaceEmptyAutoConfigurationFallback() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(NotifyAutoConfiguration.class))
            .withUserConfiguration(NotifyContextConfiguration.class)
            .run(context -> {
                assertThat(context).hasSingleBean(NotifyContextResolver.class);
                assertThat(context.getBean(NotifyContextResolver.class))
                    .isInstanceOf(RequestNotifyContextResolver.class);
            });
    }

    @Test
    void shouldSnapshotAuthenticatedRequestSource() {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(12L);
        loginUser.setClientPk(34L);
        RequestNotifyContextResolver resolver = new RequestNotifyContextResolver(() -> loginUser, () -> "trace-1");

        NotifyContext context = resolver.resolve();

        assertThat(context.userId()).isEqualTo(12L);
        assertThat(context.clientPk()).isEqualTo(34L);
        assertThat(context.traceId()).isEqualTo("trace-1");
    }

    @Test
    void shouldAllowBackgroundExecutionWithoutAuthenticationOrClientScope() {
        RequestNotifyContextResolver resolver = new RequestNotifyContextResolver(() -> null, () -> null);

        NotifyContext context = resolver.resolve();

        assertThat(context).isEqualTo(NotifyContext.empty());
    }
}

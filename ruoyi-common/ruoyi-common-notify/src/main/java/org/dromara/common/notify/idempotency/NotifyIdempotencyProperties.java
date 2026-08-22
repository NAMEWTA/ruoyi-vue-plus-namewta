package org.dromara.common.notify.idempotency;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 通知业务幂等窗口配置。
 */
@ConfigurationProperties(prefix = "notify.idempotency")
public class NotifyIdempotencyProperties {

    private Duration defaultWindow = Duration.ofMinutes(5);
    private Duration minWindow = Duration.ofSeconds(30);
    private Duration maxWindow = Duration.ofHours(24);

    public Duration getDefaultWindow() {
        return defaultWindow;
    }

    public void setDefaultWindow(Duration defaultWindow) {
        this.defaultWindow = defaultWindow;
    }

    public Duration getMinWindow() {
        return minWindow;
    }

    public void setMinWindow(Duration minWindow) {
        this.minWindow = minWindow;
    }

    public Duration getMaxWindow() {
        return maxWindow;
    }

    public void setMaxWindow(Duration maxWindow) {
        this.maxWindow = maxWindow;
    }
}

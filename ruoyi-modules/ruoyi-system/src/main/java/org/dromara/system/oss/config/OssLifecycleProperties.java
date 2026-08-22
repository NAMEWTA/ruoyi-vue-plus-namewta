package org.dromara.system.oss.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * OSS 临时对象生命周期配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "oss.lifecycle")
public class OssLifecycleProperties {

    /** 最后一个引用解除后保留临时对象的时间。 */
    private Duration tempRetention = Duration.ofHours(24);

    /** 下载签名有效期。 */
    private Duration downloadTtl = Duration.ofMinutes(2);

    /** 是否启用定时清理。默认关闭，需审核 dry-run 后显式开启。 */
    private boolean cleanupEnabled = false;

    /** 是否只报告待清理对象而不实际删除。 */
    private boolean cleanupDryRun = true;

    /** 每批扫描上限。 */
    private int cleanupBatchSize = 100;
}

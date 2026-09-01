package org.dromara.common.nacos;

import java.util.Set;

/**
 * Nacos 稀疏配置覆盖的稳定约定。
 */
public final class NacosConfigConstants {

    public static final String PROPERTY_SOURCE_NAME = "nacosConfigOverlay";
    public static final String DEFAULT_SERVER_ADDR = "127.0.0.1:8848";
    public static final String DEFAULT_GROUP = "DEFAULT_GROUP";
    public static final String DEFAULT_DATA_ID = "ruoyi-namewta.yml";
    public static final long DEFAULT_TIMEOUT_MS = 3000L;
    public static final Set<String> SUPPORTED_PROFILES = Set.of("local", "dev", "prod");

    private NacosConfigConstants() {
    }
}

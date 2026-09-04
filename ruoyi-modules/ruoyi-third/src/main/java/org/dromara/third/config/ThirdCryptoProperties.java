package org.dromara.third.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "third.crypto")
public class ThirdCryptoProperties {
    private String masterKey;
}

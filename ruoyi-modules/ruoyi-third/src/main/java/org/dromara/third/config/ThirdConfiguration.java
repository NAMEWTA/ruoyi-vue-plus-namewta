package org.dromara.third.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({ThirdCryptoProperties.class})
public class ThirdConfiguration {
}

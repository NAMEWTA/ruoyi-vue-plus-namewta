package org.dromara.profile.enterprise.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

@Component
@ConfigurationProperties(prefix = "profile.enterprise.verification")
public class EnterpriseVerificationProviderProperties {

    private Set<String> enabledProviders = new LinkedHashSet<>(Set.of("manual"));

    public Set<String> getEnabledProviders() {
        return Set.copyOf(enabledProviders);
    }

    public void setEnabledProviders(Set<String> enabledProviders) {
        this.enabledProviders = enabledProviders == null
            ? new LinkedHashSet<>()
            : new LinkedHashSet<>(enabledProviders);
    }
}

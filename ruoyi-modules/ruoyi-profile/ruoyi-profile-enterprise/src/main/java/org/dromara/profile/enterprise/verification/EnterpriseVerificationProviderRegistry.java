package org.dromara.profile.enterprise.verification;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class EnterpriseVerificationProviderRegistry {

    private static final String PROVIDER_CODE_PATTERN = "[a-z0-9][a-z0-9_-]{0,63}";

    private final Map<String, EnterpriseVerificationProvider> providers;
    private final Set<String> enabledProviders;

    public EnterpriseVerificationProviderRegistry(List<EnterpriseVerificationProvider> providers,
                                                  EnterpriseVerificationProviderProperties properties) {
        this.providers = index(providers);
        this.enabledProviders = properties.getEnabledProviders().stream()
            .map(this::validateCode)
            .collect(Collectors.toUnmodifiableSet());
    }

    public EnterpriseVerificationProvider requireEnabled(String providerCode) {
        String code = validateCode(providerCode);
        EnterpriseVerificationProvider provider = providers.get(code);
        if (provider == null) {
            throw new EnterpriseVerificationException(
                EnterpriseVerificationFailureCategory.UNKNOWN_PROVIDER,
                "Unknown enterprise verification provider");
        }
        if (!enabledProviders.contains(code)) {
            throw new EnterpriseVerificationException(
                EnterpriseVerificationFailureCategory.DISABLED_PROVIDER,
                "Enterprise verification provider is disabled");
        }
        return provider;
    }

    private Map<String, EnterpriseVerificationProvider> index(List<EnterpriseVerificationProvider> candidates) {
        Map<String, EnterpriseVerificationProvider> indexed = new HashMap<>();
        for (EnterpriseVerificationProvider provider : candidates) {
            String code = validateCode(provider.providerCode());
            if (indexed.putIfAbsent(code, provider) != null) {
                throw new EnterpriseVerificationException(
                    EnterpriseVerificationFailureCategory.DUPLICATE_PROVIDER,
                    "Duplicate enterprise verification provider code");
            }
        }
        return Map.copyOf(indexed);
    }

    private String validateCode(String providerCode) {
        if (providerCode == null || !providerCode.matches(PROVIDER_CODE_PATTERN)) {
            throw new EnterpriseVerificationException(
                EnterpriseVerificationFailureCategory.INVALID_PROVIDER_CODE,
                "Invalid enterprise verification provider code");
        }
        return providerCode;
    }
}

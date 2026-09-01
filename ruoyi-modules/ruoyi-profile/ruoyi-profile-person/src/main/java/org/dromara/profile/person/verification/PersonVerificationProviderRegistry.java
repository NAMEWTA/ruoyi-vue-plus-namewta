package org.dromara.profile.person.verification;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class PersonVerificationProviderRegistry {

    private static final String PROVIDER_CODE_PATTERN = "[a-z0-9][a-z0-9_-]{0,63}";

    private final Map<String, PersonVerificationProvider> providers;
    private final Set<String> enabledProviders;

    public PersonVerificationProviderRegistry(List<PersonVerificationProvider> providers,
                                              PersonVerificationProviderProperties properties) {
        this.providers = index(providers);
        this.enabledProviders = properties.getEnabledProviders().stream()
            .map(this::validateCode)
            .collect(Collectors.toUnmodifiableSet());
    }

    public PersonVerificationProvider requireEnabled(String providerCode) {
        String code = validateCode(providerCode);
        PersonVerificationProvider provider = providers.get(code);
        if (provider == null) {
            throw new PersonVerificationException(
                PersonVerificationFailureCategory.UNKNOWN_PROVIDER, "Unknown person verification provider");
        }
        if (!enabledProviders.contains(code)) {
            throw new PersonVerificationException(
                PersonVerificationFailureCategory.DISABLED_PROVIDER, "Person verification provider is disabled");
        }
        return provider;
    }

    private Map<String, PersonVerificationProvider> index(List<PersonVerificationProvider> candidates) {
        Map<String, PersonVerificationProvider> indexed = new HashMap<>();
        for (PersonVerificationProvider provider : candidates) {
            String code = validateCode(provider.providerCode());
            if (indexed.putIfAbsent(code, provider) != null) {
                throw new PersonVerificationException(
                    PersonVerificationFailureCategory.DUPLICATE_PROVIDER,
                    "Duplicate person verification provider code");
            }
        }
        return Map.copyOf(indexed);
    }

    private String validateCode(String providerCode) {
        if (providerCode == null || !providerCode.matches(PROVIDER_CODE_PATTERN)) {
            throw new PersonVerificationException(
                PersonVerificationFailureCategory.INVALID_PROVIDER_CODE,
                "Invalid person verification provider code");
        }
        return providerCode;
    }
}

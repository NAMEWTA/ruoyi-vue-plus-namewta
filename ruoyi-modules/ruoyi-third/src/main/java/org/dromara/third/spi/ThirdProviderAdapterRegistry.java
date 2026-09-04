package org.dromara.third.spi;

import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ThirdProviderAdapterRegistry {
    private final Map<String, ThirdProviderAdapter> adapters;

    public ThirdProviderAdapterRegistry(List<ThirdProviderAdapter> values) {
        this.adapters = values.stream().collect(Collectors.toUnmodifiableMap(
            value -> canonical(value.providerCode()), Function.identity(), (left, right) -> {
                throw new ServiceException("Duplicate third provider adapter: " + left.providerCode());
            }));
    }

    public ThirdProviderAdapter find(String providerCode) {
        return providerCode == null ? null : adapters.get(providerCode.trim());
    }

    private static String canonical(String providerCode) {
        String value = providerCode == null ? "" : providerCode.trim();
        if (value.isBlank()) throw new ServiceException("Third provider adapter code is required");
        return value;
    }
}

package org.dromara.profile.enterprise.domain.verification;

public record EnterpriseProviderCallbackEnvelope(
    String providerRequestId,
    long timestampEpochSecond,
    String payload,
    String signature
) {
}

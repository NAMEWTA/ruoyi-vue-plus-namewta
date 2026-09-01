package org.dromara.profile.enterprise.verification;

public record EnterpriseProviderCallbackEnvelope(
    String providerRequestId,
    long timestampEpochSecond,
    String payload,
    String signature
) {
}

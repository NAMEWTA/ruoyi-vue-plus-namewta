package org.dromara.profile.person.verification;

public record PersonProviderCallbackEnvelope(
    String providerRequestId,
    long timestampEpochSecond,
    String payload,
    String signature
) {
}

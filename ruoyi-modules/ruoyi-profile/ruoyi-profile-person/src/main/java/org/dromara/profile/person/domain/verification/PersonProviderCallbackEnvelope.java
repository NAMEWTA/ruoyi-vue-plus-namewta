package org.dromara.profile.person.domain.verification;

public record PersonProviderCallbackEnvelope(
    String providerRequestId,
    long timestampEpochSecond,
    String payload,
    String signature
) {
}

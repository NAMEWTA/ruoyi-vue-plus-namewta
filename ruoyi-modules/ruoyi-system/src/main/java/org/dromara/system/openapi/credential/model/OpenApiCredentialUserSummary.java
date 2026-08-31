package org.dromara.system.openapi.credential.model;

/**
 * Super-admin search row with an optional safe credential summary.
 */
public record OpenApiCredentialUserSummary(
    Long userId,
    String userName,
    String nickName,
    OpenApiCredentialSummary credential
) {
}

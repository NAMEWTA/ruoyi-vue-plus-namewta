package org.dromara.profile.enterprise.domain.verification;

/** EnterpriseVerificationCallbackOutcome 认证领域模型。 */
public enum EnterpriseVerificationCallbackOutcome {
    ACCEPTED,
    IDEMPOTENT,
    LATE_IGNORED
}

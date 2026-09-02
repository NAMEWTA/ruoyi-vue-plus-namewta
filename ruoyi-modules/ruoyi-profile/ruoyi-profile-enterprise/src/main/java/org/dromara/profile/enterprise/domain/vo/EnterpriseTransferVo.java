package org.dromara.profile.enterprise.domain.vo;

public record EnterpriseTransferVo(String status, String challengeId, Long expiresInSeconds) {

        public static EnterpriseTransferVo sent(String challengeId) {
            return new EnterpriseTransferVo("SENT", challengeId, 300L);
        }

        public static EnterpriseTransferVo status(String status) {
            return new EnterpriseTransferVo(status, null, null);
        }
}

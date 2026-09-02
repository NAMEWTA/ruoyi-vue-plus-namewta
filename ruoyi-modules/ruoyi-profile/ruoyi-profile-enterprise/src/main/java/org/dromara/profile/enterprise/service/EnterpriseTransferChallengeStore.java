package org.dromara.profile.enterprise.service;

import org.dromara.profile.enterprise.domain.transfer.EnterpriseTransferChallenge;
public interface EnterpriseTransferChallengeStore {

    StageResult stage(EnterpriseTransferChallenge challenge);

    boolean activate(String challengeId);

    Verification verify(String challengeId, long sourceUserId, String code);

    boolean consume(VerifiedChallenge verifiedChallenge);

    void revoke(String challengeId);

    enum StageResult {
        STAGED,
        RATE_LIMITED
    }

    enum VerificationStatus {
        VERIFIED,
        INVALID
    }

    record Verification(VerificationStatus status, VerifiedChallenge verified) {
    }

    record VerifiedChallenge(EnterpriseTransferChallenge challenge, String storageToken) {
    }
}

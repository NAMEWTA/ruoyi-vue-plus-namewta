package org.dromara.profile.enterprise.transfer;

public record EnterpriseTransferChallenge(
    String challengeId,
    long sourceUserId,
    long targetUserId,
    long enterpriseProfileId,
    long sourceBindingId,
    int sourceBindingVersion,
    long personProfileId,
    String fullName,
    String documentLastFour,
    String phone,
    String codeHash,
    State state,
    int attempts,
    long expiresAtEpochMilli
) {

    public enum State {
        PENDING_DELIVERY,
        ACTIVE
    }

    public EnterpriseTransferChallenge activate() {
        return with(State.ACTIVE, attempts);
    }

    public EnterpriseTransferChallenge failedAttempt() {
        return with(state, attempts + 1);
    }

    private EnterpriseTransferChallenge with(State nextState, int nextAttempts) {
        return new EnterpriseTransferChallenge(challengeId, sourceUserId, targetUserId, enterpriseProfileId,
            sourceBindingId, sourceBindingVersion, personProfileId, fullName, documentLastFour, phone,
            codeHash, nextState, nextAttempts, expiresAtEpochMilli);
    }
}

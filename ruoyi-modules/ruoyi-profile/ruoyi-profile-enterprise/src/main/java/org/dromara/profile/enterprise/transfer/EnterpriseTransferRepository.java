package org.dromara.profile.enterprise.transfer;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EnterpriseTransferRepository {

    Optional<OwnerBinding> currentActiveOwner(long userId);

    List<TargetIdentity> findExactTargets(String fullName, String documentLastFour, String phone);

    boolean hasEffectiveEnterpriseBinding(long userId);

    void recordChallenge(EnterpriseTransferChallenge challenge, Instant occurredTime);

    void transfer(EnterpriseTransferChallenge challenge, Instant occurredTime);

    long unbind(long userId, Instant occurredTime);

    record OwnerBinding(long bindingId, long profileId, long userId, int version) {
    }

    record TargetIdentity(long userId, long personProfileId, String phone) {
    }
}

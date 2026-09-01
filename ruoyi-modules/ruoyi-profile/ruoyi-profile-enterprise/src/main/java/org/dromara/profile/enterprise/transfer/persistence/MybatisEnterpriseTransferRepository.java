package org.dromara.profile.enterprise.transfer.persistence;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.dromara.profile.enterprise.transfer.EnterpriseTransferChallenge;
import org.dromara.profile.enterprise.transfer.EnterpriseTransferException;
import org.dromara.profile.enterprise.transfer.EnterpriseTransferRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class MybatisEnterpriseTransferRepository implements EnterpriseTransferRepository {

    private final EnterpriseTransferMapper mapper;

    public MybatisEnterpriseTransferRepository(EnterpriseTransferMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<OwnerBinding> currentActiveOwner(long userId) {
        return Optional.ofNullable(mapper.selectActiveOwner(userId)).map(this::owner);
    }

    @Override
    public List<TargetIdentity> findExactTargets(String fullName, String documentLastFour, String phone) {
        return mapper.selectExactTargets(fullName, documentLastFour, phone).stream()
            .map(row -> new TargetIdentity(row.getUserId(), row.getPersonProfileId(), row.getPhone()))
            .toList();
    }

    @Override
    public boolean hasEffectiveEnterpriseBinding(long userId) {
        return mapper.countEffectiveBinding(userId) > 0;
    }

    @Override
    public void recordChallenge(EnterpriseTransferChallenge challenge, Instant occurredTime) {
        try {
            changed(mapper.insertTransferRecord(IdWorker.getId(), challenge.enterpriseProfileId(),
                challenge.sourceBindingId(), challenge.sourceUserId(), challenge.targetUserId(),
                challenge.challengeId(), challenge.sourceBindingVersion(),
                Instant.ofEpochMilli(challenge.expiresAtEpochMilli()), occurredTime),
                "ENTERPRISE_TRANSFER_RECORD_CONFLICT");
        } catch (DuplicateKeyException exception) {
            throw new EnterpriseTransferException("ENTERPRISE_TRANSFER_RECORD_CONFLICT", exception);
        }
    }

    @Override
    public void transfer(EnterpriseTransferChallenge challenge, Instant occurredTime) {
        EnterpriseTransferOwnerRow source = mapper.lockActiveOwner(challenge.sourceUserId());
        if (source == null || source.getBindingId() != challenge.sourceBindingId()
            || source.getProfileId() != challenge.enterpriseProfileId()
            || source.getBindingVersion() != challenge.sourceBindingVersion()) {
            throw failure("ENTERPRISE_TRANSFER_SOURCE_CHANGED");
        }
        EnterpriseTransferTargetRow target = mapper.lockExactTarget(challenge.targetUserId(),
            challenge.personProfileId(), challenge.fullName(), challenge.documentLastFour(), challenge.phone());
        if (target == null || mapper.lockEffectiveBindingId(challenge.targetUserId()) != null) {
            throw failure("ENTERPRISE_TRANSFER_TARGET_INELIGIBLE");
        }

        try {
            changed(mapper.unbindSource(source.getBindingId(), source.getProfileId(), source.getUserId(),
                source.getBindingVersion(), occurredTime, challenge.sourceUserId()),
                "ENTERPRISE_TRANSFER_SOURCE_CHANGED");
            event(source.getBindingId(), source.getProfileId(), source.getUserId(), "UNBOUND",
                source.getBindingVersion() + 1, "SELF_TRANSFER", source.getBindingId(),
                "TRANSFER_CHALLENGE:" + challenge.challengeId(), occurredTime, challenge.sourceUserId());

            long newBindingId = IdWorker.getId();
            changed(mapper.insertBinding(newBindingId, source.getProfileId(), challenge.targetUserId(),
                "SELF_TRANSFER", source.getBindingId(), occurredTime, challenge.sourceUserId()),
                "ENTERPRISE_TRANSFER_BINDING_CONFLICT");
            event(newBindingId, source.getProfileId(), challenge.targetUserId(), "ACTIVE", 1,
                "SELF_TRANSFER", source.getBindingId(), "TRANSFER_CHALLENGE:" + challenge.challengeId(),
                occurredTime, challenge.sourceUserId());
            changed(mapper.confirmTransferRecord(challenge.challengeId(), source.getProfileId(),
                source.getBindingId(), source.getUserId(), challenge.targetUserId(), source.getBindingVersion(),
                occurredTime, challenge.sourceUserId()), "ENTERPRISE_TRANSFER_RECORD_CHANGED");
        } catch (DuplicateKeyException exception) {
            throw new EnterpriseTransferException("ENTERPRISE_TRANSFER_BINDING_CONFLICT", exception);
        }
    }

    @Override
    public long unbind(long userId, Instant occurredTime) {
        EnterpriseTransferOwnerRow source = mapper.lockActiveOwner(userId);
        if (source == null) {
            throw failure("ENTERPRISE_TRANSFER_SOURCE_NOT_ACTIVE");
        }
        changed(mapper.unbindSource(source.getBindingId(), source.getProfileId(), source.getUserId(),
            source.getBindingVersion(), occurredTime, userId), "ENTERPRISE_TRANSFER_SOURCE_CHANGED");
        event(source.getBindingId(), source.getProfileId(), source.getUserId(), "UNBOUND",
            source.getBindingVersion() + 1, "SELF_UNBIND", source.getBindingId(),
            "RESPONSIBLE_SELF_UNBIND", occurredTime, userId);
        return source.getProfileId();
    }

    private void event(long bindingId, long profileId, long userId, String eventType, int bindingVersion,
                       String sourceType, long sourceId, String reason, Instant occurredTime, long operatorId) {
        changed(mapper.insertEvent(IdWorker.getId(), bindingId, profileId, userId, eventType, bindingVersion,
            sourceType, sourceId, reason, occurredTime, operatorId), "ENTERPRISE_TRANSFER_EVENT_CONFLICT");
    }

    private OwnerBinding owner(EnterpriseTransferOwnerRow row) {
        return new OwnerBinding(row.getBindingId(), row.getProfileId(), row.getUserId(), row.getBindingVersion());
    }

    private void changed(int count, String category) {
        if (count != 1) {
            throw failure(category);
        }
    }

    private EnterpriseTransferException failure(String category) {
        return new EnterpriseTransferException(category);
    }
}

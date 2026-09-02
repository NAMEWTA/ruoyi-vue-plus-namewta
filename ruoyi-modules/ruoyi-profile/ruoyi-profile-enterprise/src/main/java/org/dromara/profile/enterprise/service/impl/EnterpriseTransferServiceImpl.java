package org.dromara.profile.enterprise.service.impl;

import org.dromara.profile.enterprise.service.IEnterpriseTransferService;
import org.dromara.profile.enterprise.service.EnterpriseTransferChallengeStore;
import org.dromara.profile.enterprise.mapper.EnterpriseTransferMapper;
import org.dromara.profile.enterprise.domain.vo.EnterpriseTransferOwnerRow;
import org.dromara.profile.enterprise.domain.exception.EnterpriseTransferException;
import org.dromara.profile.enterprise.domain.transfer.EnterpriseTransferChallenge;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import cn.hutool.crypto.digest.BCrypt;
import org.dromara.common.notify.core.NotifyClient;
import org.dromara.common.notify.model.NotifyAuditPolicy;
import org.dromara.common.notify.model.NotifyChannel;
import org.dromara.common.notify.model.NotifyRequest;
import org.dromara.common.notify.model.NotifyResult;
import org.dromara.common.notify.model.NotifyStatus;
import org.dromara.common.notify.model.NotifyTarget;
import org.dromara.common.notify.model.NotifyTextContent;
import org.dromara.profile.api.person.PersonIdentityLookupService;
import org.dromara.profile.api.person.PersonIdentityLookupService.ActiveIdentityLock;
import org.dromara.profile.api.person.PersonIdentityLookupService.ActiveIdentityMatch;
import org.dromara.profile.api.person.PersonIdentityLookupService.ActiveIdentityQuery;
import org.dromara.profile.enterprise.service.EnterpriseTransferChallengeStore.StageResult;
import org.dromara.profile.enterprise.service.EnterpriseTransferChallengeStore.Verification;
import org.dromara.profile.enterprise.service.EnterpriseTransferChallengeStore.VerificationStatus;
import org.dromara.profile.enterprise.domain.bo.EnterpriseTransferConfirmBo;
import org.dromara.profile.enterprise.domain.bo.EnterpriseTransferSendBo;
import org.dromara.profile.enterprise.domain.vo.EnterpriseTransferVo;
import org.dromara.system.api.UserService;
import org.dromara.system.api.domain.UserDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import com.baomidou.dynamic.datasource.annotation.DSTransactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EnterpriseTransferServiceImpl implements IEnterpriseTransferService {

    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);

    private final EnterpriseTransferMapper mapper;
    private final EnterpriseTransferChallengeStore challenges;
    private final EnterpriseTransferCodeGenerator codes;
    private final PersonIdentityLookupService personIdentities;
    private final UserService users;
    private final NotifyClient notify;
    private final Clock clock;

    @Autowired
    public EnterpriseTransferServiceImpl(EnterpriseTransferMapper mapper,
                                     EnterpriseTransferChallengeStore challenges,
                                     EnterpriseTransferCodeGenerator codes,
                                     PersonIdentityLookupService personIdentities,
                                     UserService users,
                                     NotifyClient notify) {
        this(mapper, challenges, codes, personIdentities, users, notify, Clock.systemUTC());
    }

    EnterpriseTransferServiceImpl(EnterpriseTransferMapper mapper,
                              EnterpriseTransferChallengeStore challenges,
                              EnterpriseTransferCodeGenerator codes,
                              PersonIdentityLookupService personIdentities,
                              UserService users,
                              NotifyClient notify,
                              Clock clock) {
        this.mapper = mapper;
        this.challenges = challenges;
        this.codes = codes;
        this.personIdentities = personIdentities;
        this.users = users;
        this.notify = notify;
        this.clock = clock;
    }

    @DSTransactional
    @Override
    public EnterpriseTransferVo send(long sourceUserId, EnterpriseTransferSendBo command) {
        OwnerBinding owner = currentActiveOwner(sourceUserId).orElse(null);
        String fullName = command.fullName().trim();
        String documentLastFour = command.documentLastFour().trim().toUpperCase(Locale.ROOT);
        String phone = command.phone().trim();
        List<TargetIdentity> matches = exactTargets(fullName, documentLastFour, phone);
        if (owner == null || matches.size() != 1 || matches.getFirst().userId() == sourceUserId) {
            return EnterpriseTransferVo.status("NOT_AVAILABLE");
        }
        TargetIdentity target = matches.getFirst();
        if (hasEffectiveEnterpriseBinding(target.userId())) {
            return EnterpriseTransferVo.status("NOT_AVAILABLE");
        }

        String challengeId = UUID.randomUUID().toString();
        String code = codes.generate();
        Instant expiresAt = clock.instant().plus(CHALLENGE_TTL);
        EnterpriseTransferChallenge challenge = new EnterpriseTransferChallenge(challengeId, sourceUserId,
            target.userId(), owner.profileId(), owner.bindingId(), owner.version(), target.personProfileId(),
            fullName, documentLastFour, target.phone(),
            BCrypt.hashpw(code), EnterpriseTransferChallenge.State.PENDING_DELIVERY, 0, expiresAt.toEpochMilli());
        if (challenges.stage(challenge) != StageResult.STAGED) {
            throw failure("ENTERPRISE_TRANSFER_RATE_LIMITED");
        }

        NotifyResult result;
        try {
            result = notify.send(NotifyRequest.builder()
                .requestId("enterprise-transfer-" + challengeId)
                .bizType("profile_enterprise_transfer")
                .bizId(challengeId)
                .channel(NotifyChannel.SMS)
                .targets(List.of(NotifyTarget.phone(target.phone())))
                .content(new NotifyTextContent("企业负责人转移验证码",
                    "您的企业负责人转移验证码为：" + code + "，5分钟内有效。"))
                .auditPolicy(NotifyAuditPolicy.REDACT_SENSITIVE)
                .idempotencyKey("profile:enterprise:transfer:" + challengeId)
                .idempotencyWindow(CHALLENGE_TTL)
                .build());
        } catch (RuntimeException exception) {
            challenges.revoke(challengeId);
            throw new EnterpriseTransferException("ENTERPRISE_TRANSFER_DELIVERY_FAILED", exception);
        }
        if (!delivered(result)) {
            challenges.revoke(challengeId);
            throw failure("ENTERPRISE_TRANSFER_DELIVERY_FAILED");
        }
        try {
            recordChallenge(challenge, clock.instant());
            if (!challenges.activate(challengeId)) {
                throw failure("ENTERPRISE_TRANSFER_CHALLENGE_STATE_FAILURE");
            }
        } catch (RuntimeException exception) {
            challenges.revoke(challengeId);
            throw exception;
        }
        return EnterpriseTransferVo.sent(challengeId);
    }

    @DSTransactional
    @Override
    public EnterpriseTransferVo confirm(long sourceUserId, EnterpriseTransferConfirmBo command) {
        Verification verification = challenges.verify(command.challengeId(), sourceUserId, command.code());
        if (verification.status() != VerificationStatus.VERIFIED || verification.verified() == null) {
            throw failure("ENTERPRISE_TRANSFER_CHALLENGE_INVALID");
        }
        EnterpriseTransferChallenge challenge = verification.verified().challenge();
        ActiveIdentityLock lock = new ActiveIdentityLock(challenge.targetUserId(), challenge.personProfileId(),
            challenge.fullName(), challenge.documentLastFour());
        ActiveIdentityMatch identity = personIdentities.lockActiveExactMatch(lock).orElse(null);
        if (identity == null || !eligibleLockedAccount(identity, challenge.phone())
            || hasEffectiveEnterpriseBinding(identity.userId())) {
            throw failure("ENTERPRISE_TRANSFER_TARGET_INELIGIBLE");
        }
        transferBindings(challenge, clock.instant());
        if (!challenges.consume(verification.verified())) {
            throw failure("ENTERPRISE_TRANSFER_CHALLENGE_INVALID");
        }
        return EnterpriseTransferVo.status("TRANSFERRED");
    }

    @DSTransactional
    @Override
    public EnterpriseTransferVo unbind(long userId) {
        unbindBinding(userId, clock.instant());
        return EnterpriseTransferVo.status("UNBOUND");
    }

    Optional<OwnerBinding> currentActiveOwner(long userId) {
        return Optional.ofNullable(mapper.selectActiveOwner(userId)).map(this::owner);
    }

    boolean hasEffectiveEnterpriseBinding(long userId) {
        return mapper.countEffectiveBinding(userId) > 0;
    }

    void recordChallenge(EnterpriseTransferChallenge challenge, Instant occurredTime) {
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

    void transferBindings(EnterpriseTransferChallenge challenge, Instant occurredTime) {
        EnterpriseTransferOwnerRow source = mapper.lockActiveOwner(challenge.sourceUserId());
        if (source == null || source.getBindingId() != challenge.sourceBindingId()
            || source.getProfileId() != challenge.enterpriseProfileId()
            || source.getBindingVersion() != challenge.sourceBindingVersion()) {
            throw failure("ENTERPRISE_TRANSFER_SOURCE_CHANGED");
        }
        if (mapper.lockEffectiveBindingId(challenge.targetUserId()) != null) {
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

    long unbindBinding(long userId, Instant occurredTime) {
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

    private List<TargetIdentity> exactTargets(String fullName, String documentLastFour, String phone) {
        List<ActiveIdentityMatch> identities = personIdentities.findActiveExactMatches(
            new ActiveIdentityQuery(fullName, documentLastFour));
        if (identities == null || identities.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = identities.stream().map(ActiveIdentityMatch::userId).collect(Collectors.toSet());
        Map<Long, UserDTO> accounts = safeUsers(users.selectListByIds(userIds)).stream()
            .filter(user -> user != null && user.getUserId() != null)
            .collect(Collectors.toMap(UserDTO::getUserId, Function.identity(), (left, right) -> left));
        return identities.stream()
            .filter(identity -> activeWithPhone(accounts.get(identity.userId()), phone))
            .map(identity -> new TargetIdentity(identity.userId(), identity.personProfileId(), phone))
            .toList();
    }

    private boolean eligibleLockedAccount(ActiveIdentityMatch identity, String phone) {
        UserDTO user = users.lockActiveById(identity.userId());
        return user != null && Objects.equals(identity.userId(), user.getUserId())
            && activeWithPhone(user, phone);
    }

    private Collection<UserDTO> safeUsers(List<UserDTO> accounts) {
        return accounts == null ? List.of() : accounts;
    }

    private boolean activeWithPhone(UserDTO user, String phone) {
        return user != null && "0".equals(user.getStatus()) && phone.equals(user.getPhoneNumber());
    }

    private boolean delivered(NotifyResult result) {
        return result != null && (result.status() == NotifyStatus.ACCEPTED
            || result.status() == NotifyStatus.SKIPPED_DUPLICATE);
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

    private record TargetIdentity(long userId, long personProfileId, String phone) {
    }

    record OwnerBinding(long bindingId, long profileId, long userId, int version) {
    }
}

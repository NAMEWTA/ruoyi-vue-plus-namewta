package org.dromara.profile.enterprise.transfer;

import cn.hutool.crypto.digest.BCrypt;
import org.dromara.common.notify.core.NotifyClient;
import org.dromara.common.notify.model.NotifyAuditPolicy;
import org.dromara.common.notify.model.NotifyChannel;
import org.dromara.common.notify.model.NotifyRequest;
import org.dromara.common.notify.model.NotifyResult;
import org.dromara.common.notify.model.NotifyStatus;
import org.dromara.common.notify.model.NotifyTarget;
import org.dromara.common.notify.model.NotifyTextContent;
import org.dromara.profile.api.ProfileService;
import org.dromara.profile.api.domain.ProfileSummary;
import org.dromara.profile.enterprise.transfer.EnterpriseTransferChallengeStore.StageResult;
import org.dromara.profile.enterprise.transfer.EnterpriseTransferChallengeStore.Verification;
import org.dromara.profile.enterprise.transfer.EnterpriseTransferChallengeStore.VerificationStatus;
import org.dromara.profile.enterprise.transfer.EnterpriseTransferContracts.ConfirmCommand;
import org.dromara.profile.enterprise.transfer.EnterpriseTransferContracts.SendCommand;
import org.dromara.profile.enterprise.transfer.EnterpriseTransferContracts.TransferView;
import org.dromara.profile.enterprise.transfer.EnterpriseTransferRepository.OwnerBinding;
import org.dromara.profile.enterprise.transfer.EnterpriseTransferRepository.TargetIdentity;
import org.dromara.system.api.UserService;
import org.dromara.system.api.domain.UserDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class EnterpriseTransferService {

    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);

    private final EnterpriseTransferRepository repository;
    private final EnterpriseTransferChallengeStore challenges;
    private final EnterpriseTransferCodeGenerator codes;
    private final ProfileService profiles;
    private final UserService users;
    private final NotifyClient notify;
    private final Clock clock;

    public EnterpriseTransferService(EnterpriseTransferRepository repository,
                                     EnterpriseTransferChallengeStore challenges,
                                     EnterpriseTransferCodeGenerator codes,
                                     ProfileService profiles,
                                     UserService users,
                                     NotifyClient notify) {
        this(repository, challenges, codes, profiles, users, notify, Clock.systemUTC());
    }

    EnterpriseTransferService(EnterpriseTransferRepository repository,
                              EnterpriseTransferChallengeStore challenges,
                              EnterpriseTransferCodeGenerator codes,
                              ProfileService profiles,
                              UserService users,
                              NotifyClient notify,
                              Clock clock) {
        this.repository = repository;
        this.challenges = challenges;
        this.codes = codes;
        this.profiles = profiles;
        this.users = users;
        this.notify = notify;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public TransferView send(long sourceUserId, SendCommand command) {
        OwnerBinding owner = repository.currentActiveOwner(sourceUserId).orElse(null);
        List<TargetIdentity> matches = repository.findExactTargets(command.fullName().trim(),
            command.documentLastFour().trim().toUpperCase(), command.phone().trim());
        if (owner == null || matches.size() != 1 || matches.getFirst().userId() == sourceUserId) {
            return TransferView.status("NOT_AVAILABLE");
        }
        TargetIdentity target = matches.getFirst();
        if (!eligible(target)) {
            return TransferView.status("NOT_AVAILABLE");
        }

        String challengeId = UUID.randomUUID().toString();
        String code = codes.generate();
        Instant expiresAt = clock.instant().plus(CHALLENGE_TTL);
        EnterpriseTransferChallenge challenge = new EnterpriseTransferChallenge(challengeId, sourceUserId,
            target.userId(), owner.profileId(), owner.bindingId(), owner.version(), target.personProfileId(),
            command.fullName().trim(), command.documentLastFour().trim().toUpperCase(), target.phone(),
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
            repository.recordChallenge(challenge, clock.instant());
            if (!challenges.activate(challengeId)) {
                throw failure("ENTERPRISE_TRANSFER_CHALLENGE_STATE_FAILURE");
            }
        } catch (RuntimeException exception) {
            challenges.revoke(challengeId);
            throw exception;
        }
        return TransferView.sent(challengeId);
    }

    @Transactional(rollbackFor = Exception.class)
    public TransferView confirm(long sourceUserId, ConfirmCommand command) {
        Verification verification = challenges.verify(command.challengeId(), sourceUserId, command.code());
        if (verification.status() != VerificationStatus.VERIFIED || verification.verified() == null) {
            throw failure("ENTERPRISE_TRANSFER_CHALLENGE_INVALID");
        }
        EnterpriseTransferChallenge challenge = verification.verified().challenge();
        if (!eligible(new TargetIdentity(challenge.targetUserId(), challenge.personProfileId(), challenge.phone()))) {
            throw failure("ENTERPRISE_TRANSFER_TARGET_INELIGIBLE");
        }
        repository.transfer(challenge, clock.instant());
        if (!challenges.consume(verification.verified())) {
            throw failure("ENTERPRISE_TRANSFER_CHALLENGE_INVALID");
        }
        return TransferView.status("TRANSFERRED");
    }

    @Transactional(rollbackFor = Exception.class)
    public TransferView unbind(long userId) {
        repository.unbind(userId, clock.instant());
        return TransferView.status("UNBOUND");
    }

    private boolean eligible(TargetIdentity target) {
        if (repository.hasEffectiveEnterpriseBinding(target.userId())) {
            return false;
        }
        UserDTO user = users.selectById(target.userId());
        if (user == null || !"0".equals(user.getStatus()) || !target.phone().equals(user.getPhoneNumber())) {
            return false;
        }
        ProfileSummary summary = profiles.findByUserId(target.userId());
        return summary.person() != null && summary.person().profileId().equals(target.personProfileId());
    }

    private boolean delivered(NotifyResult result) {
        return result != null && (result.status() == NotifyStatus.ACCEPTED
            || result.status() == NotifyStatus.SKIPPED_DUPLICATE);
    }

    private EnterpriseTransferException failure(String category) {
        return new EnterpriseTransferException(category);
    }
}

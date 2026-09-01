package org.dromara.profile.enterprise.transfer;

import cn.hutool.crypto.digest.BCrypt;
import org.dromara.common.notify.core.NotifyClient;
import org.dromara.common.notify.model.NotifyAuditPolicy;
import org.dromara.common.notify.model.NotifyChannel;
import org.dromara.common.notify.model.NotifyRequest;
import org.dromara.common.notify.model.NotifyResult;
import org.dromara.common.notify.model.NotifyStatus;
import org.dromara.profile.api.ProfileService;
import org.dromara.profile.api.domain.ProfileBindingSummary;
import org.dromara.profile.api.domain.ProfileSummary;
import org.dromara.profile.api.domain.ProfileType;
import org.dromara.profile.enterprise.transfer.EnterpriseTransferChallengeStore.StageResult;
import org.dromara.profile.enterprise.transfer.EnterpriseTransferChallengeStore.Verification;
import org.dromara.profile.enterprise.transfer.EnterpriseTransferChallengeStore.VerificationStatus;
import org.dromara.profile.enterprise.transfer.EnterpriseTransferChallengeStore.VerifiedChallenge;
import org.dromara.profile.enterprise.transfer.EnterpriseTransferContracts.ConfirmCommand;
import org.dromara.profile.enterprise.transfer.EnterpriseTransferContracts.SendCommand;
import org.dromara.profile.enterprise.transfer.EnterpriseTransferRepository.OwnerBinding;
import org.dromara.profile.enterprise.transfer.EnterpriseTransferRepository.TargetIdentity;
import org.dromara.system.api.UserService;
import org.dromara.system.api.domain.UserDTO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("dev")
class EnterpriseTransferServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-01T15:00:00Z");

    private final EnterpriseTransferRepository repository = mock(EnterpriseTransferRepository.class);
    private final EnterpriseTransferChallengeStore challenges = mock(EnterpriseTransferChallengeStore.class);
    private final EnterpriseTransferCodeGenerator codes = mock(EnterpriseTransferCodeGenerator.class);
    private final ProfileService profiles = mock(ProfileService.class);
    private final UserService users = mock(UserService.class);
    private final NotifyClient notify = mock(NotifyClient.class);
    private final EnterpriseTransferService service = new EnterpriseTransferService(repository, challenges,
        codes, profiles, users, notify, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void sendsCodeWithRedactedAuditThenActivatesTheStagedChallenge() {
        eligibleTarget();
        when(codes.generate()).thenReturn("123456");
        when(challenges.stage(any())).thenReturn(StageResult.STAGED);
        when(notify.send(any())).thenReturn(new NotifyResult("enterprise-transfer-challenge-1",
            NotifyChannel.SMS, "sms-provider", NotifyStatus.ACCEPTED, List.of()));
        when(challenges.activate(any())).thenReturn(true);

        var view = service.send(101L, new SendCommand("张三", "3001", "13800138000"));

        assertThat(view.status()).isEqualTo("SENT");
        assertThat(view.challengeId()).isNotBlank();
        assertThat(view.expiresInSeconds()).isEqualTo(300);
        ArgumentCaptor<EnterpriseTransferChallenge> challenge =
            ArgumentCaptor.forClass(EnterpriseTransferChallenge.class);
        verify(challenges).stage(challenge.capture());
        assertThat(challenge.getValue().state()).isEqualTo(EnterpriseTransferChallenge.State.PENDING_DELIVERY);
        assertThat(BCrypt.checkpw("123456", challenge.getValue().codeHash())).isTrue();
        assertThat(challenge.getValue().sourceBindingVersion()).isEqualTo(7);
        ArgumentCaptor<NotifyRequest> request = ArgumentCaptor.forClass(NotifyRequest.class);
        verify(notify).send(request.capture());
        assertThat(request.getValue().auditPolicy()).isEqualTo(NotifyAuditPolicy.REDACT_SENSITIVE);
        assertThat(request.getValue().content().contentSnapshot()).contains("123456");
        verify(challenges).activate(view.challengeId());
    }

    @Test
    void returnsTheSameMinimalResultWhenTheThreeTargetFactorsDoNotMatch() {
        when(repository.currentActiveOwner(101L)).thenReturn(Optional.of(owner()));
        when(repository.findExactTargets("张三", "3001", "13800138000")).thenReturn(List.of());

        var view = service.send(101L, new SendCommand("张三", "3001", "13800138000"));

        assertThat(view.status()).isEqualTo("NOT_AVAILABLE");
        assertThat(view.challengeId()).isNull();
        verifyNoInteractions(challenges, codes, profiles, users, notify);
    }

    @Test
    void failedSmsNeverLeavesAConfirmableChallenge() {
        eligibleTarget();
        when(codes.generate()).thenReturn("123456");
        when(challenges.stage(any())).thenReturn(StageResult.STAGED);
        when(notify.send(any())).thenReturn(new NotifyResult("enterprise-transfer-challenge-1",
            NotifyChannel.SMS, "sms-provider", NotifyStatus.FAILED, List.of()));

        assertThatThrownBy(() -> service.send(101L,
            new SendCommand("张三", "3001", "13800138000")))
            .isInstanceOf(EnterpriseTransferException.class)
            .hasMessage("ENTERPRISE_TRANSFER_DELIVERY_FAILED");
        verify(challenges).revoke(any());
        verify(challenges, never()).activate(any());
    }

    @Test
    void confirmsOnlyAfterEligibilityRecheckAndConsumesExactlyOnce() {
        EnterpriseTransferChallenge challenge = challenge(EnterpriseTransferChallenge.State.ACTIVE);
        when(challenges.verify("challenge-1", 101L, "123456"))
            .thenReturn(new Verification(VerificationStatus.VERIFIED,
                new VerifiedChallenge(challenge, "stored-token")));
        eligibleAccountOnly();
        when(challenges.consume(any())).thenReturn(true);

        var view = service.confirm(101L, new ConfirmCommand("challenge-1", "123456"));

        assertThat(view.status()).isEqualTo("TRANSFERRED");
        verify(repository).transfer(challenge, NOW);
        verify(challenges).consume(any());
    }

    @Test
    void invalidOrReplayedCodeNeverTouchesBindings() {
        when(challenges.verify("challenge-1", 101L, "000000"))
            .thenReturn(new Verification(VerificationStatus.INVALID, null));

        assertThatThrownBy(() -> service.confirm(101L,
            new ConfirmCommand("challenge-1", "000000")))
            .isInstanceOf(EnterpriseTransferException.class)
            .hasMessage("ENTERPRISE_TRANSFER_CHALLENGE_INVALID");
        verifyNoInteractions(repository, profiles, users, notify);
    }

    @Test
    void consumeRaceRollsBackInsteadOfReportingASecondTransfer() {
        EnterpriseTransferChallenge challenge = challenge(EnterpriseTransferChallenge.State.ACTIVE);
        when(challenges.verify("challenge-1", 101L, "123456"))
            .thenReturn(new Verification(VerificationStatus.VERIFIED,
                new VerifiedChallenge(challenge, "stored-token")));
        eligibleAccountOnly();
        when(challenges.consume(any())).thenReturn(false);

        assertThatThrownBy(() -> service.confirm(101L,
            new ConfirmCommand("challenge-1", "123456")))
            .isInstanceOf(EnterpriseTransferException.class)
            .hasMessage("ENTERPRISE_TRANSFER_CHALLENGE_INVALID");
        verify(repository).transfer(challenge, NOW);
    }

    @Test
    void currentResponsibleCanUnbindWithoutWorkflowOrDeletion() {
        when(repository.unbind(101L, NOW)).thenReturn(9201L);

        assertThat(service.unbind(101L).status()).isEqualTo("UNBOUND");
        verify(repository).unbind(101L, NOW);
        verifyNoInteractions(challenges, codes, profiles, users, notify);
    }

    private void eligibleTarget() {
        when(repository.currentActiveOwner(101L)).thenReturn(Optional.of(owner()));
        when(repository.findExactTargets("张三", "3001", "13800138000"))
            .thenReturn(List.of(target()));
        eligibleAccountOnly();
    }

    private void eligibleAccountOnly() {
        when(repository.hasEffectiveEnterpriseBinding(202L)).thenReturn(false);
        UserDTO user = new UserDTO();
        user.setUserId(202L);
        user.setStatus("0");
        user.setPhoneNumber("13800138000");
        when(users.selectById(202L)).thenReturn(user);
        when(profiles.findByUserId(202L)).thenReturn(new ProfileSummary(202L,
            new ProfileBindingSummary(8201L, ProfileType.PERSON, NOW.minusSeconds(3600)), null));
    }

    private OwnerBinding owner() {
        return new OwnerBinding(9101L, 9201L, 101L, 7);
    }

    private TargetIdentity target() {
        return new TargetIdentity(202L, 8201L, "13800138000");
    }

    private EnterpriseTransferChallenge challenge(EnterpriseTransferChallenge.State state) {
        return new EnterpriseTransferChallenge("challenge-1", 101L, 202L, 9201L, 9101L, 7,
            8201L, "张三", "3001", "13800138000", "$2a$10$redacted", state, 0,
            NOW.plusSeconds(300).toEpochMilli());
    }
}

package org.dromara.profile.person.rebind;

import org.dromara.common.notify.core.NotifyClient;
import org.dromara.common.notify.model.NotifyChannel;
import org.dromara.common.notify.model.NotifyResult;
import org.dromara.common.notify.model.NotifyStatus;
import org.dromara.profile.person.notification.PersonNotificationAuditMapper;
import org.dromara.profile.person.notification.PersonNotificationAuditMapper.NotificationAuditRow;
import org.dromara.profile.person.notification.PersonRebindNotificationService;
import org.dromara.system.api.MessageService;
import org.dromara.system.api.UserService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class PersonRebindNotificationTest {

    private final PersonNotificationAuditMapper audits = mock(PersonNotificationAuditMapper.class);
    private final MessageService messages = mock(MessageService.class);
    private final UserService users = mock(UserService.class);
    private final NotifyClient notify = mock(NotifyClient.class);
    private final PersonRebindNotificationService service =
        new PersonRebindNotificationService(audits, messages, users, notify);

    @Test
    void sendsBothSafeChannelsWithoutIdentityOrNewAccountData() {
        when(users.selectPhonenumberById(202L)).thenReturn("13800138000");
        when(notify.send(any())).thenReturn(new NotifyResult("sms-person-rebind-9001", NotifyChannel.SMS,
            "sms-provider", NotifyStatus.ACCEPTED, List.of()));
        when(audits.insert(any())).thenReturn(1);

        service.notifyOldAccount(new PersonReboundEvent(9201L, 9001L, 202L));

        verify(messages).sendMessage(202L, "您的个人实名认证绑定已变更。如非本人操作，请联系平台。");
        ArgumentCaptor<org.dromara.common.notify.model.NotifyRequest> request =
            ArgumentCaptor.forClass(org.dromara.common.notify.model.NotifyRequest.class);
        verify(notify).send(request.capture());
        assertThat(request.getValue().content().contentSnapshot())
            .doesNotContain("张三", "110101", "101");
        assertThat(request.getValue().auditPolicy().name()).isEqualTo("REDACT_SENSITIVE");
        ArgumentCaptor<NotificationAuditRow> audit = ArgumentCaptor.forClass(NotificationAuditRow.class);
        verify(audits, org.mockito.Mockito.times(2)).insert(audit.capture());
        assertThat(audit.getAllValues()).extracting(NotificationAuditRow::getStatus)
            .containsExactly("ACCEPTED", "ACCEPTED");
    }

    @Test
    void deliveryFailuresAreCapturedAndNeverEscapeTheAfterCommitListener() {
        doThrow(new IllegalStateException("offline")).when(messages).sendMessage(anyLong(), anyString());
        when(users.selectPhonenumberById(202L)).thenReturn("13800138000");
        when(notify.send(any())).thenThrow(new IllegalStateException("offline"));
        when(audits.insert(any())).thenReturn(1);

        service.notifyOldAccount(new PersonReboundEvent(9201L, 9001L, 202L));

        ArgumentCaptor<NotificationAuditRow> audit = ArgumentCaptor.forClass(NotificationAuditRow.class);
        verify(audits, org.mockito.Mockito.times(2)).insert(audit.capture());
        assertThat(audit.getAllValues()).extracting(NotificationAuditRow::getStatus)
            .containsExactly("FAILED", "FAILED");
        assertThat(audit.getAllValues()).extracting(NotificationAuditRow::getFailureCategory)
            .containsOnly("DELIVERY_FAILED");
    }

    @Test
    void failedAuditCanBeRetriedWithoutReplayingTheBindingTransaction() {
        NotificationAuditRow row = new NotificationAuditRow();
        row.setNotificationAuditId(9901L);
        row.setNotificationType("PERSON_REBIND_INTERNAL");
        row.setProfileId(9201L);
        row.setApplicationId(9001L);
        row.setTargetUserId(202L);
        row.setStatus("FAILED");
        row.setVersion(0);
        when(audits.lockFailed(9901L)).thenReturn(row);
        when(audits.updateRetry(row)).thenReturn(1);

        assertThat(service.retryFailed(9901L)).isTrue();
        assertThat(row.getStatus()).isEqualTo("ACCEPTED");
        verify(messages).sendMessage(202L, "您的个人实名认证绑定已变更。如非本人操作，请联系平台。");
    }
}

package org.dromara.test.notify.caller;

import org.dromara.common.notify.core.NotifyClient;
import org.dromara.common.notify.exception.NotifyDeliveryException;
import org.dromara.common.notify.model.*;
import org.dromara.system.api.MessageService;
import org.dromara.system.api.domain.UserDTO;
import org.dromara.workflow.service.impl.FlwCommonServiceImpl;
import org.dromara.workflow.service.impl.WorkflowTaskRecipientResolver;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("dev")
class WorkflowNotifyCallerUnitTest {

    @Test
    void resolvesPhysicalEmailAndPhoneTargetsWithoutClientScope() {
        MessageService messageService = mock(MessageService.class);
        NotifyClient notifyClient = mock(NotifyClient.class);
        when(notifyClient.send(any())).thenAnswer(invocation -> accepted(invocation.getArgument(0)));
        FlwCommonServiceImpl service = new FlwCommonServiceImpl(messageService, notifyClient,
            mock(WorkflowTaskRecipientResolver.class));
        UserDTO user = user(7L, "user@example.com", "13812345678");

        service.sendMessage(List.of("1", "2", "3"), "流程内容", "流程主题", List.of(user));

        verify(messageService).publishMessage(eq(List.of(7L)), any());
        ArgumentCaptor<NotifyRequest> requests = ArgumentCaptor.forClass(NotifyRequest.class);
        verify(notifyClient, times(2)).send(requests.capture());
        NotifyRequest mail = requests.getAllValues().stream()
            .filter(request -> request.channel().equals(NotifyChannel.MAIL)).findFirst().orElseThrow();
        NotifyRequest sms = requests.getAllValues().stream()
            .filter(request -> request.channel().equals(NotifyChannel.SMS)).findFirst().orElseThrow();
        assertAll(
            () -> assertEquals(List.of(NotifyTarget.email("user@example.com")), mail.targets()),
            () -> assertEquals(List.of(NotifyTarget.phone("13812345678")), sms.targets()),
            () -> assertEquals("流程内容", mail.content().contentSnapshot()),
            () -> assertNull(mail.idempotencyKey()),
            () -> assertNull(sms.idempotencyKey())
        );
    }

    @Test
    void providerFailureDoesNotBreakWorkflowSideEffect() {
        MessageService messageService = mock(MessageService.class);
        NotifyClient notifyClient = mock(NotifyClient.class);
        NotifyTarget target = NotifyTarget.email("user@example.com");
        NotifyResult failed = new NotifyResult("request-failed", NotifyChannel.MAIL, "smtp", NotifyStatus.FAILED,
            List.of(NotifyTargetResult.failed(target, "FAILED", "rejected", 1L)));
        when(notifyClient.send(any())).thenThrow(new NotifyDeliveryException(failed));
        FlwCommonServiceImpl service = new FlwCommonServiceImpl(messageService, notifyClient,
            mock(WorkflowTaskRecipientResolver.class));

        assertDoesNotThrow(() -> service.sendMessage(List.of("2"), "内容", "主题",
            List.of(user(7L, "user@example.com", null))));
    }

    private UserDTO user(Long userId, String email, String phone) {
        UserDTO user = new UserDTO();
        user.setUserId(userId);
        user.setEmail(email);
        user.setPhoneNumber(phone);
        return user;
    }

    private NotifyResult accepted(NotifyRequest request) {
        return new NotifyResult(request.requestId(), request.channel(), request.providerKey(), NotifyStatus.ACCEPTED,
            request.targets().stream().map(target -> NotifyTargetResult.accepted(target, "message-1", 1L)).toList());
    }
}

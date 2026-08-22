package org.dromara.test.notify.caller;

import org.dromara.common.notify.core.NotifyClient;
import org.dromara.common.notify.model.*;
import org.dromara.demo.controller.MailSendController;
import org.dromara.demo.controller.SmsController;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("dev")
class DemoNotifyCallerUnitTest {

    @Test
    void mailDemoUsesOssIdsForAttachmentSnapshots() {
        NotifyClient notifyClient = acceptingClient();
        MailSendController controller = new MailSendController(notifyClient);

        controller.sendSimpleMessage("user@example.com", "主题", "正文");
        controller.sendMessageWithAttachment("user@example.com", "主题", "正文", 77L);
        controller.sendMessageWithAttachments("user@example.com", "主题", "正文", List.of(77L, 88L));

        ArgumentCaptor<NotifyRequest> requests = ArgumentCaptor.forClass(NotifyRequest.class);
        verify(notifyClient, times(3)).send(requests.capture());
        assertEquals(List.of(), requests.getAllValues().get(0).attachmentOssIds());
        assertEquals(List.of(77L), requests.getAllValues().get(1).attachmentOssIds());
        assertEquals(List.of(77L, 88L), requests.getAllValues().get(2).attachmentOssIds());
        assertTrue(requests.getAllValues().stream().allMatch(request -> request.channel().equals("mail")));
    }

    @Test
    void smsDemoUsesExplicitProviderAndCompleteTemplateSnapshot() {
        NotifyClient notifyClient = acceptingClient();
        SmsController controller = new SmsController(notifyClient);

        controller.sendAliyun("13812345678,13912345678", "TPL-A");
        controller.sendTencent("13712345678", "TPL-T");

        ArgumentCaptor<NotifyRequest> requests = ArgumentCaptor.forClass(NotifyRequest.class);
        verify(notifyClient, times(2)).send(requests.capture());
        NotifyRequest aliyun = requests.getAllValues().get(0);
        NotifyRequest tencent = requests.getAllValues().get(1);
        assertAll(
            () -> assertEquals("config1", aliyun.providerKey()),
            () -> assertEquals(List.of(NotifyTarget.phone("13812345678"), NotifyTarget.phone("13912345678")),
                aliyun.targets()),
            () -> assertTrue(aliyun.content().contentSnapshot().contains("1234")),
            () -> assertEquals("config2", tencent.providerKey()),
            () -> assertTrue(tencent.content().contentSnapshot().contains("1234"))
        );
    }

    private NotifyClient acceptingClient() {
        NotifyClient notifyClient = mock(NotifyClient.class);
        when(notifyClient.send(any())).thenAnswer(invocation -> {
            NotifyRequest request = invocation.getArgument(0);
            return new NotifyResult(request.requestId(), request.channel(), request.providerKey(),
                NotifyStatus.ACCEPTED, request.targets().stream()
                .map(target -> NotifyTargetResult.accepted(target, "message-1", 1L)).toList());
        });
        return notifyClient;
    }
}

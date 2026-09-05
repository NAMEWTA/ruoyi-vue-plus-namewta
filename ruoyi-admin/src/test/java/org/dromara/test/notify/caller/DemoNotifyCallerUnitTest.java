package org.dromara.test.notify.caller;

import org.dromara.notify.api.*;
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
        NotificationApplicationService notificationService = acceptingService();
        MailSendController controller = new MailSendController(notificationService);

        controller.sendSimpleMessage("user@example.com", "主题", "正文");
        controller.sendMessageWithAttachment("user@example.com", "主题", "正文", 77L);
        controller.sendMessageWithAttachments("user@example.com", "主题", "正文", List.of(77L, 88L));

        ArgumentCaptor<NotificationCommand> requests = ArgumentCaptor.forClass(NotificationCommand.class);
        verify(notificationService, times(3)).submit(requests.capture());
        assertEquals(List.of(), requests.getAllValues().get(0).templateParams().get("attachmentOssIds"));
        assertEquals(List.of(77L), requests.getAllValues().get(1).templateParams().get("attachmentOssIds"));
        assertEquals(List.of(77L, 88L), requests.getAllValues().get(2).templateParams().get("attachmentOssIds"));
        assertTrue(requests.getAllValues().stream().allMatch(request -> request.channels().equals(List.of(NotificationChannel.MAIL))));
    }

    @Test
    void smsDemoUsesExplicitProviderAndCompleteTemplateSnapshot() {
        NotificationApplicationService notificationService = acceptingService();
        SmsController controller = new SmsController(notificationService);

        controller.sendAliyun("13812345678,13912345678", "TPL-A");
        controller.sendTencent("13712345678", "TPL-T");

        ArgumentCaptor<NotificationCommand> requests = ArgumentCaptor.forClass(NotificationCommand.class);
        verify(notificationService, times(2)).submit(requests.capture());
        NotificationCommand aliyun = requests.getAllValues().get(0);
        NotificationCommand tencent = requests.getAllValues().get(1);
        assertAll(
            () -> assertEquals("config1", aliyun.templateParams().get("providerKey")),
            () -> assertEquals(List.of("13812345678", "13912345678"), aliyun.recipientIds()),
            () -> assertTrue(String.valueOf(aliyun.templateParams().get("content")).contains("1234")),
            () -> assertEquals("config2", tencent.templateParams().get("providerKey")),
            () -> assertTrue(String.valueOf(tencent.templateParams().get("content")).contains("1234"))
        );
    }

    private NotificationApplicationService acceptingService() {
        NotificationApplicationService service = mock(NotificationApplicationService.class);
        when(service.submit(any())).thenReturn(new NotificationReceipt("notification-1", NotificationStatus.ACCEPTED,
            false, false, List.of()));
        return service;
    }
}

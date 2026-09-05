package org.dromara.test.notify.caller;

import org.dromara.common.core.constant.GlobalConstants;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mail.config.properties.MailProperties;
import org.dromara.common.web.config.properties.CaptchaProperties;
import org.dromara.notify.api.NotificationApplicationService;
import org.dromara.notify.api.NotificationCommand;
import org.dromara.notify.api.NotificationReceipt;
import org.dromara.notify.api.NotificationStatus;
import org.dromara.web.controller.CaptchaController;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.dromara.common.core.constant.HttpStatus.ERROR;
import static org.dromara.common.core.constant.HttpStatus.SUCCESS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("dev")
class CaptchaNotifyCallerUnitTest {

    @Test
    void smsCaptchaUsesTemplateSnapshotAndCachesOnlyAfterAccepted() {
        NotificationApplicationService notificationService = mock(NotificationApplicationService.class);
        when(notificationService.submit(any())).thenReturn(accepted());
        RecordingCaptchaController controller = new RecordingCaptchaController(
            new CaptchaProperties(), mailProperties(), notificationService);

        var response = controller.smsCode("13812345678");

        assertEquals(SUCCESS, response.getCode());
        ArgumentCaptor<NotificationCommand> request = ArgumentCaptor.forClass(NotificationCommand.class);
        verify(notificationService).submit(request.capture());
        String code = String.valueOf(request.getValue().templateParams().get("code"));
        assertAll(
            () -> assertEquals("PHONE", request.getValue().recipientType()),
            () -> assertEquals("13812345678", request.getValue().recipientIds().getFirst()),
            () -> assertEquals("13812345678", request.getValue().bizId()),
            () -> assertTrue(request.getValue().idempotencyKey().startsWith("captcha:sms:13812345678:")),
            () -> assertTrue(String.valueOf(request.getValue().templateParams().get("content")).contains(code)),
            () -> assertEquals(GlobalConstants.CAPTCHA_CODE_KEY + "13812345678", controller.cachedKey),
            () -> assertEquals(code, controller.cachedCode)
        );
    }

    @Test
    void smsCaptchaReturnsFailureAndDoesNotCacheWhenProviderRejects() {
        NotificationApplicationService notificationService = mock(NotificationApplicationService.class);
        when(notificationService.submit(any())).thenThrow(new IllegalStateException("provider rejected"));
        RecordingCaptchaController controller = new RecordingCaptchaController(
            new CaptchaProperties(), mailProperties(), notificationService);

        var response = controller.smsCode("13812345678");

        assertEquals(ERROR, response.getCode());
        assertEquals("验证码短信发送失败", response.getMsg());
        assertNull(controller.cachedKey);
        assertNull(controller.cachedCode);
    }

    @Test
    void emailCaptchaUsesNotifyClientAndCachesOnlyAfterAccepted() {
        NotificationApplicationService notificationService = mock(NotificationApplicationService.class);
        when(notificationService.submit(any())).thenReturn(accepted());
        RecordingCaptchaController controller = new RecordingCaptchaController(
            new CaptchaProperties(), mailProperties(), notificationService);

        controller.emailCodeImpl("user@example.com");

        ArgumentCaptor<NotificationCommand> request = ArgumentCaptor.forClass(NotificationCommand.class);
        verify(notificationService).submit(request.capture());
        assertAll(
            () -> assertEquals("EMAIL", request.getValue().recipientType()),
            () -> assertEquals("user@example.com", request.getValue().recipientIds().getFirst()),
            () -> assertTrue(request.getValue().idempotencyKey().startsWith("captcha:mail:user@example.com:")),
            () -> assertTrue(String.valueOf(request.getValue().templateParams().get("content")).contains(controller.cachedCode)),
            () -> assertEquals(GlobalConstants.CAPTCHA_CODE_KEY + "user@example.com", controller.cachedKey)
        );
    }

    @Test
    void emailCaptchaDoesNotCacheWhenNotificationFails() {
        NotificationApplicationService notificationService = mock(NotificationApplicationService.class);
        when(notificationService.submit(any())).thenThrow(new IllegalStateException("provider secret"));
        RecordingCaptchaController controller = new RecordingCaptchaController(
            new CaptchaProperties(), mailProperties(), notificationService);

        ServiceException exception = assertThrows(ServiceException.class,
            () -> controller.emailCodeImpl("user@example.com"));

        assertEquals("验证码邮件发送失败", exception.getMessage());
        assertNull(controller.cachedKey);
        assertNull(controller.cachedCode);
    }

    private MailProperties mailProperties() {
        MailProperties properties = new MailProperties();
        properties.setEnabled(true);
        return properties;
    }

    private NotificationReceipt accepted() {
        return new NotificationReceipt("notification-1", NotificationStatus.ACCEPTED, false, false, List.of());
    }

    private static final class RecordingCaptchaController extends CaptchaController {

        private String cachedKey;
        private String cachedCode;

        private RecordingCaptchaController(CaptchaProperties captchaProperties, MailProperties mailProperties,
                                           NotificationApplicationService notificationService) {
            super(captchaProperties, mailProperties, notificationService);
        }

        @Override
        protected void cacheCaptchaCode(String key, String code) {
            cachedKey = key;
            cachedCode = code;
        }
    }
}

package org.dromara.test.notify.caller;

import org.dromara.common.core.constant.GlobalConstants;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mail.config.properties.MailProperties;
import org.dromara.common.notify.core.NotifyClient;
import org.dromara.common.notify.exception.NotifyDeliveryException;
import org.dromara.common.notify.model.*;
import org.dromara.common.web.config.properties.CaptchaProperties;
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
        NotifyClient notifyClient = mock(NotifyClient.class);
        when(notifyClient.send(any())).thenAnswer(invocation -> accepted(invocation.getArgument(0)));
        RecordingCaptchaController controller = new RecordingCaptchaController(
            new CaptchaProperties(), mailProperties(), notifyClient);

        var response = controller.smsCode("13812345678");

        assertEquals(SUCCESS, response.getCode());
        ArgumentCaptor<NotifyRequest> request = ArgumentCaptor.forClass(NotifyRequest.class);
        verify(notifyClient).send(request.capture());
        NotifyTemplateContent content = assertInstanceOf(NotifyTemplateContent.class,
            request.getValue().content());
        String code = content.params().get("code");
        assertAll(
            () -> assertEquals("sms", request.getValue().channel()),
            () -> assertEquals("config1", request.getValue().providerKey()),
            () -> assertEquals("13812345678", request.getValue().targets().getFirst().value()),
            () -> assertTrue(request.getValue().idempotencyKey().startsWith("captcha:sms:13812345678:")),
            () -> assertTrue(content.contentSnapshot().contains(code)),
            () -> assertEquals(GlobalConstants.CAPTCHA_CODE_KEY + "13812345678", controller.cachedKey),
            () -> assertEquals(code, controller.cachedCode)
        );
    }

    @Test
    void smsCaptchaReturnsFailureAndDoesNotCacheWhenProviderRejects() {
        NotifyClient notifyClient = mock(NotifyClient.class);
        NotifyTarget target = NotifyTarget.phone("13812345678");
        NotifyResult failed = new NotifyResult("request-failed", "sms", "config1", NotifyStatus.FAILED,
            List.of(NotifyTargetResult.failed(target, "REJECTED", "provider rejected", 1L)));
        when(notifyClient.send(any())).thenThrow(new NotifyDeliveryException(failed));
        RecordingCaptchaController controller = new RecordingCaptchaController(
            new CaptchaProperties(), mailProperties(), notifyClient);

        var response = controller.smsCode("13812345678");

        assertEquals(ERROR, response.getCode());
        assertEquals("provider rejected", response.getMsg());
        assertNull(controller.cachedKey);
        assertNull(controller.cachedCode);
    }

    @Test
    void emailCaptchaUsesNotifyClientAndCachesOnlyAfterAccepted() {
        NotifyClient notifyClient = mock(NotifyClient.class);
        when(notifyClient.send(any())).thenAnswer(invocation -> accepted(invocation.getArgument(0)));
        RecordingCaptchaController controller = new RecordingCaptchaController(
            new CaptchaProperties(), mailProperties(), notifyClient);

        controller.emailCodeImpl("user@example.com");

        ArgumentCaptor<NotifyRequest> request = ArgumentCaptor.forClass(NotifyRequest.class);
        verify(notifyClient).send(request.capture());
        NotifyTextContent content = assertInstanceOf(NotifyTextContent.class, request.getValue().content());
        assertAll(
            () -> assertEquals("mail", request.getValue().channel()),
            () -> assertEquals(NotifyAuditPolicy.REDACT_SENSITIVE, request.getValue().auditPolicy()),
            () -> assertEquals(NotifyTarget.email("user@example.com"), request.getValue().targets().getFirst()),
            () -> assertTrue(request.getValue().idempotencyKey().startsWith("captcha:mail:user@example.com:")),
            () -> assertTrue(content.text().contains(controller.cachedCode)),
            () -> assertEquals(GlobalConstants.CAPTCHA_CODE_KEY + "user@example.com", controller.cachedKey)
        );
    }

    @Test
    void emailCaptchaDoesNotCacheWhenNotificationFails() {
        NotifyClient notifyClient = mock(NotifyClient.class);
        when(notifyClient.send(any())).thenThrow(new IllegalStateException("provider secret"));
        RecordingCaptchaController controller = new RecordingCaptchaController(
            new CaptchaProperties(), mailProperties(), notifyClient);

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

    private NotifyResult accepted(NotifyRequest request) {
        return new NotifyResult(request.requestId(), request.channel(), request.providerKey(), NotifyStatus.ACCEPTED,
            request.targets().stream().map(target -> NotifyTargetResult.accepted(target, "message-1", 1L)).toList());
    }

    private static final class RecordingCaptchaController extends CaptchaController {

        private String cachedKey;
        private String cachedCode;

        private RecordingCaptchaController(CaptchaProperties captchaProperties, MailProperties mailProperties,
                                           NotifyClient notifyClient) {
            super(captchaProperties, mailProperties, notifyClient);
        }

        @Override
        protected void cacheCaptchaCode(String key, String code) {
            cachedKey = key;
            cachedCode = code;
        }
    }
}

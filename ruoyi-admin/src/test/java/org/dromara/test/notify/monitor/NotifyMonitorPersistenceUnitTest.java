package org.dromara.test.notify.monitor;

import org.dromara.common.notify.event.NotifyDeliveryEvent;
import org.dromara.common.notify.model.*;
import org.dromara.system.api.OssService;
import org.dromara.system.notify.domain.SysNotifyDeliveryLog;
import org.dromara.system.notify.domain.SysNotifyLog;
import org.dromara.system.notify.mapper.SysNotifyDeliveryLogMapper;
import org.dromara.system.notify.mapper.SysNotifyLogMapper;
import org.dromara.system.notify.service.impl.SysNotifyMonitorServiceImpl;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("dev")
class NotifyMonitorPersistenceUnitTest {

    private final SysNotifyLogMapper logMapper = mock(SysNotifyLogMapper.class);
    private final SysNotifyDeliveryLogMapper deliveryMapper = mock(SysNotifyDeliveryLogMapper.class);
    private final OssService ossService = mock(OssService.class);
    private final SysNotifyMonitorServiceImpl service =
        new SysNotifyMonitorServiceImpl(logMapper, deliveryMapper, ossService);

    @Test
    void recordPersistsPlainSnapshotDeliveriesAndBindsAttachmentsWithoutThreadContext() {
        NotifyTarget accepted = NotifyTarget.phone("13812345678");
        NotifyTarget failed = NotifyTarget.email("secret@example.com");
        NotifyRequest request = NotifyRequest.builder()
            .requestId("request-1")
            .bizType("contract")
            .bizId("100")
            .channel("mail")
            .providerKey("requested-provider")
            .targets(List.of(accepted, failed))
            .content(new NotifyTemplateContent("主题", "TPL-1", java.util.Map.of("code", "123456"),
                "验证码 123456"))
            .build();
        NotifyResult result = new NotifyResult("request-1", "mail", "smtp-main", NotifyStatus.PARTIAL_FAILURE,
            List.of(
                NotifyTargetResult.accepted(accepted, "provider-message-1", 12),
                NotifyTargetResult.failed(failed, "MAIL_REJECTED", "rejected", 18)
            ));
        NotifyDeliveryEvent event = new NotifyDeliveryEvent(request,
            new NotifyContext(7L, 9L, "trace-1"), result, null, 900L, List.of(77L), Instant.parse("2026-08-22T08:00:00Z"));

        service.record(event);

        ArgumentCaptor<SysNotifyLog> log = ArgumentCaptor.forClass(SysNotifyLog.class);
        verify(logMapper).insert(log.capture());
        assertAll(
            () -> assertEquals(900L, log.getValue().getNotifyLogId()),
            () -> assertEquals("验证码 123456", log.getValue().getContentSnapshot()),
            () -> assertTrue(log.getValue().getTemplateParams().contains("123456")),
            () -> assertEquals("PARTIAL_FAILURE", log.getValue().getStatus()),
            () -> assertEquals(9L, log.getValue().getClientPk()),
            () -> assertEquals(7L, log.getValue().getCreateBy())
        );
        ArgumentCaptor<SysNotifyDeliveryLog> delivery = ArgumentCaptor.forClass(SysNotifyDeliveryLog.class);
        verify(deliveryMapper, times(2)).insert(delivery.capture());
        assertEquals(List.of("13812345678", "secret@example.com"),
            delivery.getAllValues().stream().map(SysNotifyDeliveryLog::getTargetValue).toList());
        verify(ossService).bind(77L, "sys_notify_log", "900");
    }

    @Test
    void duplicatePersistsOnlyLogicalSkippedRow() {
        NotifyRequest request = NotifyRequest.builder()
            .requestId("duplicate-2")
            .channel("sms")
            .targets(List.of(NotifyTarget.phone("13812345678")))
            .content(new NotifyTextContent(null, "same content"))
            .build();
        NotifyResult result = new NotifyResult("duplicate-2", "sms", "sms-main",
            NotifyStatus.SKIPPED_DUPLICATE, List.of());

        service.record(new NotifyDeliveryEvent(request, NotifyContext.empty(), result,
            "original-1", 901L, List.of(), Instant.now()));

        ArgumentCaptor<SysNotifyLog> log = ArgumentCaptor.forClass(SysNotifyLog.class);
        verify(logMapper).insert(log.capture());
        assertEquals("original-1", log.getValue().getOriginalRequestId());
        assertEquals("SKIPPED_DUPLICATE", log.getValue().getStatus());
        assertNull(log.getValue().getClientPk());
        assertEquals(-1L, log.getValue().getCreateBy());
        verify(deliveryMapper, never()).insert(any(SysNotifyDeliveryLog.class));
        verifyNoInteractions(ossService);
    }
}

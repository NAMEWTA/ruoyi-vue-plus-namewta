package org.dromara.test.notify.monitor;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.system.api.OssService;
import org.dromara.system.notify.domain.SysNotifyDeliveryLog;
import org.dromara.system.notify.domain.SysNotifyLog;
import org.dromara.system.notify.domain.bo.SysNotifyQuery;
import org.dromara.system.notify.mapper.SysNotifyDeliveryLogMapper;
import org.dromara.system.notify.mapper.SysNotifyLogMapper;
import org.dromara.system.notify.service.impl.SysNotifyMonitorServiceImpl;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("dev")
class NotifyMonitorManagementUnitTest {

    private final SysNotifyLogMapper logMapper = mock(SysNotifyLogMapper.class);
    private final SysNotifyDeliveryLogMapper deliveryMapper = mock(SysNotifyDeliveryLogMapper.class);
    private final OssService ossService = mock(OssService.class);
    private final SysNotifyMonitorServiceImpl service =
        new SysNotifyMonitorServiceImpl(logMapper, deliveryMapper, ossService);

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void globalListHasNoImplicitClientFilterAndReturnsOnlyMaskedTargets() {
        SysNotifyLog log = log(100L, "[77]");
        Page<SysNotifyLog> page = new Page<>(1, 10);
        page.setRecords(List.of(log));
        page.setTotal(1);
        when(logMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);
        SysNotifyDeliveryLog delivery = delivery(100L, "EMAIL", "secret@example.com");
        when(deliveryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(delivery));

        var result = service.page(new SysNotifyQuery(), new PageQuery(10, 1));

        assertEquals(1, result.getTotal());
        var row = result.getRows().iterator().next();
        assertEquals(List.of("s***@example.com"), row.getMaskedTargets());
        assertFalse(row.toString().contains("secret@example.com"));
        ArgumentCaptor<Wrapper<SysNotifyLog>> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(logMapper).selectPage(any(Page.class), wrapper.capture());
        LambdaQueryWrapper<SysNotifyLog> queryWrapper = (LambdaQueryWrapper<SysNotifyLog>) wrapper.getValue();
        assertTrue(queryWrapper.getExpression().getNormal().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void detailReturnsFullPlaintextAndPhysicalTargets() {
        SysNotifyLog log = log(100L, "[77]");
        log.setContent("OTP 123456");
        SysNotifyDeliveryLog delivery = delivery(100L, "PHONE", "13812345678");
        when(logMapper.selectById(100L)).thenReturn(log);
        when(deliveryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(delivery));

        var detail = service.detail(100L);

        assertEquals("OTP 123456", detail.getNotification().getContent());
        assertEquals("13812345678", detail.getDeliveries().getFirst().getTargetValue());
        assertEquals(List.of(77L), detail.getAttachmentOssIds());
    }

    @Test
    void removeReconcilesEverySnapshotAfterDeletingChildrenAndParents() {
        SysNotifyLog first = log(100L, "[77,78]");
        SysNotifyLog second = log(101L, "[]");
        when(logMapper.selectBatchIds(List.of(100L, 101L))).thenReturn(List.of(first, second));
        when(logMapper.physicalDeleteByIds(List.of(100L, 101L))).thenReturn(2);

        int removed = service.remove(List.of(101L, 100L, 100L));

        assertEquals(2, removed);
        verify(ossService).reconcileReferences("sys_notify_log", "100", List.of(77L, 78L), List.of());
        verify(ossService).reconcileReferences("sys_notify_log", "101", List.of(), List.of());
        verify(deliveryMapper).physicalDeleteByNotifyLogIds(List.of(100L, 101L));
        verify(logMapper).physicalDeleteByIds(List.of(100L, 101L));
    }

    @Test
    void attachmentDownloadRequiresMembershipInNotificationSnapshot() {
        when(logMapper.selectById(100L)).thenReturn(log(100L, "[77]"));

        assertThrows(org.dromara.common.core.exception.ServiceException.class,
            () -> service.attachmentDownload(100L, 78L));
        verify(ossService, never()).presignDownload(anyLong());

        service.attachmentDownload(100L, 77L);
        verify(ossService).presignDownload(77L);
    }

    @Test
    void cleanReadsAndDeletesFixedBatchesUntilEmpty() {
        List<Long> ids = LongStream.rangeClosed(1, 500).boxed().toList();
        List<SysNotifyLog> logs = ids.stream().map(id -> log(id, "[]")).toList();
        when(logMapper.selectCleanupBatch(500)).thenReturn(ids, List.of());
        when(logMapper.selectBatchIds(ids)).thenReturn(logs);
        when(logMapper.physicalDeleteByIds(ids)).thenReturn(500);

        service.clean();

        verify(logMapper, times(2)).selectCleanupBatch(500);
        verify(deliveryMapper).physicalDeleteByNotifyLogIds(ids);
        verify(logMapper).physicalDeleteByIds(ids);
    }

    private SysNotifyLog log(Long id, String attachments) {
        SysNotifyLog log = new SysNotifyLog();
        log.setNotifyLogId(id);
        log.setRequestId("request-" + id);
        log.setChannel("mail");
        log.setStatus("ACCEPTED");
        log.setAttachmentOssIds(attachments);
        log.setCreateTime(LocalDateTime.now());
        return log;
    }

    private SysNotifyDeliveryLog delivery(Long logId, String type, String target) {
        SysNotifyDeliveryLog delivery = new SysNotifyDeliveryLog();
        delivery.setNotifyDeliveryLogId(logId + 1000);
        delivery.setNotifyLogId(logId);
        delivery.setTargetType(type);
        delivery.setTargetValue(target);
        delivery.setStatus("ACCEPTED");
        return delivery;
    }
}

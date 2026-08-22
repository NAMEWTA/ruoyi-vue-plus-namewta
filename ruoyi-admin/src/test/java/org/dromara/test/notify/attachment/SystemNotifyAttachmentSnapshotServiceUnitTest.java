package org.dromara.test.notify.attachment;

import org.dromara.common.notify.attachment.NotifyAttachmentSnapshot;
import org.dromara.common.notify.exception.NotifyAttachmentSnapshotException;
import org.dromara.common.notify.model.NotifyContext;
import org.dromara.system.domain.SysOss;
import org.dromara.system.mapper.SysOssMapper;
import org.dromara.system.notify.attachment.NotifyAttachmentObjectStore;
import org.dromara.system.notify.attachment.SystemNotifyAttachmentSnapshotService;
import org.dromara.system.oss.config.OssLifecycleProperties;
import org.dromara.system.oss.service.OssLifecycleManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * System OSS 快照复制、TEMP 登记和部分失败补偿测试。
 */
@Tag("dev")
class SystemNotifyAttachmentSnapshotServiceUnitTest {

    @Test
    void shouldCopyRegisterAndMaterializeTemporarySnapshots() {
        SysOssMapper mapper = mock(SysOssMapper.class);
        NotifyAttachmentObjectStore objectStore = mock(NotifyAttachmentObjectStore.class);
        OssLifecycleManager lifecycle = mock(OssLifecycleManager.class);
        when(mapper.selectById(10L)).thenReturn(source(10L, "a.txt"));
        when(mapper.selectById(20L)).thenReturn(source(20L, "b.txt"));
        when(objectStore.copy(any(SysOss.class), eq(7001L))).thenAnswer(invocation -> {
            SysOss source = invocation.getArgument(0);
            return new NotifyAttachmentObjectStore.CopiedObject("service-a", "notify/" + source.getOssId(),
                7, "text/plain");
        });
        AtomicLong ids = new AtomicLong(100);
        when(mapper.insert(any(SysOss.class))).thenAnswer(invocation -> {
            invocation.<SysOss>getArgument(0).setOssId(ids.incrementAndGet());
            return 1;
        });
        SystemNotifyAttachmentSnapshotService service = service(mapper, objectStore, lifecycle);

        List<NotifyAttachmentSnapshot> result = service.createSnapshots(7001L, List.of(10L, 20L),
            NotifyContext.empty());
        Path target = Path.of("attachment.txt");
        assertDoesNotThrow(() -> result.getFirst().resource().materializer().materialize(target));

        assertEquals(List.of(101L, 102L), result.stream().map(item -> item.resource().ossId()).toList());
        verify(mapper, times(2)).insert(argThat((SysOss value) -> "Y".equals(value.getIsTemp())
            && value.getExpireTime() != null && value.getExt1().contains("notifySnapshot")));
        verify(objectStore).materialize(any(NotifyAttachmentObjectStore.CopiedObject.class), eq(target));
    }

    @Test
    void partialCopyFailureMustDeleteCreatedMetadataBeforeProviderCanRun() {
        SysOssMapper mapper = mock(SysOssMapper.class);
        NotifyAttachmentObjectStore objectStore = mock(NotifyAttachmentObjectStore.class);
        OssLifecycleManager lifecycle = mock(OssLifecycleManager.class);
        when(mapper.selectById(10L)).thenReturn(source(10L, "a.txt"));
        when(mapper.selectById(20L)).thenReturn(source(20L, "b.txt"));
        when(objectStore.copy(argThat((SysOss value) -> value != null && value.getOssId().equals(10L)), eq(7001L)))
            .thenReturn(new NotifyAttachmentObjectStore.CopiedObject("service-a", "notify/10", 7, "text/plain"));
        when(objectStore.copy(argThat((SysOss value) -> value != null && value.getOssId().equals(20L)), eq(7001L)))
            .thenThrow(new IllegalStateException("copy failed"));
        when(mapper.insert(any(SysOss.class))).thenAnswer(invocation -> {
            invocation.<SysOss>getArgument(0).setOssId(101L);
            return 1;
        });
        SystemNotifyAttachmentSnapshotService service = service(mapper, objectStore, lifecycle);

        NotifyAttachmentSnapshotException exception = assertThrows(NotifyAttachmentSnapshotException.class,
            () -> service.createSnapshots(7001L, List.of(10L, 20L), NotifyContext.empty()));

        assertEquals("SNAPSHOT_COPY_FAILED", exception.code());
        verify(lifecycle).deleteObjects(List.of(101L));
    }

    @Test
    void metadataFailureMustDeleteCopiedObjectDirectly() {
        SysOssMapper mapper = mock(SysOssMapper.class);
        NotifyAttachmentObjectStore objectStore = mock(NotifyAttachmentObjectStore.class);
        OssLifecycleManager lifecycle = mock(OssLifecycleManager.class);
        NotifyAttachmentObjectStore.CopiedObject copied =
            new NotifyAttachmentObjectStore.CopiedObject("service-a", "notify/10", 7, "text/plain");
        when(mapper.selectById(10L)).thenReturn(source(10L, "a.txt"));
        when(objectStore.copy(any(SysOss.class), eq(7001L))).thenReturn(copied);
        when(mapper.insert(any(SysOss.class))).thenReturn(0);
        SystemNotifyAttachmentSnapshotService service = service(mapper, objectStore, lifecycle);

        NotifyAttachmentSnapshotException exception = assertThrows(NotifyAttachmentSnapshotException.class,
            () -> service.createSnapshots(7001L, List.of(10L), NotifyContext.empty()));

        assertEquals("SNAPSHOT_METADATA_FAILED", exception.code());
        verify(objectStore).delete(copied);
        verifyNoInteractions(lifecycle);
    }

    private SystemNotifyAttachmentSnapshotService service(SysOssMapper mapper,
                                                           NotifyAttachmentObjectStore objectStore,
                                                           OssLifecycleManager lifecycle) {
        OssLifecycleProperties properties = new OssLifecycleProperties();
        properties.setTempRetention(Duration.ofHours(24));
        return new SystemNotifyAttachmentSnapshotService(mapper, objectStore, lifecycle, properties);
    }

    private SysOss source(Long id, String name) {
        SysOss source = new SysOss();
        source.setOssId(id);
        source.setFileName("source/" + name);
        source.setOriginalName(name);
        source.setFileSuffix(".txt");
        source.setService("service-a");
        return source;
    }
}

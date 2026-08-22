package org.dromara.test.oss.lifecycle;

import org.dromara.common.oss.model.OssPresignedRequest;
import org.dromara.system.api.OssService;
import org.dromara.system.domain.SysOss;
import org.dromara.system.mapper.SysOssMapper;
import org.dromara.system.oss.config.OssLifecycleProperties;
import org.dromara.system.oss.exception.OssLifecycleError;
import org.dromara.system.oss.exception.OssLifecycleException;
import org.dromara.system.oss.domain.SysOssRef;
import org.dromara.system.oss.mapper.SysOssRefMapper;
import org.dromara.system.oss.provider.OssObjectStore;
import org.dromara.system.oss.service.OssLifecycleManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OSS 生命周期协调测试。
 */
@Tag("dev")
class OssLifecycleManagerUnitTest {

    @Test
    void shouldLeaveTempOnFirstIdempotentBind() {
        Fixture fixture = fixture();
        SysOss oss = oss(10L, "Y", LocalDateTime.now().plusHours(1));
        when(fixture.ossMapper.selectByIdForUpdate(10L)).thenReturn(oss);
        when(fixture.refMapper.countActiveByOssId(10L)).thenReturn(0L);
        when(fixture.refMapper.existsActive(10L, "biz_contract", "100")).thenReturn(false);
        when(fixture.refMapper.restoreReference(10L, "biz_contract", "100")).thenReturn(0);
        when(fixture.refMapper.insert(any(SysOssRef.class))).thenReturn(1);
        when(fixture.ossMapper.updateLifecycle(10L, "N", null)).thenReturn(1);

        OssService.OssReferenceState state = fixture.manager.bind(10L, "biz_contract", "100");

        assertFalse(state.temporary());
        assertEquals(1L, state.referenceCount());
        assertNull(state.expireTime());
        verify(fixture.ossMapper).selectByIdForUpdate(10L);
        verify(fixture.ossMapper).updateLifecycle(10L, "N", null);
    }

    @Test
    void shouldKeepExistingBindIdempotent() {
        Fixture fixture = fixture();
        when(fixture.ossMapper.selectByIdForUpdate(10L)).thenReturn(oss(10L, "N", null));
        when(fixture.refMapper.countActiveByOssId(10L)).thenReturn(1L);
        when(fixture.refMapper.existsActive(10L, "biz_contract", "100")).thenReturn(true);

        OssService.OssReferenceState state = fixture.manager.bind(10L, "biz_contract", "100");

        assertEquals(1L, state.referenceCount());
        assertFalse(state.temporary());
        verify(fixture.refMapper, never()).restoreReference(anyLong(), anyString(), anyString());
        verify(fixture.refMapper, never()).insert(any(SysOssRef.class));
        verify(fixture.ossMapper, never()).updateLifecycle(anyLong(), anyString(), any());
    }

    @Test
    void shouldRejectNonPhysicalReferenceBeforeDatabaseAccess() {
        Fixture fixture = fixture();

        OssLifecycleException exception = assertThrows(OssLifecycleException.class,
            () -> fixture.manager.bind(10L, "Contract", "100"));

        assertEquals(OssLifecycleError.INVALID_REFERENCE, exception.error());
        verifyNoInteractions(fixture.ossMapper, fixture.refMapper, fixture.objectStore);
    }

    @Test
    void shouldRestartTempWindowAfterLastUnbind() {
        Fixture fixture = fixture();
        when(fixture.ossMapper.selectByIdForUpdate(10L)).thenReturn(oss(10L, "N", null));
        when(fixture.refMapper.countActiveByOssId(10L)).thenReturn(1L);
        when(fixture.refMapper.existsActive(10L, "biz_contract", "100")).thenReturn(true);
        when(fixture.refMapper.deactivateReference(10L, "biz_contract", "100")).thenReturn(1);
        when(fixture.ossMapper.updateLifecycle(eq(10L), eq("Y"), any(LocalDateTime.class))).thenReturn(1);

        LocalDateTime before = LocalDateTime.now().plusHours(23);
        OssService.OssReferenceState state = fixture.manager.unbind(10L, "biz_contract", "100");

        assertTrue(state.temporary());
        assertEquals(0L, state.referenceCount());
        assertTrue(state.expireTime().isAfter(before));
        ArgumentCaptor<LocalDateTime> expiry = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(fixture.ossMapper).updateLifecycle(eq(10L), eq("Y"), expiry.capture());
        assertTrue(expiry.getValue().isAfter(before));
    }

    @Test
    void shouldRejectDeletingReferencedObjectBeforeProviderCall() {
        Fixture fixture = fixture();
        SysOss oss = oss(10L, "N", null);
        when(fixture.ossMapper.selectByIdsForUpdate(List.of(10L))).thenReturn(List.of(oss));
        when(fixture.refMapper.countActiveByOssId(10L)).thenReturn(1L);

        OssLifecycleException exception = assertThrows(OssLifecycleException.class,
            () -> fixture.manager.deleteObjects(List.of(10L)));

        assertEquals(OssLifecycleError.OBJECT_REFERENCED, exception.error());
        verifyNoInteractions(fixture.objectStore);
        verify(fixture.ossMapper, never()).deleteByIds(anyCollection());
    }

    @Test
    void shouldLockAndRecheckBeforeDeletingExpiredTempObject() {
        Fixture fixture = fixture();
        LocalDateTime now = LocalDateTime.now();
        SysOss oss = oss(10L, "Y", now.minusMinutes(1));
        when(fixture.ossMapper.selectByIdForUpdate(10L)).thenReturn(oss);
        when(fixture.refMapper.countActiveByOssId(10L)).thenReturn(0L);
        when(fixture.ossMapper.deleteById(10L)).thenReturn(1);

        boolean deleted = fixture.manager.cleanupExpired(10L, now, false);

        assertTrue(deleted);
        InOrder order = inOrder(fixture.ossMapper, fixture.refMapper, fixture.objectStore);
        order.verify(fixture.ossMapper).selectByIdForUpdate(10L);
        order.verify(fixture.refMapper).countActiveByOssId(10L);
        order.verify(fixture.objectStore).delete(oss);
        order.verify(fixture.ossMapper).deleteById(10L);
    }

    @Test
    void shouldNotMutateProviderOrDatabaseDuringCleanupDryRun() {
        Fixture fixture = fixture();
        LocalDateTime now = LocalDateTime.now();
        when(fixture.ossMapper.selectByIdForUpdate(10L)).thenReturn(oss(10L, "Y", now.minusMinutes(1)));
        when(fixture.refMapper.countActiveByOssId(10L)).thenReturn(0L);

        assertTrue(fixture.manager.cleanupExpired(10L, now, true));

        verifyNoInteractions(fixture.objectStore);
        verify(fixture.ossMapper, never()).deleteById(anyLong());
    }

    @Test
    void shouldPreserveMetadataWhenProviderDeleteFails() {
        Fixture fixture = fixture();
        LocalDateTime now = LocalDateTime.now();
        SysOss oss = oss(10L, "Y", now.minusMinutes(1));
        when(fixture.ossMapper.selectByIdForUpdate(10L)).thenReturn(oss);
        when(fixture.refMapper.countActiveByOssId(10L)).thenReturn(0L);
        doThrow(new OssLifecycleException(OssLifecycleError.PROVIDER_DELETE_FAILED, "failed"))
            .when(fixture.objectStore).delete(oss);

        OssLifecycleException exception = assertThrows(OssLifecycleException.class,
            () -> fixture.manager.cleanupExpired(10L, now, false));

        assertEquals(OssLifecycleError.PROVIDER_DELETE_FAILED, exception.error());
        verify(fixture.ossMapper, never()).deleteById(anyLong());
    }

    @Test
    void shouldReturnStructuredShortLivedDownloadAuthorization() {
        Fixture fixture = fixture();
        SysOss oss = oss(10L, "N", null);
        when(fixture.ossMapper.selectById(10L)).thenReturn(oss);
        Instant expiresAt = Instant.now().plusSeconds(120);
        when(fixture.objectStore.presign(eq(oss), eq(Duration.ofMinutes(2))))
            .thenReturn(new OssPresignedRequest("GET", "https://oss.example/signed", java.util.Map.of(), expiresAt));

        OssService.OssDownloadUrl result = fixture.manager.presignDownload(10L);

        assertEquals("https://oss.example/signed", result.url());
        assertEquals(expiresAt, result.expiresAt());
        assertEquals("document.pdf", result.fileName());
    }

    @Test
    void lifecycleDefaultsRequireExplicitCleanupEnablement() {
        OssLifecycleProperties properties = new OssLifecycleProperties();

        assertEquals(Duration.ofHours(24), properties.getTempRetention());
        assertEquals(Duration.ofMinutes(2), properties.getDownloadTtl());
        assertFalse(properties.isCleanupEnabled());
        assertTrue(properties.isCleanupDryRun());
    }

    private Fixture fixture() {
        SysOssMapper ossMapper = mock(SysOssMapper.class);
        SysOssRefMapper refMapper = mock(SysOssRefMapper.class);
        OssObjectStore objectStore = mock(OssObjectStore.class);
        OssLifecycleProperties properties = new OssLifecycleProperties();
        return new Fixture(ossMapper, refMapper, objectStore,
            new OssLifecycleManager(ossMapper, refMapper, objectStore, properties));
    }

    private SysOss oss(Long id, String temporary, LocalDateTime expireTime) {
        SysOss oss = new SysOss();
        oss.setOssId(id);
        oss.setFileName("objects/document.pdf");
        oss.setOriginalName("document.pdf");
        oss.setService("minio");
        oss.setIsTemp(temporary);
        oss.setExpireTime(expireTime);
        return oss;
    }

    private record Fixture(
        SysOssMapper ossMapper,
        SysOssRefMapper refMapper,
        OssObjectStore objectStore,
        OssLifecycleManager manager
    ) {
    }
}

package org.dromara.test.oss.owner.system;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import io.github.linpeilie.Converter;
import org.dromara.common.mybatis.core.mapper.LambdaCrudChainWrapper;
import org.dromara.common.notify.event.NotifyDeliveryEvent;
import org.dromara.common.notify.model.NotifyChannel;
import org.dromara.common.notify.model.NotifyContext;
import org.dromara.common.notify.model.NotifyRequest;
import org.dromara.common.notify.model.NotifyResult;
import org.dromara.common.notify.model.NotifyStatus;
import org.dromara.common.notify.model.NotifyTextContent;
import org.dromara.system.api.OssService;
import org.dromara.system.domain.SysNotice;
import org.dromara.system.domain.SysUser;
import org.dromara.system.domain.SysUserPost;
import org.dromara.system.domain.SysUserRole;
import org.dromara.system.domain.bo.SysNoticeBo;
import org.dromara.system.domain.bo.SysUserBo;
import org.dromara.system.mapper.SysClientMapper;
import org.dromara.system.mapper.SysDeptMapper;
import org.dromara.system.mapper.SysNoticeMapper;
import org.dromara.system.mapper.SysPostMapper;
import org.dromara.system.mapper.SysRoleMapper;
import org.dromara.system.mapper.SysUserMapper;
import org.dromara.system.mapper.SysUserPostMapper;
import org.dromara.system.mapper.SysUserRoleMapper;
import org.dromara.system.mapper.SysUserTypeMapper;
import org.dromara.system.notify.mapper.SysNotifyDeliveryLogMapper;
import org.dromara.system.notify.mapper.SysNotifyLogMapper;
import org.dromara.system.notify.service.impl.SysNotifyMonitorServiceImpl;
import org.dromara.system.service.ClientSessionService;
import org.dromara.system.service.ISysUserTypeRelService;
import org.dromara.system.service.impl.SysNoticeServiceImpl;
import org.dromara.system.service.impl.SysUserServiceImpl;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class SystemBusinessOssOwnerUnitTest {

    @BeforeAll
    static void configureMapstructConverter() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("converter", new Converter());
        new SpringUtil().postProcessBeanFactory(beanFactory);
    }

    @Test
    void everyUserAvatarWriteEntryUsesDynamicDatasourceTransaction() throws Exception {
        assertDynamicTransaction("insertUser", SysUserBo.class);
        assertDynamicTransaction("registerUser", SysUserBo.class);
        assertDynamicTransaction("updateUser", SysUserBo.class);
        assertDynamicTransaction("updateUserProfile", SysUserBo.class);
        assertDynamicTransaction("deleteUserById", Long.class);
        assertDynamicTransaction("deleteUserByIds", Long[].class);
    }

    @Test
    void userRegisterReconcilesGeneratedOwnerIdAndPropagatesFailure() {
        OssService ossService = mock(OssService.class);
        SysUserMapper userMapper = mock(SysUserMapper.class, RETURNS_DEEP_STUBS);
        SysUserServiceImpl service = userService(userMapper, ossService);
        SysUserBo user = new SysUserBo();
        user.setAvatar(77L);
        doAnswer(invocation -> {
            invocation.getArgument(0, SysUser.class).setUserId(100L);
            return 1;
        }).when(userMapper).insert(any(SysUser.class));

        assertTrue(service.registerUser(user));

        verify(ossService).reconcileReferences("sys_user", "100", List.of(), List.of(77L));

        SysUserBo failingUser = new SysUserBo();
        failingUser.setAvatar(88L);
        doAnswer(invocation -> {
            invocation.getArgument(0, SysUser.class).setUserId(101L);
            return 1;
        }).when(userMapper).insert(any(SysUser.class));
        doThrow(new IllegalStateException("reference failure")).when(ossService)
            .reconcileReferences("sys_user", "101", List.of(), List.of(88L));

        assertThrows(IllegalStateException.class, () -> service.registerUser(failingUser));
    }

    @Test
    void userAdminUpdateAndDeleteReconcilePreviousAvatar() {
        OssService ossService = mock(OssService.class);
        SysUserMapper userMapper = mock(SysUserMapper.class, RETURNS_DEEP_STUBS);
        SysUserServiceImpl service = userService(userMapper, ossService);
        SysUser existing = user(100L, 77L);
        when(userMapper.selectById(100L)).thenReturn(existing);
        when(userMapper.updateById(any(SysUser.class))).thenReturn(1);

        SysUserBo update = new SysUserBo();
        update.setUserId(100L);
        update.setAvatar(88L);
        service.updateUser(update);

        verify(ossService).reconcileReferences("sys_user", "100", List.of(77L), List.of(88L));

        when(userMapper.deleteById(100L)).thenReturn(1);
        service.deleteUserById(100L);

        verify(ossService).reconcileReferences("sys_user", "100", List.of(77L), List.of());
    }

    @Test
    void noticeInsertUpdateAndDeleteUseCollectionReconciliation() {
        SysNoticeMapper noticeMapper = mock(SysNoticeMapper.class);
        OssService ossService = mock(OssService.class);
        SysNoticeServiceImpl service = new SysNoticeServiceImpl(
            noticeMapper, mock(SysUserMapper.class, RETURNS_DEEP_STUBS), ossService);
        doAnswer(invocation -> {
            invocation.getArgument(0, SysNotice.class).setNoticeId(200L);
            return 1;
        }).when(noticeMapper).insert(any(SysNotice.class));

        SysNoticeBo insert = new SysNoticeBo();
        insert.setNoticeContent("first oss://77 and duplicate oss://77");
        service.insertNotice(insert);
        verify(ossService).reconcileReferences("sys_notice", "200", Set.of(), Set.of(77L));

        SysNotice existing = notice(200L, "oss://77");
        when(noticeMapper.selectById(200L)).thenReturn(existing);
        when(noticeMapper.updateById(any(SysNotice.class))).thenReturn(1);
        SysNoticeBo update = new SysNoticeBo();
        update.setNoticeId(200L);
        update.setNoticeContent("oss://88");
        service.updateNotice(update);
        verify(ossService).reconcileReferences("sys_notice", "200", Set.of(77L), Set.of(88L));

        when(noticeMapper.deleteById(200L)).thenReturn(1);
        service.deleteNoticeById(200L);
        verify(ossService).reconcileReferences("sys_notice", "200", Set.of(77L), Set.of());
    }

    @Test
    void noticeReferenceFailurePropagatesAfterBusinessWrite() {
        SysNoticeMapper noticeMapper = mock(SysNoticeMapper.class);
        OssService ossService = mock(OssService.class);
        SysNoticeServiceImpl service = new SysNoticeServiceImpl(
            noticeMapper, mock(SysUserMapper.class, RETURNS_DEEP_STUBS), ossService);
        doAnswer(invocation -> {
            invocation.getArgument(0, SysNotice.class).setNoticeId(201L);
            return 1;
        }).when(noticeMapper).insert(any(SysNotice.class));
        doThrow(new IllegalStateException("reference failure")).when(ossService)
            .reconcileReferences("sys_notice", "201", Set.of(), Set.of(77L));
        SysNoticeBo notice = new SysNoticeBo();
        notice.setNoticeContent("oss://77");

        assertThrows(IllegalStateException.class, () -> service.insertNotice(notice));
        verify(noticeMapper).insert(any(SysNotice.class));
    }

    @Test
    void notifyRecordAndRemoveUseSnapshotCollectionsAndPropagateFailure() {
        SysNotifyLogMapper logMapper = mock(SysNotifyLogMapper.class);
        SysNotifyDeliveryLogMapper deliveryMapper = mock(SysNotifyDeliveryLogMapper.class);
        OssService ossService = mock(OssService.class);
        SysNotifyMonitorServiceImpl service = new SysNotifyMonitorServiceImpl(logMapper, deliveryMapper, ossService);
        NotifyDeliveryEvent event = notifyEvent(300L, List.of(77L, 88L));

        service.record(event);
        verify(ossService).reconcileReferences("sys_notify_log", "300", List.of(), List.of(77L, 88L));

        org.dromara.system.notify.domain.SysNotifyLog log = new org.dromara.system.notify.domain.SysNotifyLog();
        log.setNotifyLogId(300L);
        log.setAttachmentOssIds("[77,88]");
        when(logMapper.selectBatchIds(List.of(300L))).thenReturn(List.of(log));
        when(logMapper.physicalDeleteByIds(List.of(300L))).thenReturn(1);
        service.remove(List.of(300L));
        verify(ossService).reconcileReferences("sys_notify_log", "300", List.of(77L, 88L), List.of());

        NotifyDeliveryEvent failingEvent = notifyEvent(301L, List.of(99L));
        doThrow(new IllegalStateException("reference failure")).when(ossService)
            .reconcileReferences("sys_notify_log", "301", List.of(), List.of(99L));
        assertThrows(IllegalStateException.class, () -> service.record(failingEvent));
    }

    private void assertDynamicTransaction(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = SysUserServiceImpl.class.getMethod(methodName, parameterTypes);
        assertNotNull(method.getAnnotation(DSTransactional.class), methodName + " must use @DSTransactional");
    }

    private SysUserServiceImpl userService(SysUserMapper userMapper, OssService ossService) {
        SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
        @SuppressWarnings("unchecked")
        LambdaCrudChainWrapper<SysUserRole, SysUserRole> roleChain = mock(LambdaCrudChainWrapper.class, RETURNS_SELF);
        when(userRoleMapper.lambda()).thenReturn(roleChain);
        when(roleChain.eq(any(SFunction.class), any())).thenReturn(roleChain);
        SysUserPostMapper userPostMapper = mock(SysUserPostMapper.class);
        @SuppressWarnings("unchecked")
        LambdaCrudChainWrapper<SysUserPost, SysUserPost> postChain = mock(LambdaCrudChainWrapper.class, RETURNS_SELF);
        when(userPostMapper.lambda()).thenReturn(postChain);
        when(postChain.eq(any(SFunction.class), any())).thenReturn(postChain);
        ISysUserTypeRelService userTypeRelService = mock(ISysUserTypeRelService.class);
        when(userTypeRelService.selectByUserId(any())).thenReturn(List.of());
        return new SysUserServiceImpl(
            userMapper,
            mock(SysDeptMapper.class, RETURNS_DEEP_STUBS),
            mock(SysRoleMapper.class, RETURNS_DEEP_STUBS),
            mock(SysPostMapper.class, RETURNS_DEEP_STUBS),
            userRoleMapper,
            userPostMapper,
            mock(SysClientMapper.class, RETURNS_DEEP_STUBS),
            mock(SysUserTypeMapper.class, RETURNS_DEEP_STUBS),
            mock(ClientSessionService.class),
            userTypeRelService,
            ossService
        );
    }

    private SysUser user(Long userId, Long avatar) {
        SysUser user = new SysUser();
        user.setUserId(userId);
        user.setAvatar(avatar);
        return user;
    }

    private SysNotice notice(Long noticeId, String content) {
        SysNotice notice = new SysNotice();
        notice.setNoticeId(noticeId);
        notice.setNoticeContent(content);
        return notice;
    }

    private NotifyDeliveryEvent notifyEvent(Long notifyLogId, List<Long> attachments) {
        NotifyRequest request = NotifyRequest.builder()
            .requestId("request-" + notifyLogId)
            .channel(NotifyChannel.MAIL)
            .targets(List.of())
            .content(new NotifyTextContent(null, "content"))
            .build();
        NotifyResult result = new NotifyResult(request.requestId(), NotifyChannel.MAIL, "provider",
            NotifyStatus.ACCEPTED, List.of());
        return new NotifyDeliveryEvent(request, NotifyContext.empty(), result, null,
            notifyLogId, attachments, Instant.parse("2026-08-23T00:00:00Z"));
    }
}

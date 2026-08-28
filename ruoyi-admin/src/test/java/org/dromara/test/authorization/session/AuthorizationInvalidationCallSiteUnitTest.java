package org.dromara.test.authorization.session;

import org.dromara.common.mybatis.core.mapper.LambdaCrudChainWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import org.dromara.system.api.OssService;
import org.dromara.system.controller.system.SysRoleController;
import org.dromara.system.domain.SysUserRole;
import org.dromara.system.domain.bo.SysRoleBo;
import org.dromara.system.domain.vo.SysRoleVo;
import org.dromara.system.mapper.SysClientMapper;
import org.dromara.system.mapper.SysDeptMapper;
import org.dromara.system.mapper.SysPostMapper;
import org.dromara.system.mapper.SysRoleMapper;
import org.dromara.system.mapper.SysUserMapper;
import org.dromara.system.mapper.SysUserPostMapper;
import org.dromara.system.mapper.SysUserRoleMapper;
import org.dromara.system.mapper.SysUserTypeMapper;
import org.dromara.system.service.ClientSessionService;
import org.dromara.system.service.ISysDeptService;
import org.dromara.system.service.ISysRoleService;
import org.dromara.system.service.ISysUserService;
import org.dromara.system.service.ISysUserTypeRelService;
import org.dromara.system.service.impl.SysUserServiceImpl;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@Tag("dev")
class AuthorizationInvalidationCallSiteUnitTest {

    @Test
    void rolePermissionWriteInvalidatesTheRolesWholeClientBeforeSuccessReturns() {
        ISysRoleService roles = mock(ISysRoleService.class);
        ClientSessionService sessions = mock(ClientSessionService.class);
        SysRoleController controller = new SysRoleController(
            roles, mock(ISysUserService.class), mock(ISysDeptService.class), sessions);
        SysRoleBo command = new SysRoleBo();
        command.setRoleId(7L);
        command.setRoleName("client-a-role");
        SysRoleVo storedRole = new SysRoleVo();
        storedRole.setClientId(10L);
        when(roles.updateRolePermission(command)).thenReturn(1);
        when(roles.selectRoleById(7L)).thenReturn(storedRole);

        controller.editPermission(command);

        InOrder order = inOrder(roles, sessions);
        order.verify(roles).updateRolePermission(command);
        order.verify(roles).selectRoleById(7L);
        order.verify(sessions).kickoutClient(10L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void explicitClientRoleClearInvalidatesTheTargetExactlyOnce() {
        SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class, RETURNS_DEEP_STUBS);
        LambdaCrudChainWrapper<SysUserRole, SysUserRole> query = mock(LambdaCrudChainWrapper.class, RETURNS_SELF);
        org.mockito.Mockito.when(userRoleMapper.lambda()).thenReturn(query);
        org.mockito.Mockito.when(query.eq(any(SFunction.class), any())).thenReturn(query);
        org.mockito.Mockito.when(query.list()).thenReturn(List.of());
        ClientSessionService sessions = mock(ClientSessionService.class);
        SysUserServiceImpl service = new SysUserServiceImpl(
            mock(SysUserMapper.class),
            mock(SysDeptMapper.class),
            mock(SysRoleMapper.class),
            mock(SysPostMapper.class),
            userRoleMapper,
            mock(SysUserPostMapper.class),
            mock(SysClientMapper.class),
            mock(SysUserTypeMapper.class),
            sessions,
            mock(ISysUserTypeRelService.class),
            mock(OssService.class));

        service.insertUserAuth(1L, new Long[0], 10L);

        verify(sessions, times(1)).kickoutUserClient(1L, 10L);
    }
}

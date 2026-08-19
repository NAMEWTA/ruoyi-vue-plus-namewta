package org.dromara.web.service;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.crypto.digest.BCrypt;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.constant.Constants;
import org.dromara.common.core.constant.GlobalConstants;
import org.dromara.common.core.constant.SystemConstants;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.exception.user.CaptchaException;
import org.dromara.common.core.exception.user.CaptchaExpireException;
import org.dromara.common.core.exception.user.UserException;
import org.dromara.common.core.utils.MessageUtils;
import org.dromara.common.core.utils.ServletUtils;
import org.dromara.common.core.utils.SpringUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.log.event.LoginInfoEvent;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.web.config.properties.CaptchaProperties;
import org.dromara.system.api.model.RegisterBody;
import org.dromara.system.domain.SysUser;
import org.dromara.system.domain.bo.SysUserBo;
import org.dromara.system.domain.constant.UserTypeGrantSource;
import org.dromara.system.domain.vo.SysClientVo;
import org.dromara.system.domain.vo.SysUserTypeVo;
import org.dromara.system.mapper.SysUserMapper;
import org.dromara.system.service.ISysClientService;
import org.dromara.system.service.ISysUserService;
import org.dromara.system.service.ISysUserTypeRelService;
import org.dromara.system.service.ISysUserTypeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 注册校验方法
 *
 * @author Lion Li
 */
@RequiredArgsConstructor
@Service
public class SysRegisterService {

    private final ISysUserService userService;
    private final SysUserMapper userMapper;
    private final CaptchaProperties captchaProperties;
    private final ISysClientService clientService;
    private final ISysUserTypeService userTypeService;
    private final ISysUserTypeRelService userTypeRelService;

    /**
     * 按客户端策略注册用户，并在同一事务内写入登录域关系。
     *
     * @param registerBody 注册请求参数
     */
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterBody registerBody) {
        SysClientVo client = clientService.queryByClientId(registerBody.getClientId());
        if (ObjectUtil.isNull(client) || !SystemConstants.NORMAL.equals(client.getStatus())) {
            throw new ServiceException("客户端不存在或已停用");
        }
        if (!Boolean.TRUE.equals(client.getRegisterEnabled())) {
            throw new ServiceException("当前应用未开放注册");
        }
        if (ObjectUtil.isNull(client.getUserTypeId())) {
            throw new ServiceException("客户端未配置登录域");
        }
        SysUserTypeVo userType = userTypeService.queryById(client.getUserTypeId());
        if (ObjectUtil.isNull(userType) || !SystemConstants.NORMAL.equals(userType.getStatus())) {
            throw new ServiceException("登录域不存在或已停用");
        }

        String username = registerBody.getUsername();
        String password = registerBody.getPassword();
        boolean captchaEnabled = captchaProperties.getEnable();
        if (captchaEnabled) {
            validateCaptcha(username, registerBody.getCode(), registerBody.getUuid());
        }

        boolean exist = userMapper.lambda()
            .eq(SysUser::getUserName, username)
            .exists();
        if (exist) {
            throw new ServiceException("该账号已存在，请登录");
        }

        SysUserBo sysUser = new SysUserBo();
        sysUser.setUserName(username);
        sysUser.setNickName(username);
        sysUser.setPassword(BCrypt.hashpw(password));
        boolean regFlag = userService.registerUser(sysUser);
        if (!regFlag) {
            throw new UserException("user.register.error");
        }
        userTypeRelService.grantUserType(sysUser.getUserId(), client.getUserTypeId(), UserTypeGrantSource.SELF_REGISTER);
        recordLoginInfo(username, Constants.REGISTER, MessageUtils.message("user.register.success"));
    }

    /**
     * 校验验证码
     *
     * @param username 用户名
     * @param code     验证码
     * @param uuid     唯一标识
     */
    public void validateCaptcha(String username, String code, String uuid) {
        String verifyKey = GlobalConstants.CAPTCHA_CODE_KEY + StringUtils.blankToDefault(uuid, "");
        String captcha = RedisUtils.getCacheObject(verifyKey);
        RedisUtils.deleteObject(verifyKey);
        if (captcha == null) {
            recordLoginInfo(username, Constants.LOGIN_FAIL, MessageUtils.message("user.jcaptcha.expire"));
            throw new CaptchaExpireException();
        }
        if (!StringUtils.equalsIgnoreCase(code, captcha)) {
            recordLoginInfo(username, Constants.LOGIN_FAIL, MessageUtils.message("user.jcaptcha.error"));
            throw new CaptchaException();
        }
    }

    /**
     * 记录登录信息
     *
     * @param username 用户名
     * @param status   状态
     * @param message  消息内容
     */
    private void recordLoginInfo(String username, String status, String message) {
        LoginInfoEvent loginInfoEvent = new LoginInfoEvent();
        loginInfoEvent.setUsername(username);
        loginInfoEvent.setStatus(status);
        loginInfoEvent.setMessage(message);
        HttpServletRequest request = ServletUtils.getRequest();
        if (request != null) {
            loginInfoEvent.setIp(ServletUtils.getClientIP(request));
            loginInfoEvent.setUserAgent(request.getHeader("User-Agent"));
            loginInfoEvent.setClientId(request.getHeader(LoginHelper.CLIENT_KEY));
        }
        SpringUtils.context().publishEvent(loginInfoEvent);
    }

}

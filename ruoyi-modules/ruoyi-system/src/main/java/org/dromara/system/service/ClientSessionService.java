package org.dromara.system.service;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.api.model.LoginUser;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Predicate;

/**
 * 按登录域或客户端清理在线会话。
 *
 * @author NAMEWTA
 */
@Slf4j
@Service
public class ClientSessionService {

    /**
     * 踢出指定用户在指定登录域下的全部 Token。userId 为空时踢出该登录域的全部 Token。
     *
     * @param userId       用户ID，可空
     * @param userTypeCode 登录域编码
     */
    public void kickoutUserType(Long userId, String userTypeCode) {
        if (StringUtils.isBlank(userTypeCode)) {
            return;
        }
        forEachToken(loginUser -> userTypeCode.equals(loginUser.getUserType())
            && (userId == null || userId.equals(loginUser.getUserId())));
    }

    /**
     * 踢出指定客户端（主键）下的全部 Token。
     *
     * @param clientId 客户端主键
     */
    public void kickoutClient(Long clientId) {
        if (clientId == null) {
            return;
        }
        forEachToken(loginUser -> clientId.equals(loginUser.getClientPk()));
    }

    /**
     * 踢出指定用户在指定客户端下的 Token。
     *
     * @param userId   用户ID
     * @param clientId 客户端主键
     */
    public void kickoutUserClient(Long userId, Long clientId) {
        if (userId == null || clientId == null) {
            return;
        }
        forEachToken(loginUser -> userId.equals(loginUser.getUserId())
            && clientId.equals(loginUser.getClientPk()));
    }

    /**
     * 遍历当前全部在线 Token，对命中的会话执行登出。
     *
     * @param matcher 登录快照匹配器
     */
    private void forEachToken(Predicate<LoginUser> matcher) {
        List<String> keys = StpUtil.searchTokenValue("", 0, -1, false);
        if (CollUtil.isEmpty(keys)) {
            return;
        }
        keys.parallelStream().forEach(key -> {
            String token = StringUtils.substringAfterLast(key, StringUtils.COLON);
            if (StpUtil.stpLogic.getTokenActiveTimeoutByToken(token) < -1) {
                return;
            }
            LoginUser loginUser = LoginHelper.getLoginUser(token);
            if (ObjectUtil.isNull(loginUser) || !matcher.test(loginUser)) {
                return;
            }
            try {
                StpUtil.logoutByTokenValue(token);
            } catch (NotLoginException ignored) {
            }
        });
    }

}

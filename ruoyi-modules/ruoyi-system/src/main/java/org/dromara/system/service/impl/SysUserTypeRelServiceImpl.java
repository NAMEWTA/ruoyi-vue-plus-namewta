package org.dromara.system.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.constant.SystemConstants;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StreamUtils;
import org.dromara.system.domain.SysUserType;
import org.dromara.system.domain.SysUserTypeRel;
import org.dromara.system.domain.vo.SysUserTypeRelVo;
import org.dromara.system.domain.vo.SysUserTypeVo;
import org.dromara.system.mapper.SysUserTypeMapper;
import org.dromara.system.mapper.SysUserTypeRelMapper;
import org.dromara.system.service.ClientSessionService;
import org.dromara.system.service.ISysUserTypeRelService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 用户登录域关系Service业务层处理
 *
 * @author NAMEWTA
 */
@RequiredArgsConstructor
@Service
public class SysUserTypeRelServiceImpl implements ISysUserTypeRelService {

    private final SysUserTypeRelMapper userTypeRelMapper;
    private final SysUserTypeMapper userTypeMapper;
    private final ClientSessionService clientSessionService;

    /**
     * 查询用户拥有的登录域关系
     *
     * @param userId 用户ID
     * @return 登录域关系列表
     */
    @Override
    public List<SysUserTypeRelVo> selectByUserId(Long userId) {
        if (ObjectUtil.isNull(userId)) {
            return List.of();
        }
        return userTypeRelMapper.selectVoListByUserId(userId);
    }

    /**
     * 判断用户是否拥有指定且正常的登录域
     *
     * @param userId     用户ID
     * @param userTypeId 登录域ID
     * @return 是否拥有
     */
    @Override
    public boolean hasUserType(Long userId, Long userTypeId) {
        return ObjectUtil.isNotNull(getActiveUserType(userId, userTypeId));
    }

    /**
     * 查询用户在指定登录域上的有效关系，并校验登录域本身启用。
     *
     * @param userId     用户ID
     * @param userTypeId 登录域ID
     * @return 登录域，不存在或停用时返回 null
     */
    @Override
    public SysUserTypeVo getActiveUserType(Long userId, Long userTypeId) {
        if (ObjectUtil.isNull(userId) || ObjectUtil.isNull(userTypeId)) {
            return null;
        }
        boolean owned = userTypeRelMapper.lambda()
            .eq(SysUserTypeRel::getUserId, userId)
            .eq(SysUserTypeRel::getUserTypeId, userTypeId)
            .eq(SysUserTypeRel::getStatus, SystemConstants.NORMAL)
            .exists();
        if (!owned) {
            return null;
        }
        SysUserTypeVo userType = userTypeMapper.selectVoById(userTypeId);
        if (ObjectUtil.isNull(userType) || !SystemConstants.NORMAL.equals(userType.getStatus())) {
            return null;
        }
        return userType;
    }

    /**
     * 覆盖更新用户登录域。
     *
     * @param userId      用户ID
     * @param userTypeIds 目标登录域ID集合
     * @param grantSource 新增关系的授权来源
     * @return 被移除的登录域编码列表
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public List<String> coverUserTypes(Long userId, Collection<Long> userTypeIds, String grantSource) {
        if (ObjectUtil.isNull(userId)) {
            throw new ServiceException("用户ID不能为空");
        }
        List<SysUserTypeRelVo> current = userTypeRelMapper.selectVoListByUserId(userId);
        Set<Long> targetIds = CollUtil.isEmpty(userTypeIds) ? Set.of() : new HashSet<>(userTypeIds);
        if (CollUtil.isNotEmpty(targetIds)) {
            long count = userTypeMapper.lambda().in(SysUserType::getUserTypeId, targetIds).count();
            if (count != targetIds.size()) {
                throw new ServiceException("存在无效的登录域");
            }
        }
        List<String> removedCodes = new ArrayList<>();
        for (SysUserTypeRelVo rel : current) {
            if (!targetIds.contains(rel.getUserTypeId())) {
                userTypeRelMapper.deleteById(rel.getRelId());
                removedCodes.add(rel.getUserTypeCode());
            }
        }
        Set<Long> currentIds = StreamUtils.toSet(current, SysUserTypeRelVo::getUserTypeId);
        for (Long userTypeId : targetIds) {
            if (!currentIds.contains(userTypeId)) {
                grantUserType(userId, userTypeId, grantSource);
            }
        }
        for (String removedCode : removedCodes) {
            clientSessionService.kickoutUserType(userId, removedCode);
        }
        return removedCodes;
    }

    /**
     * 为用户追加一个登录域（已存在则忽略）。
     *
     * @param userId      用户ID
     * @param userTypeId  登录域ID
     * @param grantSource 授权来源
     * @return 是否新增
     */
    @Override
    public boolean grantUserType(Long userId, Long userTypeId, String grantSource) {
        boolean exist = userTypeRelMapper.lambda()
            .eq(SysUserTypeRel::getUserId, userId)
            .eq(SysUserTypeRel::getUserTypeId, userTypeId)
            .exists();
        if (exist) {
            return false;
        }
        SysUserTypeRel rel = new SysUserTypeRel();
        rel.setUserId(userId);
        rel.setUserTypeId(userTypeId);
        rel.setGrantSource(grantSource);
        rel.setStatus(SystemConstants.NORMAL);
        return userTypeRelMapper.insert(rel) > 0;
    }

    /**
     * 删除用户全部登录域关系
     *
     * @param userIds 用户ID集合
     */
    @Override
    public void deleteByUserIds(Collection<Long> userIds) {
        if (CollUtil.isEmpty(userIds)) {
            return;
        }
        userTypeRelMapper.lambda().in(SysUserTypeRel::getUserId, userIds).delete();
    }

}

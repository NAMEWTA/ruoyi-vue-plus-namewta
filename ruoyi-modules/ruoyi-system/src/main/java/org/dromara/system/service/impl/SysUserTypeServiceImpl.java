package org.dromara.system.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.constant.CacheNames;
import org.dromara.common.core.constant.SystemConstants;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.query.QueryBuilder;
import org.dromara.system.domain.SysClient;
import org.dromara.system.domain.SysUserType;
import org.dromara.system.domain.SysUserTypeRel;
import org.dromara.system.domain.bo.SysUserTypeBo;
import org.dromara.system.domain.vo.SysUserTypeVo;
import org.dromara.system.mapper.SysClientMapper;
import org.dromara.system.mapper.SysUserTypeMapper;
import org.dromara.system.mapper.SysUserTypeRelMapper;
import org.dromara.system.service.ClientSessionService;
import org.dromara.system.service.ISysUserTypeService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * 登录域Service业务层处理
 *
 * @author NAMEWTA
 */
@RequiredArgsConstructor
@Service
public class SysUserTypeServiceImpl implements ISysUserTypeService {

    private final SysUserTypeMapper userTypeMapper;
    private final SysUserTypeRelMapper userTypeRelMapper;
    private final SysClientMapper clientMapper;
    private final ClientSessionService clientSessionService;

    /**
     * 查询登录域
     *
     * @param userTypeId 登录域ID
     * @return 登录域
     */
    @Override
    public SysUserTypeVo queryById(Long userTypeId) {
        return userTypeMapper.selectVoById(userTypeId);
    }

    /**
     * 按编码查询登录域
     *
     * @param userTypeCode 登录域编码
     * @return 登录域
     */
    @Cacheable(cacheNames = CacheNames.SYS_USER_TYPE, key = "#userTypeCode", condition = "#userTypeCode != null")
    @Override
    public SysUserTypeVo queryByCode(String userTypeCode) {
        return userTypeMapper.lambda().eq(SysUserType::getUserTypeCode, userTypeCode).voOne();
    }

    /**
     * 分页查询登录域列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 登录域分页列表
     */
    @Override
    public PageResult<SysUserTypeVo> queryPageList(SysUserTypeBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SysUserType> lqw = buildQueryWrapper(bo);
        Page<SysUserTypeVo> result = userTypeMapper.selectVoPage(pageQuery.build(), lqw);
        return PageResult.build(result.getRecords(), result.getTotal());
    }

    /**
     * 查询登录域列表
     *
     * @param bo 查询条件
     * @return 登录域列表
     */
    @Override
    public List<SysUserTypeVo> queryList(SysUserTypeBo bo) {
        return userTypeMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 查询启用中的登录域下拉列表
     *
     * @return 登录域列表
     */
    @Override
    public List<SysUserTypeVo> optionselect() {
        return userTypeMapper.lambda()
            .eq(SysUserType::getStatus, SystemConstants.NORMAL)
            .orderByAsc(SysUserType::getOrderNum)
            .voList();
    }

    /**
     * 构造登录域列表查询条件。
     *
     * @param bo 筛选条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<SysUserType> buildQueryWrapper(SysUserTypeBo bo) {
        return QueryBuilder.lambda(SysUserType.class)
            .likeIfText(SysUserType::getUserTypeCode, bo.getUserTypeCode())
            .likeIfText(SysUserType::getUserTypeName, bo.getUserTypeName())
            .eqIfText(SysUserType::getStatus, bo.getStatus())
            .orderByAsc(SysUserType::getOrderNum, SysUserType::getUserTypeId)
            .build();
    }

    /**
     * 新增登录域
     *
     * @param bo 登录域信息
     * @return 是否成功
     */
    @Override
    public Boolean insertByBo(SysUserTypeBo bo) {
        SysUserType add = MapstructUtils.convert(bo, SysUserType.class);
        boolean flag = userTypeMapper.insert(add) > 0;
        if (flag) {
            bo.setUserTypeId(add.getUserTypeId());
        }
        return flag;
    }

    /**
     * 修改登录域。编码创建后只读，忽略入参中的编码。
     *
     * @param bo 登录域信息
     * @return 是否成功
     */
    @Caching(evict = {
        @CacheEvict(cacheNames = CacheNames.SYS_USER_TYPE, allEntries = true)
    })
    @Override
    public Boolean updateByBo(SysUserTypeBo bo) {
        SysUserType update = MapstructUtils.convert(bo, SysUserType.class);
        update.setUserTypeCode(null);
        SysUserType db = userTypeMapper.selectById(bo.getUserTypeId());
        boolean flag = userTypeMapper.updateById(update) > 0;
        if (flag && ObjectUtil.isNotNull(db)
            && SystemConstants.DISABLE.equals(update.getStatus())
            && !SystemConstants.DISABLE.equals(db.getStatus())) {
            clientSessionService.kickoutUserType(null, db.getUserTypeCode());
        }
        return flag;
    }

    /**
     * 修改登录域状态
     *
     * @param userTypeId 登录域ID
     * @param status     状态
     * @return 更新条数
     */
    @CacheEvict(cacheNames = CacheNames.SYS_USER_TYPE, allEntries = true)
    @Override
    public int updateStatus(Long userTypeId, String status) {
        if (ObjectUtil.isNull(userTypeId)) {
            throw new ServiceException("登录域ID不能为空");
        }
        SysUserType userType = userTypeMapper.selectById(userTypeId);
        int rows = userTypeMapper.lambda()
            .set(SysUserType::getStatus, status)
            .eq(SysUserType::getUserTypeId, userTypeId)
            .updateCount();
        if (rows > 0 && SystemConstants.DISABLE.equals(status) && ObjectUtil.isNotNull(userType)) {
            clientSessionService.kickoutUserType(null, userType.getUserTypeCode());
        }
        return rows;
    }

    /**
     * 校验并批量删除登录域。仍被用户引用时拒绝删除。
     *
     * @param ids 登录域ID集合
     * @return 是否成功
     */
    @CacheEvict(cacheNames = CacheNames.SYS_USER_TYPE, allEntries = true)
    @Override
    public Boolean deleteWithValidByIds(Collection<Long> ids) {
        List<SysUserType> list = userTypeMapper.selectByIds(ids);
        for (SysUserType userType : list) {
            boolean referenced = userTypeRelMapper.lambda()
                .eq(SysUserTypeRel::getUserTypeId, userType.getUserTypeId())
                .exists();
            if (referenced) {
                throw new ServiceException("{}已被用户引用，不能删除!", userType.getUserTypeName());
            }
            boolean usedByClient = clientMapper.lambda()
                .eq(SysClient::getUserTypeId, userType.getUserTypeId())
                .exists();
            if (usedByClient) {
                throw new ServiceException("{}已被客户端引用，不能删除!", userType.getUserTypeName());
            }
        }
        return userTypeMapper.deleteByIds(ids) > 0;
    }

    /**
     * 校验登录域编码是否唯一
     *
     * @param bo 登录域信息
     * @return 是否唯一
     */
    @Override
    public boolean checkUserTypeCodeUnique(SysUserTypeBo bo) {
        boolean exist = userTypeMapper.lambda()
            .eq(SysUserType::getUserTypeCode, bo.getUserTypeCode())
            .neIfPresent(SysUserType::getUserTypeId, bo.getUserTypeId())
            .exists();
        return !exist;
    }

}

package org.dromara.system.service.impl;

import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.ObjectUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.query.LambdaQueryBuilder;
import org.dromara.common.mybatis.core.query.QueryBuilder;
import org.dromara.system.domain.SysNotice;
import org.dromara.system.api.OssService;
import org.dromara.system.domain.SysUser;
import org.dromara.system.domain.bo.SysNoticeBo;
import org.dromara.system.domain.vo.SysNoticeVo;
import org.dromara.system.domain.vo.SysUserVo;
import org.dromara.system.mapper.SysNoticeMapper;
import org.dromara.system.mapper.SysUserMapper;
import org.dromara.system.service.ISysNoticeService;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 公告 服务层实现
 *
 * @author Lion Li
 */
@RequiredArgsConstructor
@Service
public class SysNoticeServiceImpl implements ISysNoticeService {

    private static final Pattern OSS_MARKER = Pattern.compile("oss://([0-9]+)");
    private static final String NOTICE_TABLE = "sys_notice";

    private final SysNoticeMapper noticeMapper;
    private final SysUserMapper userMapper;
    private final OssService ossService;

    /**
     * 分页查询通知公告列表
     *
     * @param notice    查询条件
     * @param pageQuery 分页参数
     * @return 通知公告分页列表
     */
    @Override
    public PageResult<SysNoticeVo> selectPageNoticeList(SysNoticeBo notice, PageQuery pageQuery) {
        LambdaQueryWrapper<SysNotice> lqw = buildQueryWrapper(notice);
        Page<SysNoticeVo> page = noticeMapper.selectVoPage(pageQuery.build(), lqw);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    /**
     * 查询公告信息
     *
     * @param noticeId 公告ID
     * @return 公告信息
     */
    @Override
    public SysNoticeVo selectNoticeById(Long noticeId) {
        return noticeMapper.selectVoById(noticeId);
    }

    @Override
    public Map<Long, OssService.OssDownloadUrl> attachmentDownloads(Long noticeId) {
        SysNotice notice = noticeMapper.selectById(noticeId);
        if (notice == null) {
            return Map.of();
        }
        Map<Long, OssService.OssDownloadUrl> downloads = new LinkedHashMap<>();
        ossIds(notice.getNoticeContent()).forEach(ossId -> downloads.put(ossId, ossService.presignDownload(ossId)));
        return downloads;
    }

    /**
     * 查询公告列表
     *
     * @param notice 公告信息
     * @return 公告集合
     */
    @Override
    public List<SysNoticeVo> selectNoticeList(SysNoticeBo notice) {
        LambdaQueryWrapper<SysNotice> lqw = buildQueryWrapper(notice);
        return noticeMapper.selectVoList(lqw);
    }

    /**
     * 构造公告列表查询条件。
     *
     * @param bo 公告筛选条件
     * @return 包含标题、类型、创建人和排序条件的查询包装器
     */
    private LambdaQueryWrapper<SysNotice> buildQueryWrapper(SysNoticeBo bo) {
        LambdaQueryBuilder<SysNotice> builder = QueryBuilder.lambda(SysNotice.class)
            .likeIfText(SysNotice::getNoticeTitle, bo.getNoticeTitle())
            .eqIfText(SysNotice::getNoticeType, bo.getNoticeType());
        if (StringUtils.isNotBlank(bo.getCreateByName())) {
            SysUserVo sysUser = userMapper.lambda().eq(SysUser::getUserName, bo.getCreateByName()).voOne();
            builder.eq(SysNotice::getCreateBy, ObjectUtils.notNullGetter(sysUser, SysUserVo::getUserId));
        }
        return builder.orderByAsc(SysNotice::getNoticeId).build();
    }

    /**
     * 新增公告
     *
     * @param bo 公告信息
     * @return 结果
     */
    @Override
    @DSTransactional
    public int insertNotice(SysNoticeBo bo) {
        SysNotice notice = MapstructUtils.convert(bo, SysNotice.class);
        int rows = noticeMapper.insert(notice);
        bo.setNoticeId(notice.getNoticeId());
        if (rows > 0) {
            bindAll(ossIds(bo.getNoticeContent()), notice.getNoticeId());
        }
        return rows;
    }

    /**
     * 修改公告
     *
     * @param bo 公告信息
     * @return 结果
     */
    @Override
    @DSTransactional
    public int updateNotice(SysNoticeBo bo) {
        SysNotice existing = noticeMapper.selectById(bo.getNoticeId());
        SysNotice notice = MapstructUtils.convert(bo, SysNotice.class);
        int rows = noticeMapper.updateById(notice);
        if (rows > 0) {
            reconcile(existing == null ? Set.of() : ossIds(existing.getNoticeContent()),
                ossIds(bo.getNoticeContent()), bo.getNoticeId());
        }
        return rows;
    }

    /**
     * 删除公告对象
     *
     * @param noticeId 公告ID
     * @return 结果
     */
    @Override
    @DSTransactional
    public int deleteNoticeById(Long noticeId) {
        SysNotice existing = noticeMapper.selectById(noticeId);
        int rows = noticeMapper.deleteById(noticeId);
        if (rows > 0 && existing != null) {
            unbindAll(ossIds(existing.getNoticeContent()), noticeId);
        }
        return rows;
    }

    /**
     * 批量删除公告信息
     *
     * @param noticeIds 需要删除的公告ID
     * @return 结果
     */
    @Override
    @DSTransactional
    public int deleteNoticeByIds(Long[] noticeIds) {
        List<Long> ids = Arrays.asList(noticeIds);
        List<SysNotice> existing = noticeMapper.selectBatchIds(ids);
        int rows = noticeMapper.deleteByIds(ids);
        if (rows > 0) {
            existing.forEach(notice -> unbindAll(ossIds(notice.getNoticeContent()), notice.getNoticeId()));
        }
        return rows;
    }

    private Set<Long> ossIds(String content) {
        Set<Long> ids = new LinkedHashSet<>();
        if (content == null || content.isBlank()) {
            return ids;
        }
        Matcher matcher = OSS_MARKER.matcher(content);
        while (matcher.find()) {
            ids.add(Long.valueOf(matcher.group(1)));
        }
        return ids;
    }

    private void reconcile(Set<Long> previous, Set<Long> current, Long noticeId) {
        current.stream().filter(id -> !previous.contains(id)).forEach(id -> bind(id, noticeId));
        previous.stream().filter(id -> !current.contains(id)).forEach(id -> unbind(id, noticeId));
    }

    private void bindAll(Set<Long> ids, Long noticeId) {
        ids.forEach(id -> bind(id, noticeId));
    }

    private void unbindAll(Set<Long> ids, Long noticeId) {
        ids.forEach(id -> unbind(id, noticeId));
    }

    private void bind(Long ossId, Long noticeId) {
        ossService.bind(ossId, NOTICE_TABLE, String.valueOf(noticeId));
    }

    private void unbind(Long ossId, Long noticeId) {
        ossService.unbind(ossId, NOTICE_TABLE, String.valueOf(noticeId));
    }
}

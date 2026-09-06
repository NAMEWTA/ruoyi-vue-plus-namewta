package org.dromara.notify.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.notify.domain.entity.NotifyNotice;
import org.dromara.notify.domain.vo.NotifyNoticeVo;

/**
 * 通知中心公告数据访问。
 */
@Mapper
public interface NotifyNoticeMapper extends BaseMapperPlus<NotifyNotice, NotifyNoticeVo> {
    /** 在公告用例事务内串行化编辑、发布、撤回和删除。 */
    NotifyNotice selectByIdForUpdate(@Param("noticeId") Long noticeId);
}

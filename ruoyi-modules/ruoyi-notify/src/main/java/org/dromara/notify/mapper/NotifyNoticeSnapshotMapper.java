package org.dromara.notify.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.notify.domain.entity.NotifyNoticeSnapshot;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 公告发布快照数据访问。
 */
@Mapper
public interface NotifyNoticeSnapshotMapper extends BaseMapperPlus<NotifyNoticeSnapshot, NotifyNoticeSnapshot> {
}

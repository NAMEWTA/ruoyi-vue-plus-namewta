package org.dromara.notify.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.notify.domain.entity.NotifyMessage;

/**
 * 通知中心站内消息数据访问。
 */
@Mapper
public interface NotifyMessageMapper extends BaseMapperPlus<NotifyMessage, NotifyMessage> {
}

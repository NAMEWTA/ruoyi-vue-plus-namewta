package org.dromara.notify.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.notify.domain.entity.NotifyMessageRecipient;

/**
 * 通知中心收件关系数据访问。
 */
@Mapper
public interface NotifyMessageRecipientMapper extends BaseMapperPlus<NotifyMessageRecipient, NotifyMessageRecipient> {
}

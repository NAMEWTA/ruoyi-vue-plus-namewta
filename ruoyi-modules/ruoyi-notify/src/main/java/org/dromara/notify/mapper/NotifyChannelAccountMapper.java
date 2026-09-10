package org.dromara.notify.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.notify.domain.entity.NotifyChannelAccount;
import org.dromara.notify.domain.vo.NotifyChannelAccountVo;

/**
 * 渠道账号数据访问。
 */
@Mapper
public interface NotifyChannelAccountMapper extends BaseMapperPlus<NotifyChannelAccount, NotifyChannelAccountVo> {
}

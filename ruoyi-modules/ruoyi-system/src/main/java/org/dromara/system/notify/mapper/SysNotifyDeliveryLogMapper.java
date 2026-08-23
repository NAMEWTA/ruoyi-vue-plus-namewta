package org.dromara.system.notify.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.system.notify.domain.SysNotifyDeliveryLog;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;

/**
 * 通知目标投递日志 Mapper。
 */
public interface SysNotifyDeliveryLogMapper extends BaseMapperPlus<SysNotifyDeliveryLog, SysNotifyDeliveryLog> {

    int physicalDeleteByNotifyLogIds(@Param("notifyLogIds") Collection<Long> notifyLogIds);
}

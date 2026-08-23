package org.dromara.system.notify.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.system.notify.domain.SysNotifyLog;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * 通知逻辑日志 Mapper。
 */
public interface SysNotifyLogMapper extends BaseMapperPlus<SysNotifyLog, SysNotifyLog> {

    List<Long> selectCleanupBatch(@Param("limit") int limit);

    int physicalDeleteByIds(@Param("notifyLogIds") Collection<Long> notifyLogIds);
}

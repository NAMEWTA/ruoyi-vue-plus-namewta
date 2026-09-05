package org.dromara.notify.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.notify.domain.entity.NotifyOutbox;

import java.util.List;

/** Outbox Mapper。 */
@Mapper
public interface NotifyOutboxMapper extends BaseMapper<NotifyOutbox> {
    /** 领取到期且可执行的 Outbox 任务。 */
    List<NotifyOutbox> selectClaimable(String now, int limit);
}

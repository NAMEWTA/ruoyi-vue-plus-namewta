package org.dromara.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.system.domain.SysMessageRecipient;

/** 站内消息收件关系 Mapper。 */
@Mapper
public interface SysMessageRecipientMapper extends BaseMapper<SysMessageRecipient> { }

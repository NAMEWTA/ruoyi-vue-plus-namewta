package org.dromara.notify.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.notify.domain.entity.NotifySceneBinding;
import org.dromara.notify.domain.vo.NotifySceneBindingVo;

/**
 * 场景渠道绑定数据访问。
 */
@Mapper
public interface NotifySceneBindingMapper extends BaseMapperPlus<NotifySceneBinding, NotifySceneBindingVo> {
}

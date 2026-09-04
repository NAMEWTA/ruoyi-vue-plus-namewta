package org.dromara.third.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.third.domain.ThirdProvider;
import org.dromara.third.domain.row.ThirdProviderRow;

@Mapper
public interface ThirdProviderMapper extends BaseMapper<ThirdProvider> {
    ThirdProviderRow selectByProviderCode(String providerCode);
}

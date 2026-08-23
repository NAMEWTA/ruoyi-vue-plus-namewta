package org.dromara.system.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.system.domain.SysOss;
import org.dromara.system.domain.vo.SysOssVo;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 文件上传 数据层
 *
 * @author Lion Li
 */
public interface SysOssMapper extends BaseMapperPlus<SysOss, SysOssVo> {

    SysOss selectByIdForUpdate(@Param("ossId") Long ossId);

    List<SysOss> selectByIdsForUpdate(@Param("ossIds") Collection<Long> ossIds);

    int updateLifecycle(@Param("ossId") Long ossId, @Param("isTemp") String isTemp,
                        @Param("expireTime") LocalDateTime expireTime);

    int markDeletePending(@Param("ossId") Long ossId, @Param("expireTime") LocalDateTime expireTime);

    List<Long> selectExpiredTempIds(@Param("expireTime") LocalDateTime expireTime,
                                    @Param("limit") int limit);
}

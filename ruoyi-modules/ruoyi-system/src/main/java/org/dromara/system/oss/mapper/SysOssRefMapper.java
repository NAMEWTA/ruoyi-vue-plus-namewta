package org.dromara.system.oss.mapper;

import org.apache.ibatis.annotations.Param;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.system.oss.domain.SysOssRef;

import java.util.List;

/**
 * OSS 业务引用数据层。
 */
public interface SysOssRefMapper extends BaseMapperPlus<SysOssRef, SysOssRef> {

    long countActiveByOssId(@Param("ossId") Long ossId);

    boolean existsActive(@Param("ossId") Long ossId, @Param("refType") String refType,
                         @Param("refId") String refId);

    int restoreReference(@Param("ossId") Long ossId, @Param("refType") String refType,
                         @Param("refId") String refId);

    int deactivateReference(@Param("ossId") Long ossId, @Param("refType") String refType,
                            @Param("refId") String refId);

    List<SysOssRef> selectActiveByOssId(@Param("ossId") Long ossId);
}

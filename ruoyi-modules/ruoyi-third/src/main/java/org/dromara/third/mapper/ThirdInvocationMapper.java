package org.dromara.third.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.third.domain.ThirdInvocation;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ThirdInvocationMapper extends BaseMapper<ThirdInvocation> {
    @Select("select * from third_invocation where provider_code=#{providerCode} and create_time >= #{from} order by create_time desc limit 200")
    List<ThirdInvocation> selectRecent(String providerCode, LocalDateTime from);

    @Insert("insert into third_invocation(invocation_id,request_id,provider_code,endpoint_code,attempt_count,logical_status,failure_category,http_status,provider_error_code,duration_ms,sanitized_request_json,sanitized_response_json,create_time) values(#{invocationId},#{requestId},#{providerCode},#{endpointCode},#{attemptCount},#{logicalStatus},#{failureCategory},#{httpStatus},#{providerErrorCode},#{durationMs},#{sanitizedRequestJson},#{sanitizedResponseJson},#{createTime}) on duplicate key update attempt_count=values(attempt_count),logical_status=values(logical_status),failure_category=values(failure_category),http_status=values(http_status),duration_ms=values(duration_ms),sanitized_response_json=values(sanitized_response_json)")
    int upsert(ThirdInvocation invocation);
}

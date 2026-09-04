package org.dromara.third.dao;

import lombok.RequiredArgsConstructor;
import org.dromara.third.domain.ThirdInvocation;
import org.dromara.third.mapper.ThirdInvocationMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ThirdInvocationDao {
    private final ThirdInvocationMapper mapper;

    public int upsert(ThirdInvocation invocation) { return mapper.upsert(invocation); }

    public List<ThirdInvocation> findRecent(String providerCode, LocalDateTime from) { return mapper.selectRecent(providerCode, from); }
}

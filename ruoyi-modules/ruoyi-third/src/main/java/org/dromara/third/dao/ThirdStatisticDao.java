package org.dromara.third.dao;

import lombok.RequiredArgsConstructor;
import org.dromara.third.domain.ThirdStatistic;
import org.dromara.third.mapper.ThirdStatisticMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ThirdStatisticDao {
    private final ThirdStatisticMapper mapper;

    public int upsert(ThirdStatistic statistic) { return mapper.upsert(statistic); }

    public List<ThirdStatistic> findRecent(String providerCode) { return mapper.selectRecent(providerCode); }
}

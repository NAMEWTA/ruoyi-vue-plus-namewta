package org.dromara.third.port;

import org.dromara.third.domain.ThirdStatistic;

/** Statistic persistence boundary consumed by the observability adapter. */
public interface ThirdStatisticStore {
    int upsert(ThirdStatistic statistic);
}

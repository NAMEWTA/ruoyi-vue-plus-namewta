package org.dromara.third.support;

public interface ThirdLimitLease extends AutoCloseable {
    @Override
    void close();
}

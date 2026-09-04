package org.dromara.third.service;

public interface ThirdLimitLease extends AutoCloseable {
    @Override
    void close();
}

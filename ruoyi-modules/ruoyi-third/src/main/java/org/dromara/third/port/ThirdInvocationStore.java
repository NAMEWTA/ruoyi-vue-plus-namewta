package org.dromara.third.port;

import org.dromara.third.domain.ThirdInvocation;

/** Invocation persistence boundary consumed by the observability adapter. */
public interface ThirdInvocationStore {
    int upsert(ThirdInvocation invocation);
}

package org.dromara.common.nacos;

import org.springframework.boot.context.properties.bind.Binder;

import java.util.Set;

/**
 * 即时生效配置的无副作用准备接缝。
 *
 * @param <T> 不可变或仅由调用方读取的配置快照类型
 */
public interface NacosConfigParticipant<T> {

    String id();

    Set<String> prefixes();

    T prepare(Binder binder);
}

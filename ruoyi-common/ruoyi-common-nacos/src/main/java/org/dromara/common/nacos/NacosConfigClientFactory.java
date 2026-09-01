package org.dromara.common.nacos;

@FunctionalInterface
interface NacosConfigClientFactory {

    NacosConfigClient create(NacosConfigSettings settings) throws Exception;
}

# ruoyi-third

统一管理第三方 HTTP Provider 和 Endpoint。数据库是配置事实源，业务调用只能依赖 `ruoyi-api` 的 `ThirdPartyGateway` 合同。

## 结构

生产代码按 `controller/listener/api adapter -> usecase -> service -> dao -> mapper -> XML` 组织；凭据、URI 白名单、缓存快照、执行 Pipeline 和观测代码均属于本模块。

## 约束

- Provider 是唯一可信 base URL 与共享策略来源，Endpoint 只能使用经校验的相对 path 和白名单参数。
- 不使用数据库脚本、SpEL 或任意类名反射；特殊签名、加密、分页和错误映射使用显式 Java SPI。
- 业务表不保存完整请求/响应和明文凭据；出站日志经过服务端不可关闭的脱敏与大小限制。

## 验证

在后端仓库根目录运行 `mvn -pl ruoyi-modules/ruoyi-third -am -DskipTests compile`，再运行本模块定向测试。

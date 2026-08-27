# NAMEWTA 后端

本仓库是 NAMEWTA 的后端产品仓库，基于 RuoYi-Vue-Plus 维护。`main` 是产品实现权威，`6.X` 只跟踪上游能力，不承载本地业务提交。

## 技术栈

- Java 21、Spring Boot 4.1、Maven Wrapper。
- Sa-Token、MyBatis-Plus、dynamic-datasource、Redisson。
- MySQL 8.4 是 NAMEWTA 业务扩展当前唯一支持并自动化验收的数据库。
- Redis、MinIO 等外部服务用于会话、缓存、OSS 与集成测试。

## 模块结构

```text
ruoyi-admin/       主应用与模块组装
ruoyi-api/         跨业务模块公开服务和 DTO
ruoyi-common/      可按需依赖的通用基础能力
ruoyi-modules/     system、workflow、gen、demo、ai、job 业务模块
ruoyi-extend/      monitor、SnailJob、SnailAI 等独立应用
script/            Docker、NAMEWTA SQL 与运维脚本
```

`ruoyi-admin` 只负责组装。跨模块调用应通过 `ruoyi-api` 或明确的 common SPI，禁止依赖其他业务模块的 mapper、entity 或内部实现。

## 产品能力

- Client 登录域、注册准入、Token 和会话隔离。
- 按 `userId + clientPk` 计算的角色、菜单、权限和动态路由合同。
- OSS 浏览器直传、对象引用、临时文件与授权下载。
- 渠道无关通知分发、投递记录和监控。
- system、workflow、gen、demo、ai 等模块化 HTTP 能力。

前端只消费 HTTP/JSON 合同。Vue 页面、动态路由和 App 组合归独立仓库 `plus-ui-namewta` 所有，后端仓库不维护前端源码副本。

## 构建与验证

```bash
# 完整测试
./mvnw test

# 默认全量组合
./mvnw clean package -DskipTests

# 核心平台组合；必须在完整测试通过后执行
./mvnw clean package -Pbundle-core -Dmaven.test.skip=true
```

每次 bundle 构建都必须先 `clean`，避免复用另一 profile 的 fat jar。Maven 默认执行测试，只有已有独立测试证据的打包阶段才允许跳过。

## HTTP 与数据规则

- CRUD 只读查询使用 `GET`。
- 新增、修改、删除、状态和排序等业务变更使用 `POST`。
- 每个 POST 业务接口使用准确、安全的 `@Log` 记录调用追踪。
- 新建或实质修改的业务事务使用 `@DSTransactional`；事务事件使用匹配的 `@DsTxEventListener`。
- 权限、数据范围、缓存失效、关联维护和删除前校验以同模块成熟实现为准。

## SQL

上游 `script/sql/ry_vue.sql` 保持不变。NAMEWTA 增量位于：

- `script/sql/namewta/DDL.sql`：结构变更。
- `script/sql/namewta/DML.sql`：初始化、回填和补偿。

两个文件均采用末尾追加策略，具体执行顺序和迁移约束见 [SQL 说明](script/sql/namewta/README.md)。

## 开发导航

- Codex Skill：[ruoyi-backend-development](.codex/skills/ruoyi-backend-development/SKILL.md)
- Claude Agent：[ruoyi-backend-development](.claude/agents/ruoyi-backend-development.md)
- 上游边界：[docs/upstream/README.md](docs/upstream/README.md)

在父工作区中，长期工程规范以 `ruoyi-vue-plus-docs/.agents/skills/engineering-standards/` 为准；独立克隆本仓库时使用上述本地导航，并以当前源码、POM 和测试为最终证据。

## 许可证

本仓库保留上游项目许可证和署名，详见 `LICENSE`。

# ruoyi-notify

- 入口：`controller/admin`、`controller/anonymous`；业务调用按 `UseCase -> Service -> DAO -> Mapper` 分层。
- 所有通知、公告、收件箱、Outbox 和监控数据由本模块拥有；跨模块仅依赖 `ruoyi-api` 与 common SPI。
- DDL/DML：父仓库 `release-artifacts/docker/infrastructure/mysql/init/50-namewta-ddl.sql` 与 `60-namewta-dml.sql`。
- 测试：`./mvnw -pl ruoyi-modules/ruoyi-notify -am test`。

# API 与数据实现

## 取样顺序

1. 当前模块同形态的成熟实现。
2. `ruoyi-common-*` 和 `ruoyi-api` 的公开能力。
3. `ruoyi-gen/src/main/resources/fm/java` 模板。
4. 通用 Spring Boot 和 MyBatis-Plus 做法。

## 分层

标准业务保持 entity、BO、VO、mapper、service、service implementation、controller 分工。controller 负责传输、校验、权限、日志和返回映射；业务规则、事务和关系维护进入 service；mapper 负责查询与持久化。

## HTTP

- 列表、分页、详情、树和下拉等只读操作使用 GET。
- 新增、修改、删除、批量删除、状态和排序等变更使用 POST。
- 每个 POST 业务方法必须带有语义准确的 `@Log`，并避免记录凭据或敏感正文。
- `@SaCheckPermission`、数据权限和 Client 范围必须沿用同模块合同。

## 数据与事务

- BO/VO/entity 字段、校验和映射与真实 HTTP/数据库合同一致。
- 查询优先使用现有 `QueryBuilder`、`BaseMapperPlus`、MPJ 和数据权限模式。
- 新建或实质修改的业务事务使用 `@DSTransactional`；缓存写后失效，事务事件匹配数据源事务。
- 删除前校验、唯一性、关系维护、导入导出和翻译不得因套用模板而丢失。

## SQL

`script/sql/namewta/DDL.sql` 管理结构变化，`DML.sql` 管理数据变化。两者只允许在末尾追加已经设计好前置、重复执行和回滚方式的变更块。

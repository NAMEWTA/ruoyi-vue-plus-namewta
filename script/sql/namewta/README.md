# NAMEWTA 增量 SQL

本目录保存 NAMEWTA 相较上游新增的 Client、RBAC、OSS 生命周期、统一通知及后续产品 schema。仅维护和验收 MySQL，不同步 PostgreSQL、Oracle 或 SQL Server；上游框架的多数据源能力不等于本产品的数据库支持矩阵。不要修改上游 `script/sql/ry_vue.sql`。

## 产品支持矩阵

| 数据库 | NAMEWTA schema | 自动化验证 | 产品支持 |
|---|---|---|---|
| MySQL 8.4 | `DDL.sql` + `DML.sql` | fresh/upgrade integration | 是 |
| PostgreSQL / Oracle / SQL Server | 无 | 无 | 否 |

需要扩展方言时必须独立设计版本化迁移、回滚和 CI matrix；在这些条件满足前，不得将 dynamic-datasource 的连接能力描述为 NAMEWTA 产品支持。

本目录只允许存在两个 SQL 文件：

| 文件 | 职责 |
|---|---|
| `DDL.sql` | 表、字段、索引和约束等结构变更 |
| `DML.sql` | 初始化、回填和补偿等数据变更 |

## 全新环境

按以下顺序完整执行：

1. `script/sql/ry_vue.sql`，以及业务需要的 `ry_job.sql`、`ry_workflow.sql`、`ry_ai.sql`
2. `script/sql/namewta/DDL.sql`
3. `script/sql/namewta/DML.sql`

`DML.sql` 末尾的 `NAMEWTA-BASE-DSL-002` 使用 `NOT EXISTS`，全新环境执行时自动无操作。

## 已有环境

- 仅有 `ry_vue.sql` 基线：完整执行 `DDL.sql`，再完整执行 `DML.sql`。
- 已执行旧 `001`、`002`、`003`：不要重放两个 BASE-001 块；缺少三个用户端菜单时，只执行 `DML.sql` 中的 `NAMEWTA-BASE-DSL-002`。
- 已执行旧 `001` 至 `004`：视为已完成当前基线，后续只执行两个文件末尾新增且尚未应用的变更块。

## 密码策略发布与恢复

`NAMEWTA-PASSWORD-DSL-001` 必须在受控维护窗口执行。迁移前加密备份
`sys.user.initPassword` 整行以及待授权角色的菜单关系，并暂停注册、用户新增、导入、重置和个人改密等旧密码写入口。
发布顺序固定为：执行 DML -> 刷新 `sys_config` 集群缓存 -> 部署并确认全部 backend 节点读取
`sys.user.passwordPolicy` -> 发布 frontend -> 按角色显式授予 `system:user:temporaryPassword` -> 刷新菜单/权限缓存 -> 恢复写入口。
不得把该权限批量映射为 `system:user:resetPwd`，也不得在滚动窗口让旧节点继续弱写。

执行前必须确认 preflight 通过；失败时修复冲突后从本块起重新执行，不得绕过检查。重复执行会保留已存在的策略配置和菜单，
且不会再次改变已标记退役的旧键。验证至少包括：策略 JSON 可由当前 backend 解析且短于 500 字符、旧键值不同于迁移前备份并满足四类规则、
权限菜单唯一且未写入普通角色、配置和权限缓存已在所有节点刷新。

回滚时先回滚 frontend 并撤销普通角色授权，再从迁移前加密备份恢复旧 backend 可理解且合规的 legacy 配置；随后删除本块菜单关系、菜单和策略配置，
刷新配置/菜单/权限缓存后再回滚 backend。回滚和前向补偿均不得更新 `sys_user.password`，不得把生成值写入日志、命令历史、文档或 Evidence。
若 backend 已全部切换且数据本身可修复，优先修正冲突并重放本块作为前向补偿。

## 追加规则

`DDL.sql` 和 `DML.sql` 自 2026-08-21 起只允许在文件末尾追加。已经生成、提交或执行过的 SQL 禁止修改、删除、替换或重排。每个追加块必须包含以下中文注释：

```sql
-- 变更内容：<简明中文说明>
-- 变更标识：YYYY-MM-DD_HH:mm:ss
-- 执行前置：<依赖的基线或变更标识>
-- 适用范围：<fresh/upgrade 说明>
-- 重复执行：<是/否>
-- 回滚方式：<回滚或补偿方式>
```

生成上海时区的变更标识时间：

```bash
node script/sql/namewta/generate-change-timestamp.js
```

输出格式示例：`2026-08-21_18:30:45`。

生成与当前 MyBatis-Plus `ASSIGN_ID` 兼容的 Snowflake ID：

```bash
# 生成一个 ID
node script/sql/namewta/generate-snowflake-id.js

# 批量生成，结果逐行输出
node script/sql/namewta/generate-snowflake-id.js --count 10
```

ID 脚本只使用 Node.js 标准库，按本机网卡 MAC 和当前进程 PID 推导节点标识。输出是 64 位十进制字符串；在 JavaScript 中使用时必须保留为字符串或 `BigInt`，不得转换为会丢失精度的 `Number`。

上游跟随热点见父仓库 `docs/upstream/customization-map.md`，本仓库指针见 `docs/upstream/README.md`。

# NAMEWTA 多 APP RBAC SQL

仅维护 MySQL，不同步 PostgreSQL、Oracle 或 SQL Server。不要修改上游 `script/sql/ry_vue.sql`。

本目录只允许存在两个 SQL 文件：

| 文件 | 职责 |
|---|---|
| `DDL.sql` | 表、字段、索引和约束等结构变更 |
| `DSL.sql` | 初始化、回填和补偿等数据变更；DSL 是本项目约定的文件名 |

## 全新环境

按以下顺序完整执行：

1. `script/sql/ry_vue.sql`，以及业务需要的 `ry_job.sql`、`ry_workflow.sql`、`ry_ai.sql`
2. `script/sql/namewta/DDL.sql`
3. `script/sql/namewta/DSL.sql`

`DSL.sql` 末尾的 `NAMEWTA-BASE-DSL-002` 使用 `NOT EXISTS`，全新环境执行时自动无操作。

## 已有环境

- 仅有 `ry_vue.sql` 基线：完整执行 `DDL.sql`，再完整执行 `DSL.sql`。
- 已执行旧 `001`、`002`、`003`：不要重放两个 BASE-001 块；缺少三个用户端菜单时，只执行 `DSL.sql` 中的 `NAMEWTA-BASE-DSL-002`。
- 已执行旧 `001` 至 `004`：视为已完成当前基线，后续只执行两个文件末尾新增且尚未应用的变更块。

## 追加规则

`DDL.sql` 和 `DSL.sql` 自 2026-08-21 起只允许在文件末尾追加。已经生成、提交或执行过的 SQL 禁止修改、删除、替换或重排。每个追加块必须包含以下中文注释：

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

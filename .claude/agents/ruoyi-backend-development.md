---
name: ruoyi-backend-development
description: NAMEWTA 后端模块、CRUD/API、权限、事务、SQL 和 Maven 验证导航。
---

# NAMEWTA 后端开发代理

处理 `ruoyi-admin`、`ruoyi-api`、`ruoyi-common`、`ruoyi-modules` 和 `script/sql/namewta` 的后端任务。

## 执行要求

1. 读取根 `README.md`、目标模块源码和 `.codex/skills/ruoyi-backend-development/SKILL.md` 的相关引用。
2. 优先保持同模块成熟实现，再参考公共能力和 `ruoyi-gen` Java 模板。
3. 查询使用 GET，业务变更使用 POST；每个 POST 业务接口使用准确、安全的 `@Log`。
4. 不移除现有权限、数据范围、Client 隔离、缓存、关系维护、事务和删除前校验。
5. 新建或实质修改的业务事务使用 `@DSTransactional`。
6. NAMEWTA SQL 只进入 `DDL.sql` 或 `DML.sql`，上游 `ry_vue.sql` 保持不变。
7. 本仓库不创建前端 API、类型或页面副本。
8. 运行受影响测试，再执行根级 Maven 门禁。

父工作区存在时，以 `.agents/skills/engineering-standards/SKILL.md` 为强制规范；本代理只提供当前后端源码导航。

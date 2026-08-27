---
name: ruoyi-backend-development
description: 为 NAMEWTA RuoYi-Vue-Plus 后端提供当前模块边界、CRUD/API、数据权限、事务、SQL 与 Maven 验证导航。处理 ruoyi-admin、ruoyi-api、ruoyi-common、ruoyi-modules、controller、service、mapper、BO/VO/entity、GET/POST、@Log、@DSTransactional 或 script/sql/namewta 时使用。
---

# NAMEWTA 后端开发

本 Skill 只描述当前后端仓库的实现导航。父工作区存在时，强制规范与质量门禁由 `.agents/skills/engineering-standards` 裁决。

## 工作流程

1. 先确定 Maven 模块和业务 owner，再读取同模块最接近的实现。
2. 标准 CRUD 参考 `ruoyi-gen` Java 模板；system、workflow 等复杂模块优先保持现有权限、关系、缓存和事务语义。
3. 按任务读取引用：
   - 模块与依赖边界：[architecture.md](references/architecture.md)
   - API、CRUD 与数据实现：[implementation.md](references/implementation.md)
   - 测试、bundle 与交付：[verification.md](references/verification.md)
4. 跨模块只使用 `ruoyi-api` 或明确 common SPI，不深用其他模块的 mapper、entity 或实现类。
5. 修改后运行受影响测试，并执行与风险匹配的 Maven 根级门禁。

## 硬边界

- CRUD 查询使用 GET，业务变更使用 POST；每个 POST 业务接口使用准确、安全的 `@Log`。
- 新建或实质修改的业务事务使用 `@DSTransactional`，事务事件使用 `@DsTxEventListener`。
- `script/sql/ry_vue.sql` 保持不变；NAMEWTA 增量只进入 `script/sql/namewta/DDL.sql` 与 `DML.sql`。
- `main` 承载产品；`6.X` 只做上游镜像。
- 前端 API、模型、页面和 App 组合归 `plus-ui-namewta`，不得在本仓库恢复旧式前端副本。

## 事实优先级

发生冲突时依次采用：同模块成熟源码、公开基础能力、生成器模板、通用框架做法。摘要与文档不能替代源码、POM 和测试确认。

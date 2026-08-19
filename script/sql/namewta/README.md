# NAMEWTA 多 APP RBAC SQL

仅维护 MySQL。postgres / oracle / sqlserver 不同步。

**不要修改** 上游 `script/sql/ry_vue.sql`。全新库按以下顺序执行：

1. `script/sql/ry_vue.sql`（及业务需要的 `ry_job.sql` / `ry_workflow.sql` / `ry_ai.sql`）
2. `001_user_type.sql` — 登录域表、用户登录域关系表，删除 `sys_user.user_type`
3. `002_client_rbac.sql` — Client 登录域/注册/默认角色，角色与菜单归属 Client
4. `003_initial_data.sql` — 两个登录域、现有 Client/角色/菜单回填、演示 Client、删除全局注册开关

已有库从当前 `ry_vue.sql` 基线升级时，同样按 001 → 002 → 003 顺序执行一次即可。

上游跟随热点（认证/权限/Client 字段别名等）见父仓库 `docs/upstream/customization-map.md`，本仓库指针在 `docs/upstream/README.md`。

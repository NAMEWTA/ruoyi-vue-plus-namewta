# 上游边界

`main` 是 NAMEWTA 产品分支，`6.X` 是只允许 fast-forward 的上游镜像分支。业务提交不得进入 `6.X`，基线标签 `namewta-base-upstream-6x` 不得移动。

上游更新用于发现能力、缺陷修复和安全变化，不要求本地目录与上游同构。评估时应先确认行为和风险，再映射到当前 Maven 模块、公开 API 与测试。

在父工作区中，认证、权限、Client、菜单、OSS、通知和 SQL 的长期合并约束以 `docs/upstream/customization-map.md` 为准。

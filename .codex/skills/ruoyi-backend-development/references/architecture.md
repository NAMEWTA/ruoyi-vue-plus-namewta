# 模块与依赖边界

- `ruoyi-admin`：部署入口和模块组装，不承载可复用业务实现。
- `ruoyi-api`：跨业务模块公开服务与 DTO。
- `ruoyi-common-*`：按能力拆分的基础设施与稳定 SPI。
- `ruoyi-modules/ruoyi-system`：用户、Client、角色、菜单、权限、组织、资源和监控。
- `ruoyi-modules/ruoyi-workflow`：流程定义、任务、实例和业务审批。
- `ruoyi-modules/ruoyi-gen`：可编辑的代码生成器源码与模板。
- `ruoyi-modules/{ruoyi-demo,ruoyi-ai,ruoyi-job}`：对应业务能力。
- `ruoyi-extend`：独立部署的扩展应用。

依赖方向为部署应用到业务模块、公开 API 和所需 common 能力。common 不反向依赖业务模块，业务模块不深用其他模块的内部实现。

认证、权限、Client 和菜单属于跨端安全合同。前端可见性不是授权边界，最终鉴权必须由后端完成。

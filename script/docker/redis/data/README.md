# Redis 数据目录

Redis 持久化数据应放在 `NAMEWTA_DATA_ROOT/redis/data`。默认示例使用 `script/docker/runtime/redis/data`，生产环境应通过仓库外 env 文件把 `NAMEWTA_DATA_ROOT` 指向独立、可备份的数据盘。

首次启动前，应让运行 Redis 容器的用户对该目录具有读写权限。优先设置正确的 owner/group 和最小权限，不要使用 `chmod 777`。具体 UID/GID 以当前镜像和部署平台实际运行用户为准。

停止 Compose 时不要执行 `docker compose down -v`。升级镜像或调整配置前先备份该目录，并通过 `docker compose ... config --quiet` 检查解析后的挂载路径。

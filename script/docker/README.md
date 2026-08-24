# NAMEWTA Docker Compose

默认只启动 MySQL、Redis 和 MinIO。前端与后端相关容器分别受 `frontend`、`backend` profile 控制，不属于中间件初始化。

## 配置

真实运行参数通过仓库外的 env 文件提供，不要将密码提交到 Git。可从 `.env.example` 复制变量名，并为密码生成独立强随机值。

Compose 使用独立 `ruoyi-namewta-network` bridge 网络。所有宿主机端口都位于 4xxxx 范围：

| 服务 | 宿主机端口 | 容器端口 |
|---|---:|---:|
| MySQL | 43306 | 3306 |
| Redis | 46379 | 6379 |
| MinIO API / Console | 49000 / 49001 | 9000 / 9001 |
| Nginx HTTP / HTTPS | 40080 / 40443 | 80 / 443 |
| Server 1 HTTP / Job / AI | 48080 / 42080 / 43080 | 8080 / 28080 / 38080 |
| Server 2 HTTP / Job / AI | 48081 / 42081 / 43081 | 8081 / 28081 / 38081 |
| Monitor | 49090 | 9090 |
| SnailJob HTTP / RPC | 48800 / 47888 | 8800 / 17888 |
| SnailAI HTTP / gRPC | 48900 / 48888 | 8900 / 18888 |

## 中间件启动

先检查解析后的配置：

```bash
docker compose --env-file /path/to/namewta.env -f script/docker/docker-compose.yml config --quiet
```

仅启动中间件：

```bash
docker compose --env-file /path/to/namewta.env -f script/docker/docker-compose.yml up -d mysql redis minio
```

MySQL 初始化客户端固定使用 `utf8mb4`，并且只在数据目录为空时自动依次执行 `ry_vue.sql`、`ry_job.sql`、`ry_workflow.sql`、`ry_ai.sql`、`namewta/DDL.sql` 和 `namewta/DSL.sql`。已有数据目录不得通过重建容器重放这些非幂等脚本。

停止容器时不要使用 `down -v`，数据目录由 `NAMEWTA_DATA_ROOT` 指定并单独备份。

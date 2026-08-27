# 验证与交付

从仓库根目录执行：

```bash
./mvnw test
./mvnw clean package -DskipTests
./mvnw clean package -Pbundle-core -Dmaven.test.skip=true
```

先运行受影响模块或测试类取得快速反馈，再运行根级完整测试。默认全量与 core bundle 都必须从 clean 构建，core 跳过测试编译只允许建立在完整测试已通过的基础上。

交付前检查：

- 未修改 Maven Wrapper、BOM、无关 POM 或构建输出。
- `target`、`.flattened-pom.xml` 和外部服务数据未进入提交。
- SQL 文件名、Docker 初始化顺序、测试和文档一致。
- 实际命令、退出码、跳过的属性门控测试和环境限制均被准确记录。

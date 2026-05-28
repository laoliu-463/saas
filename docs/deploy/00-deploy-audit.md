# 部署现状审查

审查时间：2026-05-27

## 一、阶段性结论

当前部署入口已收口为两个环境：

| 环境 | 入口 | 用途 |
| --- | --- | --- |
| test | `docker-compose.test.yml`、`.env.test` | 测试 / Mock 回归 |
| real-pre | `docker-compose.real-pre.yml`、`.env.real-pre` | 真实上游联调与当前生产形态部署 |

旧 `dev`、`local-mock`、`real`、`prod` 运行入口已合并或删除；历史文件仅可作为归档证据，不再作为部署指引。

## 二、当前证据

- 后端默认 profile 已改为 `test`，真实上游 / 生产形态只允许 `real-pre`。
- real-pre 不允许与 test 混用，且必须关闭 `APP_TEST_ENABLED` 与 `DOUYIN_TEST_ENABLED`。
- real-pre CORS、JWT、DB、Redis、抖音凭据和关键业务开关均由环境变量注入并由 Guard/Jenkins 校验。
- test 使用 `Dockerfile.test`；real-pre 使用生产 Dockerfile / 版本化镜像口径。
- real-pre 迁移入口统一为 `scripts/run-real-pre-db-migrations.sh`，迁移后写入 `schema_migration_log(version, checksum, applied_at)`。

## 三、部署流程

real-pre Jenkins / 手工部署顺序：

```text
checkout
-> real-pre 环境守卫
-> backend test
-> frontend build
-> backend package
-> build backend-real-pre / frontend-real-pre images
-> up postgres-real-pre / redis-real-pre
-> scripts/run-real-pre-db-migrations.sh
-> up backend-real-pre / frontend-real-pre
-> /api/system/health 与 /login 端口验活
-> real-pre preflight
-> real-pre P0 E2E
-> 归档 Compose 状态、日志和 QA 报告
```

## 四、本地验证证据

- `docker compose --env-file .\.env.test.example -f .\docker-compose.test.yml config --quiet`：PASS
- `docker compose --env-file .\.env.real-pre.example -f .\docker-compose.real-pre.yml config --quiet`：PASS
- `bash -n scripts/run-real-pre-db-migrations.sh`：PASS
- `mvn clean "-Dtest=RealPreMigrationContractTest,RealProdEnvironmentGuardTest,RuntimeExposurePolicyTest,TalentEnrichModeGuardTest,SwaggerConfigTest,TestControllerSecurityTest,SystemEnvControllerTest,StartupEnvironmentLoggerTest" test`：45/45 PASS
- `npm --prefix frontend run build`：PASS
- `mvn package -DskipTests`：PASS
- `docker compose --env-file .\.env.real-pre.example -f .\docker-compose.real-pre.yml build backend-real-pre frontend-real-pre`：PASS

## 五、待补外部证据

- real-pre Jenkins 实际执行记录
- real-pre `schema_migration_log` 落库记录
- 真实证书挂载、HTTPS 跳转和公网域名 CORS 值

## 六、阶段性结论

代码和配置层面的环境入口已统一，本地配置、构建和定向回归通过。当前不能声明 real-pre 生产部署已经完成，因为还缺真实 Jenkins/服务器执行证据、迁移日志落库证据和真实域名/证书验证记录。

# DDD-BASE-001 Evidence Report

## 元信息

| 项 | 值 |
| --- | --- |
| 时间 | 2026-06-09 18:32 CST |
| 环境 | real-pre（本地 Docker） |
| 分支 | feature/auth-system |
| 基线 commit | a2b55e14（本补充提交前） |
| 任务 | DDD-BASE-001 补充 order-sync / analytics.shadow + ddd-refactor-plan.md |

## 变更摘要

- `DddRefactorProperties` 新增 `orderSync`、`analytics.shadow` 嵌套配置，默认 false。
- `application.yml` / `application-test.yml` / `application-real-pre.yml` 同步追加键。
- `DddRefactorPropertiesTest` 覆盖新增默认值断言。
- 新增 `harness/reports/ddd-refactor-plan.md`（重构原则、开关、回滚、验收红线）。

**未变更**：任何 Controller / Service / Mapper / API 契约 / 数据库表。

## 构建与测试

| 检查项 | 结果 | 说明 |
| --- | --- | --- |
| `mvn compile` | PASS | BUILD SUCCESS |
| `mvn -Dtest=DddRefactorPropertiesTest test` | PASS | 2 tests, 0 failures |
| `mvn test`（全量） | PARTIAL | 42 errors，根因为 Testcontainers Postgres 启动失败、`ColonelOrderSettlementMapper.xml` 解析及局部 NoClassDefFound；与本次配置追加无关 |
| 前端构建 | 跳过 | 本任务未改前端 |

## Docker / 健康检查

| 服务 | 状态 |
| --- | --- |
| saas-active-backend-real-pre-1 | healthy |
| saas-active-frontend-real-pre-1 | healthy |
| saas-active-postgres-real-pre-1 | healthy |
| saas-active-redis-real-pre-1 | healthy |
| `GET http://127.0.0.1:8081/api/system/health` | `{"status":"UP"}` |

**未重启容器**：本次仅追加配置默认值与文档，运行中 backend 未加载新 YAML；下次镜像/容器重建后生效。线上行为不变（开关默认 false）。

## 业务 smoke

- 健康检查通过；未改业务路径，smoke 行为与改动前一致。

## 部署远端

否。

## 结论

**PARTIAL**

- 任务目标（安全开关齐备 + 计划文档 + 默认值测试）已达成。
- 全量测试受本地 Testcontainers 环境阻塞，需单独修复后重跑；不阻塞 DDD-BASE-001 开关基线合并。

## 剩余风险

1. 全量 `mvn test` 在 CI/本地需确认 Testcontainers 与 mapper XML 环境稳定。
2. 新增开关尚未被业务代码读取；后续任务接入时必须「总闸 + 子开关」双校验。

## 下一步

执行 **DDD-BASE-002**（Characterization Tests）。

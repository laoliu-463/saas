# Infra Agent — 基础设施 / 开关 / 配置

## 角色定位

基础设施的**唯一所有者**。负责：
- 13 个重构开关（`ddd.refactor.*`）的 `DddRefactorProperties.java` 维护
- `application*.yml`、Docker Compose
- 数据库 migration（Flyway-style）
- Outbox / processed_events 基础设施
- 环境隔离（`test` / `real-pre`）

**不负责**：
- 业务代码
- 业务域 Facade

## 必读入口

1. `harness/COMPLETION_GATES.md`
2. `harness/reports/ddd-base-001-refactor-switches.md`（13 开关表）
3. `harness/reports/ddd-base-004-package-structure.md`（包骨架）
4. `harness/FORBIDDEN_SCOPE.md`（特别是"real-pre 禁止"段）
5. `backend/src/main/java/com/colonel/saas/config/DddRefactorProperties.java`
6. `backend/src/main/resources/application*.yml`

## Allowed Paths

- `backend/src/main/java/com/colonel/saas/config/DddRefactorProperties.java`（**Infra 独占**）
- `backend/src/main/resources/application*.yml`（**Infra 独占**）
- `backend/src/main/resources/db/migration/**`（migration 独占）
- `docker-compose*.yml`、`Dockerfile*`
- `harness/reports/ddd-base-*`（基础设施报告）
- `harness/reports/ddd-infra-*.md`
- `harness/handovers/ddd-infra-*.md`
- `harness/agent-locks/DDD-INFRA-*-<agent>.lock.md`、`DDD-BASE-*-infra-agent.lock.md`

## Forbidden Paths

- 业务域实现（`domain/**`）
- 业务 Facade / Service / Controller
- `cross-domain-mapper-legacy-whitelist.txt`（Architecture Guard 审批后由业务 Agent 改）
- `.env*`、`*.pem`、`*.key`（**永远不提交**）

## 交付物

1. 开关 / 配置 / migration 变更
2. 报告 + handover + lock + commit
3. **DDD-BASE-001 / 003 / 004 已 DONE**（参考既有报告）

## 启动提示词格式

```text
我是 Infra Agent。task_id: DDD-INFRA-XXX
branch: feature/ddd/DDD-INFRA-XXX-infra-agent
请执行：
1. 读 `harness/agent-locks/LOCK_INDEX.md` 确认无冲突
2. 建 lock：`harness/agent-locks/DDD-INFRA-XXX-infra-agent.lock.md`
3. 读 `harness/reports/ddd-base-001-refactor-switches.md`
4. 拉 `feature/auth-system` 起点；开关默认 `false`；不破坏 application*.yml 现有契约
5. 跑 `mvn test` + `docker compose config`（如改 compose）
6. 写报告 + handover；commit
7. 不 push；不合并

完成后输出：commit hash + 测试统计 + 报告路径 + handover 路径。
```

## 红线

- 禁止在 real-pre 启用 `APP_TEST_ENABLED=true` / `DOUYIN_TEST_ENABLED=true`。
- 禁止 `docker compose down -v` / 删除 PostgreSQL / Redis volume。
- 禁止破坏性数据库操作（drop / rename / 清表 / 删字段）。
- 禁止引入外部 MQ。
- 禁止默认开启异步 dispatcher（开关必须为 `false`）。
- 禁止提交 `.env*` / `*.pem` / `*.key`。

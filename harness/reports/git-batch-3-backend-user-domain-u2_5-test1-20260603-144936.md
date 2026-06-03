# GIT-BATCH-3：backend-user-domain-u2_5-test1 提交与部署报告

## 1. 任务概述

| 项 | 内容 |
| --- | --- |
| Batch | GIT-BATCH-3 |
| 名称 | 提交并部署 backend-user-domain-u2_5-test1 批次 |
| 执行时间 | 2026-06-03 14:49:36 |
| 本地仓库 | `D:\Projects\SAAS` |
| 分支 | `feature/auth-system` |
| 提交前 HEAD | `5fe6ba23 feat(product-ui): product card hover expand and library load-more pagination` |
| Batch 3 commit | `c470dc29cf8e6e07fbf002623b189d30cc0d8f36` |
| 是否修改代码 | 否。本任务只提交已 staged 的 14 个文件并部署 backend |
| 是否提交 harness / 报告 | 否 |
| 是否执行数据库写操作命令 | 否 |
| 是否执行 migration 命令 | 否 |
| 是否部署 frontend | 否 |

## 2. staged 文件清单

提交前 `git diff --cached --name-only` 为以下 14 个文件：

```text
backend/src/main/java/com/colonel/saas/constant/DeptType.java
backend/src/main/java/com/colonel/saas/constant/DeptTypes.java
backend/src/main/java/com/colonel/saas/service/SysDeptService.java
backend/src/main/resources/db/alter-sys-dept-uuid-canonical-20260530.sql
backend/src/main/resources/db/alter-sys-dept.sql
backend/src/main/resources/db/init-db.sql
backend/src/main/resources/db/migrate-sys-dept-dept-type.sql
backend/src/test/java/com/colonel/saas/auth/service/SysUserServiceTest.java
backend/src/test/java/com/colonel/saas/constant/DeptTypeTest.java
backend/src/test/java/com/colonel/saas/controller/CommissionRuleControllerTest.java
backend/src/test/java/com/colonel/saas/controller/SysDeptControllerTest.java
backend/src/test/java/com/colonel/saas/controller/SysUserControllerTest.java
backend/src/test/java/com/colonel/saas/service/CommissionRuleServiceTest.java
backend/src/test/java/com/colonel/saas/service/SysDeptServiceTest.java
```

提交后 `git show --name-only --format=%H HEAD` 确认 commit 只包含上述 14 个文件。

## 3. staged 范围审查结论

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| staged 精确等于指定 14 文件 | PASS | `actual_count=14, missing_count=0, extra_count=0` |
| 不包含 `application-real-pre.yml` | PASS | cached diff 无该文件 |
| 不包含 frontend | PASS | 禁止项过滤无命中 |
| 不包含 harness | PASS | 禁止项过滤无命中 |
| 不包含 Docker / compose | PASS | 禁止项过滤无命中 |
| 不包含商品同步文件 | PASS | 禁止项过滤无 `ProductActivitySync` / `ProductDisplayRule` |
| `git diff --cached --check` | PASS | 无输出 |

`backend/src/main/resources/application-real-pre.yml` 保持 unstaged，未进入 commit。

## 4. 提交前验证结果

| 命令 | 结果 |
| --- | --- |
| `mvn -f backend/pom.xml "-Dtest=DeptTypeTest,SysDeptServiceTest,SysDeptControllerTest,DataScopeAspectTest" test` | PASS：16 tests / 0 failures / 0 errors |
| `mvn -f backend/pom.xml "-Dtest=SysUserServiceTest,SysUserControllerTest,CommissionRuleServiceTest,CommissionRuleControllerTest" test` | PASS：53 tests / 0 failures / 0 errors |
| `mvn -f backend/pom.xml test` | PASS：1675 tests / 0 failures / 0 errors |
| `mvn -f backend/pom.xml -DskipTests package` | PASS：BUILD SUCCESS |
| `powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\safety-check.ps1 -Env real-pre -Scope backend -DryRun` | PASS：Safety check passed |

补充：B3-VERIFY-001 证据路径仍为 `harness/reports/evidence-20260603-142506.md`。

## 5. commit hash

```text
c470dc29cf8e6e07fbf002623b189d30cc0d8f36
```

提交命令：

```powershell
git commit -m "fix(user): unify dept type constants and stabilize tests"
```

结果：

```text
[feature/auth-system c470dc29] fix(user): unify dept type constants and stabilize tests
14 files changed, 156 insertions(+), 80 deletions(-)
```

## 6. 推送结果

| Remote | Branch | 结果 |
| --- | --- | --- |
| `gitee` | `feature/auth-system` | PASS：`5fe6ba23..c470dc29` |
| `origin` | `feature/auth-system` | PASS：`5fe6ba23..c470dc29` |

## 7. 远端部署过程

远端参数：

- SSH alias：`saas`
- 远端目录：`/opt/saas/app`
- Compose 文件：`docker-compose.real-pre.yml`
- Env file：`/opt/saas/env/.env.real-pre`
- backend service：`backend-real-pre`

远端只读检查：

- 远端分支：`feature/auth-system`
- 远端工作区：clean
- Compose services：`postgres-real-pre`、`redis-real-pre`、`backend-real-pre`、`frontend-real-pre`

远端拉取：

```bash
cd /opt/saas/app
git fetch gitee feature/auth-system
git checkout feature/auth-system
git pull --ff-only gitee feature/auth-system
git rev-parse HEAD
```

结果：

```text
Fast-forward 5fe6ba2..c470dc2
c470dc29cf8e6e07fbf002623b189d30cc0d8f36
```

部署说明：

- 远端没有 `mvn`，直接远端打包失败：`bash: line 1: mvn: command not found`。
- 为避免把本地未提交的 `application-real-pre.yml` P-FIX-002 配置混入 jar，未使用当前工作区 jar。
- 使用 `git archive HEAD backend` 导出 commit `c470dc29` 的干净 backend 快照，在临时目录中执行 `mvn -f backend/pom.xml -DskipTests clean package`。
- 干净快照中的 `application-real-pre.yml` 未命中 `product/activity/sync`。
- 将干净快照构建的 `backend/target/colonel-saas.jar` 上传到远端 `/opt/saas/app/backend/target/colonel-saas.jar`。

远端 jar 上传后：

```text
remote_after_upload backend/target/colonel-saas.jar 79927408 2026-06-03 14:46:45.368678790 +0800
```

远端 build：

```bash
docker compose --env-file /opt/saas/env/.env.real-pre -f docker-compose.real-pre.yml build backend-real-pre
```

结果：

- `COPY target/*.jar app.jar` 未走缓存。
- image `colonel-saas/backend:real-pre` build success。

远端 up：

```bash
docker compose --env-file /opt/saas/env/.env.real-pre -f docker-compose.real-pre.yml up -d backend-real-pre
```

结果：

- `backend-real-pre` recreated。
- `postgres-real-pre` / `redis-real-pre` 仅作为依赖等待并保持 running / healthy。
- 未指定 `frontend-real-pre`，frontend 未 recreate。

## 8. 远端最终 commit

```text
c470dc29cf8e6e07fbf002623b189d30cc0d8f36
```

远端 `git status --short`：无输出，工作区 clean。

## 9. backend health

远端 compose 状态：

```text
backend-real-pre: Up About a minute (healthy)
frontend-real-pre: Up 40 minutes (healthy)
postgres-real-pre: Up 20 hours (healthy)
redis-real-pre: Up 2 days (healthy)
```

远端 health：

```text
GET http://127.0.0.1:8081/api/system/health
{"status":"UP"}
```

frontend healthz 补充检查：

```text
GET http://127.0.0.1:3001/healthz
ok
```

容器内 app.jar：

```text
container_app_jar /app/app.jar 79927408 2026-06-03 06:46:45.000000000 +0000
```

real-pre 关键环境：

```text
APP_TEST_ENABLED=false
DOUYIN_TEST_ENABLED=false
DOUYIN_REAL_UPSTREAM_MODE=live
```

## 10. 是否执行数据库写操作

否：本任务未执行 `psql`、`docker exec ... psql`、SQL 文件、DDL、DML、repair、backfill 或任何手工数据库写操作命令。

说明：backend 启动日志存在项目既有 schema bootstrap 信息：

```text
TalentProfileSchemaBootstrap     : Talent profile sync schema ensured
OrderSyncDedupSchemaBootstrap    : Order sync dedup claim schema ensured
DouyinWebhookSchemaBootstrap     : Douyin webhook inbox schema ensured
OrderPaymentSchemaBootstrap      : Order payment schema ensured
```

这些是应用启动时既有 bootstrap 日志，不是本轮手工执行 migration 或数据库脚本。

## 11. 是否执行 migration

否。

- 未执行 `migrate-sys-dept-dept-type.sql`。
- 未执行 `migrate-all.sql`。
- 未执行 Flyway / Liquibase 命令。
- 未执行任何数据库迁移脚本命令。

日志 grep 结果未出现 Flyway / Liquibase 迁移执行记录。

## 12. 是否重启 frontend

否。

部署前 frontend container ID：

```text
a0f5e886f827f1423bdcd210d9b863c6bdff479201db04636dbf92663fbb5508
```

部署后 frontend container ID：

```text
a0f5e886f827f1423bdcd210d9b863c6bdff479201db04636dbf92663fbb5508
```

ID 一致，frontend 未 recreate。healthz 返回 `ok`。

## 13. 是否仍有 dirty 文件

是，本地仍有 dirty / untracked 文件，但不属于 Batch 3 commit：

```text
 M backend/src/main/resources/application-real-pre.yml
 M harness/CURRENT_STATE.md
 M harness/HARNESS_CHANGELOG.md
 M harness/state/DOMAIN_STATUS.md
?? harness/reports/b3-scope-001-batch3-scope-isolation-20260603-143131.md
?? harness/reports/content-retire-20260603-103207.md
?? harness/reports/content-retire-20260603-111343.md
?? harness/reports/content-retire-20260603-113617.md
?? harness/reports/evidence-20260603-101503.md
?? harness/reports/evidence-20260603-104232.md
?? harness/reports/evidence-20260603-104601.md
?? harness/reports/evidence-20260603-111733.md
?? harness/reports/evidence-20260603-113632.md
?? harness/reports/evidence-20260603-122021.md
?? harness/reports/evidence-20260603-142506.md
?? harness/reports/func-001-product-card-hover-ui-20260603-111451.md
?? harness/reports/git-batch-2-frontend-product-ui-20260603-140800.md
?? harness/reports/p-fix-001c-product-library-pagination-20260603-112740.md
?? harness/reports/p-fix-001c-product-library-pagination-20260603-113616.md
?? harness/reports/retro-20260603-104247.md
?? harness/reports/retro-20260603-104601.md
?? harness/reports/retro-20260603-111824.md
?? harness/reports/retro-20260603-113645.md
?? harness/reports/retro-20260603-122513.md
?? harness/reports/test-1-full-backend-failures-fix-20260603-104601.md
?? harness/reports/user-domain-u2_5b-dept-type-minimal-fix-20260603-101503.md
```

本报告自身也保持 untracked，未混入 Batch 3 commit。

## 14. 风险残留

- 远端缺少 Maven，backend jar 需要由本地干净 commit 快照构建后上传；建议后续部署系统补齐远端构建工具或改为多阶段 Docker build / CI artifact。
- `application-real-pre.yml` 的 P-FIX-002 配置仍在本地 unstaged，需要后续独立状态收口。
- harness 状态 / 报告仍未提交，需要后续 Batch 4 / docs 状态收口处理。
- real-pre 历史 `sys_dept.dept_type` 如需写库修复，必须另起 DB repair 任务，执行备份、dry-run、审批和回滚方案。
- 用户域后续 `DataScopeResolver`、`PermissionContext`、`UserDomainFacade` 仍未统一，按 U-3 / U-4 / U-5 后续推进。

## 15. 下一步建议

1. 执行 Batch 4：提交本次 evidence / reports / retro / harness 状态，但不得混入业务代码。
2. 单独处理 P-FIX-002 配置残留：确认 `application-real-pre.yml` 是否应进入 P-FIX-002 状态收口 commit。
3. 如需要修复 real-pre 历史 `sys_dept.dept_type` 数据，另起 DB repair 任务，不并入本批。
4. 进入用户域 U-3：CurrentUser / PermissionContext 统一。

## 16. 结论

GIT-BATCH-3 已完成：

- 14 个 Batch 3 后端文件已提交。
- commit 已推送到 `gitee` 和 `origin` 的 `feature/auth-system`。
- 远端 `/opt/saas/app` 已 fast-forward 到本次 commit。
- backend-real-pre 已使用 commit 干净快照构建的 jar 重新构建并重启。
- backend health PASS。
- frontend 未重启。
- 未执行数据库写操作命令。
- 未执行 migration 命令。

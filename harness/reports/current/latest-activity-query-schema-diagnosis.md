# Evidence Report

## Metadata

- Time: 2026-07-18 00:42:00 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/fix-role-aware-schema-20260718
- Commit: 2092dfa45411211257597e6a79aeb750080ef440
- Owned worktree: clean; main worktree remains dirty with pre-existing unrelated files.
- Full workspace status: main worktree dirty; no unrelated files were staged or modified by this task.
- Deploy remote: true

## Owned Files

~~~text
harness/reports/current/latest-activity-query-schema-diagnosis.md
~~~

## Owned Git Status

~~~text
clean worktree: clean
main worktree: dirty with unrelated pre-existing changes
push: Everything up-to-date (gitee HEAD -> feature/auth-system)
~~~

## Build Result

~~~text
local Maven: BUILD SUCCESS (`mvn -f backend/pom.xml -DskipTests package`)
local Docker image: built `colonel-saas/backend:real-pre`
remote Docker image: built during fixed deploy script
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    48 minutes ago   Up 47 minutes (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   48 minutes ago   Up 47 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   2 hours ago      Up 2 hours (healthy)      5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      2 days ago       Up 2 days (healthy)       6379/tcp
NAMES                             STATUS                    PORTS
saas-active-frontend-real-pre-1   Up 48 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 48 minutes (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 2 hours (healthy)      5432/tcp
saas-active-redis-real-pre-1      Up 2 days (healthy)       6379/tcp
campus_frontend                   Up 3 days                 0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 3 days (healthy)       0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 3 days (healthy)       0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 3 days (unhealthy)     0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
~~~

## Health Check Result

~~~text
本地 real-pre backend/postgres/redis/frontend healthy；本地 `/api/system/health`=`{"status":"UP"}`。
远端 real-pre backend/postgres/redis/frontend healthy；远端 `/api/system/health`=`{"status":"UP"}`。
远端部署脚本客户端等待 125 秒超时，但部署后检查确认容器、提交号、镜像和字段均已更新/通过。
~~~

## Business Validation Result

~~~text
本地 PASS：`biz_staff` 登录成功，活动列表返回 1 条；活动 `3929905` 商品查询 HTTP 200，返回 1 条商品。
远端 Schema/health PASS：四个字段均存在，健康接口 UP，部署后原始缺列错误计数为 0。
远端角色 API：BLOCKED_AUTH；`biz_staff` 账号存在且启用，但当前远端环境中可读取的配置候选凭据不能登录，未重置密码或暴力尝试。
~~~

## Diagnosis Evidence

### 现象

- 招商专员测试账号查询活动商品失败。
- 应用返回 PostgreSQL `column "channel_attribution_source" does not exist`。
- 失败 SQL 来自 `colonelsettlement_order`，条件包含 `colonel_activity_id = ?` 与 `product_id IN (...)`；因此错误发生在数据库解析阶段，早于账号数据范围结果判断。

### 本地证据

- `docker compose --env-file .env.real-pre -f docker-compose.real-pre.yml ps`：real-pre 四服务均 healthy。
- 本地已幂等执行 `alter-role-aware-promotion-link-attribution-20260716.sql`，四个字段探针全部返回：`promotion_link.attribution_owner_type`、`pick_source_mapping.attribution_owner_type`、`colonelsettlement_order.channel_attribution_source`、`colonelsettlement_order.recruiter_attribution_source`。
- 本地使用干净 worktree 的目标提交构建 backend 镜像并重启；四服务 healthy。
- 本地 `biz_staff` 只读登录、活动列表和活动商品 GET 已完成，未执行分配、同步或其他业务写操作。

### 远端证据

- SSH `saas` 只读检查：远端分支为 `feature/auth-system`，commit 为 `2092dfa4`；后端、前端、PostgreSQL、Redis 均 healthy，后端 health 为 `{"status":"UP"}`，前端 `/healthz` 为 `ok`。
- 远端环境开关为 `APP_TEST_ENABLED=false`、`DOUYIN_TEST_ENABLED=false`、`DOUYIN_REAL_UPSTREAM_MODE=live`。
- 远端 fixed deploy 已执行；远端提交为 `2092dfa45411211257597e6a79aeb750080ef440`，工作树干净，gitee 推送结果为 `Everything up-to-date`。
- 远端执行角色归因迁移后，四个字段探针全部返回；backend/frontend/postgres/redis 均 healthy，backend image 在本次部署时间生成。
- 远端数据库中 `biz_staff|招商专员测试|1|0`，账号存在且启用；因未获得可用凭据，远端角色业务接口未执行。
- 远端部署后近 20 分钟日志中本次缺失列错误计数为 0；同时存在 32 条 `No hstore extension installed`，这是独立的订单事件/性能处理问题，未将其混入本次缺列结论。

### 代码与迁移链证据

- `f096d729` 为 role-aware 归属改动：实体增加 `channel_attribution_source` / `recruiter_attribution_source`，同时新增 `alter-role-aware-promotion-link-attribution-20260716.sql`。
- 该迁移还负责 `promotion_link.attribution_owner_type` 与 `pick_source_mapping.attribution_owner_type`；本地日志已对后者给出缺列错误。
- 当前 `alter-cso-dual-attribution-status-20260716.sql` 只增加两个 status 字段，不包含两个 source 字段，不能单独修复本次错误。
- 代码图谱已完成最小上下文、语义检索、调用关系和影响半径检查；相关订单实体/Mapper/迁移链影响半径被评为 high，不能按单一前端权限问题处理。

## Hypotheses and Result

1. 高可信：数据库未执行 role-aware 归属增量迁移。两边 Schema 探针和本地日志均支持，已成立为当前最可信根因。
2. 中可信：部署流程依赖首次初始化脚本，复用已有 PostgreSQL volume 后造成应用与数据库契约漂移。Compose 挂载方式和远端容器存活时间支持该推论。
3. 低可信：招商专员 `self` 数据范围或角色权限错误。当前不能解释“列不存在”，必须在 Schema 修复后通过同一 API 对 admin / biz_leader / biz_staff 做权限对比。

## Actions Executed

- 本地先执行幂等迁移、构建、重启、健康检查，并用招商专员账号完成活动商品查询复验。
- 远端执行既有 `deploy-remote.ps1 -Env real-pre`，校验提交、应用迁移、构建并重启 real-pre 服务。
- 远端做字段探针、健康检查、缺列错误统计和数据库账号存在性检查。
- 本地和远端均未使用 `docker compose down -v`，未删除 PostgreSQL/Redis volume；本地服务容器重建期间保留了原 volume。

## Content Maintenance Result

~~~text
本轮没有新增源码差异；仅更新本证据报告，部署使用远端已有提交中的迁移和门禁脚本。
~~~

## Remote Deploy Result

~~~text
已部署：固定远端部署脚本客户端等待超时，但远端 post-check 证明部署已完成；四字段、四服务健康和提交号检查通过。推送：`Everything up-to-date`，无新增 commit 可推送。
~~~

## Retro Summary

Retro：本次验证证明仅重建应用不能修复复用 volume 的增量 Schema 漂移；已有部署脚本中的迁移和四字段门禁有效。后续仍应把“远端角色凭据可用性”作为部署后 API smoke 的显式前置检查，并单独治理 `No hstore extension installed`。

## Conclusion

PARTIAL

## Residual Risk

- `biz_staff` 远端角色业务查询仍为 `BLOCKED_AUTH`，需要用户提供/配置有效测试凭据后补做远端 GET 和权限范围验证。
- 远端日志存在独立的 `No hstore extension installed`，可能影响订单事件重放/业绩计算，未在本任务中修复。
- `check-harness-limits.ps1 -BaselineRef HEAD` 仍受主工作树大量既有 unrelated modified/untracked 文件影响；本任务未清理、重置或提交这些文件。
- `saas-test-backend-1` 在本地 Docker inventory 中 unhealthy，但不属于 real-pre 四服务范围，未在本任务中修改。

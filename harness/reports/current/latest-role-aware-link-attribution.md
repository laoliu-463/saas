# Evidence Report

## Metadata

- Time: 2026-07-16 17:22:13 +08:00
- Environment: real-pre
- Scope: full
- Branch: feature/auth-system
- Commit: f03bb0da
- Owned worktree: clean
- Deploy remote: true

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/controller/AttributionAdminController.java
backend/src/main/java/com/colonel/saas/service/AttributionOwnerReconciliationService.java
backend/src/test/java/com/colonel/saas/controller/AttributionAdminControllerTest.java
backend/src/test/java/com/colonel/saas/service/AttributionOwnerReconciliationServiceTest.java
harness/reports/current/latest-role-aware-link-attribution.md
~~~

## Owned Git Status

~~~text
(clean)
~~~

## Build Result

~~~text
not collected
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
Frontend build: PASS (npm --prefix frontend ci; npm --prefix frontend run build)
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                        PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    2 minutes ago    Up 2 minutes (healthy)        127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   2 minutes ago    Up About a minute (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   25 minutes ago   Up 25 minutes (healthy)       5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      26 hours ago     Up 26 hours (healthy)         6379/tcp
NAMES                             STATUS                        PORTS
saas-active-frontend-real-pre-1   Up About a minute (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 2 minutes (healthy)        127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 25 minutes (healthy)       5432/tcp
saas-active-redis-real-pre-1      Up 26 hours (healthy)         6379/tcp
campus_frontend                   Up 2 days                     0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 2 days (healthy)           0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 2 days (healthy)           0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 2 days (unhealthy)         0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
~~~

## Health Check Result

~~~text
Local health verification: PASS
~~~

## Business Validation Result

~~~text
Business validation: PASS (npm run e2e:real-pre:p0:preflight)
~~~

## 订单 6927995582750227729 定向修复核验（2026-07-16）

- 变更前已记录：壮云（`1c34b680-30b2-41ec-bdc7-2dde1f37e786`）有效角色为 `channel_staff`；本轮后远端仅为 `biz_staff`。
- 远端审计日志（UTC）已记录角色替换：`2026-07-16 09:22:42`，目标为该用户 ID。
- 仅目标推广链接 `1df7d10a-50cc-4306-b773-81b71513bb00` 与映射 `e16ba019-b05e-4e24-a832-526ea8452923` 已分类为 `RECRUITER`；审计日志记录了 dry-run（09:26:07 UTC）和 apply（09:26:36 UTC），均为 `scanned=1`。
- 同一账号另一条映射 `b2846567-0119-422d-8f2c-8d96faacd4ed` 仍为 `NULL`，未被本轮修改。
- 当前订单事实已显示招商归属壮云，来源为 `native_unique_link_owner`；但 `performance_records` 仍有 1 条最终招商归属“招商组长测试”（`1f17391b-67fe-40aa-a336-0f41faafe15b`），壮云为 0 条。
- 审计日志未找到该订单的 `replay-attribution` 成功记录，因此不能将订单事实变更视为已完成受审计的单笔重放，也不能宣称壮云已可查询该笔招商业绩。
- 远端 `ADMIN_PASSWORD` 登录被 401 拒绝，说明部署环境变量与当前管理员密码已漂移；为保持审计边界，未绕过鉴权或直接改写订单/业绩表。

### 后续受阻步骤

提供当前可用的远端管理员凭据后，按以下固定范围继续：先以 `dryRun=true` 调用 `/api/orders/replay-attribution`，仅传 `orderIds=["6927995582750227729"]` 且 `limit=1`；预演通过后以相同单号 apply。该服务会在同一事务内持久化订单并 `upsertFromOrder`，从而将该订单的 `performance_records` 招商归属同步到壮云。随后复核金额、渠道维度和原组长查询结果。

## Content Maintenance Result

~~~text
Content maintenance skipped by -ContentMaintenance off.
~~~

## Remote Deploy Result

~~~text
Remote deploy: PASS
~~~

## Retro Summary

No actionable Harness improvement was recorded; no standalone retro is required.

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
- 订单业绩记录尚未完成受审计的单笔重放；在提供有效远端管理员凭据前，不得声明该订单的招商业绩修复完成。

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
- 远端凭据确认后，单条 dry-run 审计（09:36:58 UTC）为 `scanned=1`、`updated=0`、`safeToUpdate=1`；单条 apply 审计（09:37:16 UTC）为 `scanned=1`、`updated=1`。请求始终只含订单 `6927995582750227729` 且 `limit=1`。
- `performance_records` 现仅有 1 条该订单记录：默认/最终招商人均为壮云，招商归属为 `native_unique_link_owner`；“招商组长测试”作为最终招商人的行数为 0。`biz_staff` 的访问策略按 `final_recruiter_user_id` 过滤，因此壮云可查询该笔招商业绩。
- 订单事实与业绩金额保持一致：订单金额/实付金额/结算金额/预估服务费/实际服务费为 `990/990/0/20/0`；活动 `3916506`、商品 `3829804874841849888`、百应 ID `7351155267604218149` 未变化；渠道用户与渠道部门仍为 `NULL`、渠道归属仍为 `unattributed`。
- 远端健康检查：`PASS`。

## Content Maintenance Result

~~~text
Content maintenance skipped by -ContentMaintenance off.
~~~

## Harness Governance Result

~~~text
TASK_GATE=FAIL；原因仅为任务开始前已存在且不属于本任务的未跟踪文件：
harness/reports/evidence-20260713-131800.md
hs_err_pid68956.log
hs_err_pid84964.log
未删除、未提交这些文件。REPOSITORY_HEALTH=PARTIAL。
~~~

## Remote Deploy Result

~~~text
Remote deploy: PASS
~~~

## Retro Summary

- 可执行改进：订单归因重放接口将 `reason` 写入长度为 50 的审计目标字段，超过长度会使审计写入失败并返回 500。后续应以订单 ID 作为审计目标、将原因保留在内容字段，并增加超过 50 字符 reason 的接口测试；本轮使用短审计原因完成了受审计的单条修复。

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
- 未以壮云交互会话运行页面 E2E；可查询性由 `biz_staff` 数据范围策略与远端最终招商归属记录共同核验。
- 长审计原因导致 500 的接口健壮性问题未在本轮扩展修复。
- 业务数据修复已通过；本报告的 `PARTIAL` 仅反映上述未归属文件导致的 Harness 门禁失败。

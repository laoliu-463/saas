# Evidence Report

## Metadata

- Time: 2026-07-17 15:11:25 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/ddd-user-role-application
- Local commit: 8722da05
- Remote deploy branch commit: 0f5f5a3a
- Owned worktree: dirty (pre-existing unrelated changes preserved)
- Deploy remote: true

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/controller/TalentController.java
backend/src/main/java/com/colonel/saas/service/TalentQueryService.java
backend/src/main/java/com/colonel/saas/service/TalentService.java
backend/src/test/java/com/colonel/saas/controller/TalentControllerTest.java
backend/src/test/java/com/colonel/saas/service/TalentQueryServiceTest.java
frontend/src/router/index.test.ts
frontend/src/router/index.ts
frontend/src/router/menuTree.test.ts
frontend/src/router/menuTree.ts
frontend/src/views/dashboard/index.vue
frontend/src/views/data/index.vue
frontend/src/views/talent/components/TalentDetailModal.vue
frontend/src/views/talent/constants.test.ts
frontend/src/views/talent/constants.ts
frontend/src/views/talent/index.test.ts
frontend/src/views/talent/index.vue
harness/reports/current/latest-content-retire.md
runtime/qa/full-browser-e2e.cjs
tests/e2e/11-real-pre-role-business-flow.spec.ts
~~~

## Owned Git Status

~~~text
Task-owned changes committed as 8722da05 and pushed to origin/codex/ddd-user-role-application.
Unrelated workspace changes were not staged or modified.
~~~

## Build Result

~~~text
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
Frontend build: PASS (npm --prefix frontend run build)
Targeted backend tests: PASS (TalentControllerTest, TalentQueryServiceTest)
Targeted frontend tests: PASS (4 files, 36 tests)
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    41 seconds ago   Up 36 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   39 seconds ago   Up 13 seconds (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   52 minutes ago   Up 52 minutes (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      47 hours ago     Up 47 hours (healthy)     6379/tcp
NAMES                             STATUS                    PORTS
saas-active-frontend-real-pre-1   Up 14 seconds (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 37 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 52 minutes (healthy)   5432/tcp
saas-active-redis-real-pre-1      Up 47 hours (healthy)     6379/tcp
campus_frontend                   Up 2 days                 0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 2 days (healthy)       0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 2 days (healthy)       0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 2 days (unhealthy)     0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
~~~

## Health Check Result

~~~text
Local health verification: PASS
Remote health verification: PASS (/api/system/health=200; backend/frontend containers healthy)
~~~

## Business Validation Result

~~~text
Local business preflight: PASS
Remote biz_staff API regression: PASS
- Login=200, dataScope=1
- GET /api/talents?view=MY_TALENTS=200
- POST /api/talents=200; owner claim visible in MY_TALENTS; cleanup=200
- createTime metrics=200; settleTime metrics=200
- GET /api/data/orders/detail=200, total=0 for the QA account
- GET /api/orders/exports/detail=403 (role guard; expected for biz_staff)
~~~

## Content Maintenance Result

~~~text
Content maintenance: Plan. Manifest=. DryRun=False.
~~~

## Remote Deploy Result

~~~text
PASS
- Remote branch fast-forwarded from fde84e32 to 0f5f5a3a.
- Remote real-pre rebuilt backend/frontend and passed jar guard and health checks.
- Remote regression artifact: runtime/qa/out/remote-biz-staff-regression/result.json
~~~

## Retro Summary

Remote deployment initially rebuilt the old remote branch because the local development branch and the deployment branch had diverged. The branch alignment was corrected by a fast-forward push before the final deployment. Future deploy preflight should compare the intended commit with the remote checkout before building.

## Conclusion

PASS (implemented talent/performance fixes deployed and remotely verified)

## Residual Risk

- 玄同/壮云的订单明细仍为空：该账号 personal scope 下订单查询使用 colonelsettlement_order.user_id，而其现有招商归属在 colonel_user_id / performance_records.final_recruiter_user_id；本轮未擅自改订单数据范围规则。
- 订单明细导出接口当前明确排除 biz_staff，若业务要求招商专员导出，需要单独确认并修复权限规则。

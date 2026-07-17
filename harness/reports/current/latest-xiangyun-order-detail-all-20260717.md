# Evidence Report

## Metadata

- Time: 2026-07-17 15:40:00 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/ddd-user-role-application
- Commit: 9666fa42
- Owned source changes committed; workspace contains unrelated pre-existing changes
- Deploy remote: true
- Remote deployment branch commit: f2a3e45c

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/controller/DataController.java
backend/src/main/java/com/colonel/saas/service/data/DataApplicationService.java
backend/src/test/java/com/colonel/saas/controller/DataControllerTest.java
harness/reports/current/latest-content-retire.md
harness/reports/current/latest-xiangyun-order-detail-all-20260717.md
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
Targeted tests: PASS (`mvn -q -Dtest=DataControllerTest,OrderDetailQueryApplicationServiceTest -Djacoco.skip=true test`)
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED              STATUS                        PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    About a minute ago   Up About a minute (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   About a minute ago   Up 45 seconds (healthy)       127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   2 hours ago          Up 2 hours (healthy)          5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      2 days ago           Up 2 days (healthy)           6379/tcp
NAMES                             STATUS                        PORTS
saas-active-frontend-real-pre-1   Up 45 seconds (healthy)       127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up About a minute (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 2 hours (healthy)          5432/tcp
saas-active-redis-real-pre-1      Up 2 days (healthy)           6379/tcp
campus_frontend                   Up 3 days                     0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 3 days (healthy)           0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 3 days (healthy)           0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 2 days (unhealthy)         0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
~~~

## Health Check Result

~~~text
Local health verification: PASS (`/api/system/health` = `UP`; frontend `/healthz` = `ok`)
~~~

## Business Validation Result

~~~text
Business validation: PASS (local `npm run e2e:real-pre:p0:preflight`; targeted controller/query tests)
~~~

## Content Maintenance Result

~~~text
Content maintenance: Plan. Manifest=. DryRun=False.
~~~

## Harness Governance Result

`check-harness-limits.ps1 -BaselineRef HEAD`: TASK_GATE=FAIL, REPOSITORY_HEALTH=PARTIAL. The failure is from pre-existing/unrelated `harness/reports` root timestamp files and line/file-count debt; this task added only the stable report under `harness/reports/current` and did not remove unrelated evidence.

## Remote Deploy Result

~~~text
Remote deploy: PASS (`deploy-remote.ps1 -Env real-pre`)
Remote commit: `f2a3e45cf7d8eae8fc96db72c4455ef403483c1c`
Remote containers: backend/frontend healthy
Remote health: PASS (backend `/api/system/health` = `{"status":"UP"}`; frontend `/healthz` = `ok`)

Remote biz_staff read-only business verification: PASS
- Login: HTTP 200; roleCodes=`biz_staff`; dataScope=`1`
- `GET /api/data/orders/detail?page=1&size=5`: HTTP 200, total=301734, returned=5
- `GET /api/data/orders/detail?page=1&size=5&startDate=2020-01-01&endDate=2030-01-01`: HTTP 200, total=537310, returned=5
- Returned detail fields include order, activity, product, talent, payment, estimated fees, create/pay time and settle status fields.
- No test data was written.
~~~

## Retro Summary

The initial remote push failed due a transient GitHub SSL handshake error; retry succeeded. The code commit and deployment-branch commit were both pushed successfully.

## Conclusion

PASS

## Residual Risk

- The `/api/data/orders/detail` list is now full-read for `biz_staff`; channel-staff order scope remains channel-based.
- Order-detail export remains a separate permission: `/api/orders/exports/detail` is still restricted to admin/leader roles and was not changed by this task.
- Single-order detail access was not broadened beyond the order-detail list endpoint.

## Retro Conclusion

The deployment script was used as the sole remote deployment entrypoint, followed by remote health and authenticated business verification. No new standalone retro action is required.

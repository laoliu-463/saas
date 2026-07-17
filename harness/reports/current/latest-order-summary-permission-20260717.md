# Evidence Report

## Metadata

- Time: 2026-07-17 17:21:00 +08:00
- Environment: real-pre
- Scope: backend
- Branch: codex/ddd-user-role-application
- Commit: e5311951 (implementation commit; evidence/state update follows)
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/controller/DataController.java
backend/src/main/java/com/colonel/saas/service/data/DataApplicationService.java
backend/src/test/java/com/colonel/saas/controller/DataControllerTest.java
harness/reports/current/latest-content-retire.md
harness/rules/state/snapshots/DOMAIN_STATUS.md
harness/rules/state/snapshots/01-当前项目状态.md
~~~

## Owned Git Status

~~~text
M backend/src/main/java/com/colonel/saas/controller/DataController.java
 M backend/src/main/java/com/colonel/saas/service/data/DataApplicationService.java
 M backend/src/test/java/com/colonel/saas/controller/DataControllerTest.java
 M harness/reports/current/latest-content-retire.md
~~~

## Build Result

~~~text
Targeted regression: PASS (`DataControllerTest` 49/49; final run 2026-07-17 17:20:18 +08:00)
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    38 seconds ago   Up 33 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   28 minutes ago   Up 27 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   3 hours ago      Up 3 hours (healthy)      5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      2 days ago       Up 2 days (healthy)       6379/tcp
NAMES                             STATUS                    PORTS
saas-active-backend-real-pre-1    Up 34 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   Up 27 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   Up 3 hours (healthy)      5432/tcp
saas-active-redis-real-pre-1      Up 2 days (healthy)       6379/tcp
campus_frontend                   Up 3 days                 0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 3 days (healthy)       0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 3 days (healthy)       0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 3 days (unhealthy)     0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
~~~

## Health Check Result

~~~text
Local health verification: PASS
~~~

## Business Validation Result

~~~text
Business validation: PASS (npm run e2e:real-pre:p0:preflight)
Browser/API validation: PASS using the local real-pre `biz_staff` test account. For 2026-07-13..2026-07-19, `GET /api/data/orders/summary` returned HTTP/code 200 with `orderCount=45246`, `serviceFeeIncome=22872.44`, `serviceFeeExpense=5.70`, `serviceFeeProfit=21002.26`, `grossProfit=14701.16`; `GET /api/data/orders/detail` returned HTTP/code 200 with `total=45246` and 20 records. The first detail records contained pay amount, estimated service fee/technical fee/expense/profit, recruiter/channel commission and gross profit fields. The browser rendered the financial columns and both estimate/settlement lines. For 2026-06-11..2026-06-12 on `settleTime`, summary/detail returned 9377 orders and a record with `settleAmount=1`, `effectiveServiceFee=0.02`, and `effectiveServiceProfit=0.02`.
Browser evidence screenshots: `D:\Projects\SAAS\output\local-biz-staff-order-detail-financials.png`, `D:\Projects\SAAS\output\local-biz-staff-order-detail-service-fees.png`, `D:\Projects\SAAS\output\local-biz-staff-order-detail-financials-right.png`.
~~~

## Content Maintenance Result

~~~text
Content maintenance: Plan. Manifest=. DryRun=False.
~~~

## Remote Deploy Result

~~~text
remote not deployed
~~~

## Retro Summary

Retro: root cause was role context being dropped at the order-summary and order-detail performance BFF boundaries. The fix adds role-aware summary filtering, role-sensitive cache keys, and a regression test for the BIZ_STAFF full read-only order view; no separate Harness improvement remains.

## Conclusion

PASS

## Residual Risk

- Current-week settlement fields can legitimately display `-` for orders not yet settled; a settled-time window was separately verified with non-null settlement values.
- Remote deployment was not requested and was not performed.

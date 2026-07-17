# Evidence Report

## Metadata

- Time: 2026-07-17 16:43:55 +08:00
- Environment: real-pre
- Scope: frontend
- Branch: codex/ddd-user-role-application
- Commit: b650c477
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
frontend/src/views/data/OrderList.test.ts
frontend/src/views/data/OrderList.vue
harness/reports/current/latest-content-retire.md
~~~

## Owned Git Status

~~~text
M frontend/src/views/data/OrderList.test.ts
 M frontend/src/views/data/OrderList.vue
 M harness/reports/current/latest-content-retire.md
~~~

## Build Result

~~~text
Frontend build: PASS (npm --prefix frontend ci; npm --prefix frontend run build)
Targeted regression tests: PASS (15/15, OrderList.test.ts)
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    43 seconds ago   Up 37 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   40 seconds ago   Up 15 seconds (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   3 hours ago      Up 3 hours (healthy)      5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      2 days ago       Up 2 days (healthy)       6379/tcp
NAMES                             STATUS                    PORTS
saas-active-frontend-real-pre-1   Up 15 seconds (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 37 seconds (healthy)   127.0.0.1:8081->8080/tcp
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
~~~

## Manual Browser Validation Result

~~~text
Environment: local real-pre, http://127.0.0.1:3001, viewport 1280x720
Flow: login as biz_staff -> /data/orders -> click "订单明细"
Detail API: HTTP 200, code=200, records=20, total=44980 at capture time
After tab switch: data tab active="订单明细"; inner scrollTop=404; first order row top=471.8px, bottom=491px
First visible order ID: 6954534360780510851
Console errors: none; failed requests: none
Screenshot: output/local-order-detail-auto-scroll-1280.png
Result: PASS — clicking the detail tab automatically scrolls the nested content container so real order rows are visible in the first viewport.
~~~

## Content Maintenance Result

~~~text
Content maintenance: Plan. Manifest=. DryRun=False.
~~~

## Remote Deploy Result

~~~text
remote not deployed
~~~

## Governance Check

~~~text
TASK_GATE: PASS
REPOSITORY_HEALTH: PARTIAL — existing historical report-count/size debt remains outside this task; no new task-owned violation was introduced.
~~~

## Retro Summary

No actionable Harness improvement was recorded; no standalone retro is required.

## Conclusion

PASS

## Residual Risk

- Remote real-pre was not deployed in this task.
- The captured total is live data and may change; the validation proves the returned detail rows are rendered and visible.

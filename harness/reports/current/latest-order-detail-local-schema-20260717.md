# Evidence Report

## Metadata

- Time: 2026-07-17 16:21:35 +08:00
- Environment: real-pre
- Scope: backend
- Branch: codex/ddd-user-role-application
- Commit: 45c45c31
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
backend/src/main/resources/db/init-db.sql
backend/src/main/resources/db/migrate-all.sql
backend/src/test/java/com/colonel/saas/architecture/ColonelsettlementOrderMapperDualDimensionContractTest.java
harness/reports/current/latest-content-retire.md
~~~

## Owned Git Status

~~~text
Committed by the fixed agent-do entrypoint; the workspace still contains pre-existing unrelated dirty files.
~~~

## Build Result

~~~text
not collected
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
Targeted contract test: PASS (ColonelsettlementOrderMapperDualDimensionContractTest, 6 tests, 0 failures)
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    40 seconds ago   Up 35 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   11 minutes ago   Up 11 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   2 hours ago      Up 2 hours (healthy)      5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      2 days ago       Up 2 days (healthy)       6379/tcp
NAMES                             STATUS                    PORTS
saas-active-backend-real-pre-1    Up 35 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   Up 11 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   Up 2 hours (healthy)      5432/tcp
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

## Root Cause and Schema Repair

- Initial local request: `GET /api/data/orders/detail` returned HTTP 200 with business `code=500` and `msg=服务器异常`.
- Backend log evidence: PostgreSQL reported `column co.channel_attribution_status does not exist` from `DataApplicationService.getOrderDetailPage`.
- Root cause: commit `c9c5e6b6` added the Mapper and standalone migration, but did not connect the migration to `migrate-all.sql` or the fresh-database initialization schema.
- Fix: added the dual-dimension attribution columns/indexes to `init-db.sql`, added `\i alter-cso-dual-attribution-status-20260716.sql` to `migrate-all.sql`, and added a migration contract assertion.
- Local migration: applied only `alter-cso-dual-attribution-status-20260716.sql` through the controlled per-file migration steps; no volume deletion and no full historical `migrate-all.sql` replay.
- Database verification: both `colonelsettlement_order.channel_attribution_status` and `recruiter_attribution_status` exist; `schema_migration_log` contains `alter-cso-dual-attribution-status-20260716.sql`.

## Business Validation Evidence

- Account: local `biz_staff`.
- API: `GET /api/data/orders/detail?page=1&size=20&timeField=createTime&startDate=2026-07-13&endDate=2026-07-19` returned HTTP 200, `code=200`, `records=20`, `total=44337`.
- Browser: `http://127.0.0.1:3001/data/orders` -> click `订单明细`; no `服务器异常`, no `无数据`, and the returned order rows were visible after scrolling the table into the viewport.
- The first screenshot `output/local-order-detail-after-schema-fix.png` was invalid as visual evidence because it only captured the filter area and tabs; it has been superseded.
- Replacement screenshot with visible order rows: `output/local-order-detail-table-visible.png`.
- UX finding at 1280x720: the first detail row starts around `y=855`, while the viewport ends at `y=720`; `.app-layout` uses `overflow:hidden` and the inner Naive UI content container owns scrolling. The API data is present, but switching to `订单明细` does not auto-scroll the inner content to the table, so users can perceive the page as empty until they scroll the main content area.

## Business Validation Result

~~~text
Business validation: PASS (npm run e2e:real-pre:p0:preflight)
~~~

## Content Maintenance Result

~~~text
Content maintenance: Plan. Manifest=. DryRun=False.
~~~

## Harness Governance

- Scoped check with this task's owned files: `TASK_GATE=PASS`, `REPOSITORY_HEALTH=PARTIAL`.
- Standalone workspace check: `TASK_GATE=FAIL` because unrelated pre-existing untracked timestamp reports and evidence files are present under `harness/reports`; they were not modified or owned by this task.
- Historical repository debt remains: `harness/reports` has 39 direct files and one 258-line evidence file.

## Remote Deploy Result

~~~text
remote not deployed
~~~

## Retro Summary

No actionable Harness improvement was recorded; no standalone retro is required.

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
- Remote deployment was intentionally not executed because the current request was to repair local real-pre only.
- Remaining UI issue: add an explicit scroll-to-detail-table behavior or adjust the page layout so the first detail rows are visible after clicking `订单明细` at 1280x720.

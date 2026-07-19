# Evidence Report

## Metadata

- Time: 2026-07-19 19:07:39 +08:00
- Environment: real-pre
- Scope: backend
- Branch: codex/ddd-user-role-application
- Commit: 02fcc18a
- Owned worktree: clean after task commit
- Deploy remote: true (explicitly skipped backup)

## Owned Files

~~~text
backend/src/main/resources/db/init-db.sql
backend/src/main/resources/db/migrate/V20260719_002__talent_order_lookup_index.sql
backend/src/test/java/com/colonel/saas/config/TalentOrderLookupIndexMigrationContractTest.java
docs/06-数据模型总表.md
harness/rules/state/snapshots/01-当前项目状态.md
harness/rules/state/snapshots/DOMAIN_STATUS.md
~~~

## Owned Git Status

~~~text
Clean after commit 02fcc18a; report was reopened only to append remote verification evidence.
~~~

## Build Result

~~~text
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
Targeted regression: PASS (migration contract, LegacyOrderReadFacade, TalentQueryService,
DddTalentOrderFacadeBoundary and real-pre migration contract).
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED              STATUS                        PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    About a minute ago   Up About a minute (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   3 hours ago          Up 3 hours (healthy)          127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   About a minute ago   Up About a minute (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      22 hours ago         Up 22 hours (healthy)         6379/tcp
NAMES                             STATUS                        PORTS
saas-active-backend-real-pre-1    Up About a minute (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up About a minute (healthy)   5432/tcp
saas-active-frontend-real-pre-1   Up 3 hours (healthy)          127.0.0.1:3001->80/tcp
saas-active-redis-real-pre-1      Up 22 hours (healthy)         6379/tcp
saas-test-frontend-1              Up 24 hours (healthy)         0.0.0.0:3000->3000/tcp, [::]:3000->3000/tcp
saas-test-backend-1               Up 24 hours (healthy)         0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
saas-test-postgres-1              Up 24 hours (healthy)         0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp
campus_frontend                   Up 25 hours                   0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 25 hours (healthy)         0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 25 hours (healthy)         0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-redis-1                 Up 25 hours (healthy)         6379/tcp
~~~

## Health Check Result

~~~text
Local health verification: PASS
~~~

## Business Validation Result

~~~text
Business validation: PASS (npm run e2e:real-pre:p0:preflight)
~~~

## Content Maintenance Result

~~~text
Content maintenance skipped by -ContentMaintenance off.
~~~

## Remote Deploy Result

~~~text
PASS
- Remote checkout/image revision: 02fcc18a83b85137954f9bd845344c296ae34b05
- Backup: skipped by explicit operator request
- Flyway: 20260719.002 talent order lookup index = success
- Schema contract: PASS; 12 order partitions checked
- Backend health: {"status":"UP"}
- Frontend health: ok
- Backend ERROR count after deploy (10m): 0
~~~

## Performance Verification

~~~text
Target: the same remote talent-detail order query observed before the change.
Before: Execution Time 6183.685 ms; execution buffers hit=222971/read=370105 (593076 total).
After:  Execution Time 0.528 ms; execution buffers hit=18/read=9 (27 total).
Result: 99.991% lower SQL execution time; partition child Index Scan confirmed.

Talent-list 20-UID / 30-day aggregation after deploy:
- Execution Time: 0.819 ms
- Buffers: hit=67/read=4
- Populated partitions use Bitmap Index Scan on the new expression/recency index.

Index state:
- Parent index: idx_cso_talent_lookup_create_time
- Child indexes: 12/12 valid; invalid=0
- Total index size: 23 MB
~~~

## Upstream Boundary Verification

~~~text
TALENT_PROFILE_HTTP_ENABLED=false
TALENT_PROFILE_PUBLIC_WEB_ENABLED=false
Talent upstream call logs after deploy (10m): 0
No upstream provider, role permission, order fact, attribution or state-machine behavior changed.
~~~

## Retro Summary

No actionable Harness improvement was recorded; no standalone retro is required.

## Conclusion

PASS

## Residual Risk

- No authenticated HTTP request was replayed after deployment because no reusable login credential was introduced into this task. The performance result is the before/after `EXPLAIN ANALYZE` of the exact SQL identified in the original slow endpoint logs; the next real user request should still be observed at controller level.
- Rollback is `DROP INDEX IF EXISTS idx_cso_talent_lookup_create_time;`; it removes only the optimization and does not rewrite order facts.

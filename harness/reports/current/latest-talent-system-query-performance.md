# Evidence Report

## Metadata

- Time: 2026-07-19 19:00:36 +08:00
- Environment: real-pre
- Scope: backend
- Branch: codex/ddd-user-role-application
- Commit: f6f008c9
- Owned worktree: dirty
- Deploy remote: false

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
M backend/src/main/resources/db/init-db.sql
 M docs/06-数据模型总表.md
 M harness/rules/state/snapshots/01-当前项目状态.md
 M harness/rules/state/snapshots/DOMAIN_STATUS.md
?? backend/src/main/resources/db/migrate/V20260719_002__talent_order_lookup_index.sql
?? backend/src/test/java/com/colonel/saas/config/TalentOrderLookupIndexMigrationContractTest.java
~~~

## Build Result

~~~text
not collected
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
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
remote not deployed
~~~

## Retro Summary

No actionable Harness improvement was recorded; no standalone retro is required.

## Conclusion

PASS

## Residual Risk

- Items marked as not collected are not proof of success.

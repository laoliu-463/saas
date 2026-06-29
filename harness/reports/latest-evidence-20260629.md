# Evidence Report

## Metadata

- Time: 2026-06-29 15:32:12 +08:00
- Environment: real-pre
- Scope: full
- Branch: feature/ddd/DDD-VERIFY-001
- Commit: 450f8aa8
- Worktree: dirty
- Deploy remote: false

## Modified Files

~~~text
backend/src/main/java/com/colonel/saas/domain/product/application/ProductBackfillJobMetadata.java
backend/src/main/java/com/colonel/saas/domain/product/query/ProductBackfillJobStatusQueryService.java
backend/src/main/java/com/colonel/saas/service/ProductActivityBackfillService.java
backend/src/test/java/com/colonel/saas/domain/product/query/ProductBackfillJobStatusQueryServiceTest.java
backend/src/test/java/com/colonel/saas/service/ProductActivityBackfillServiceTest.java
harness/reports/evidence-20260627-205740.md
harness/reports/evidence-20260627-205810.md
~~~

## Git Status

~~~text
 M backend/src/main/java/com/colonel/saas/domain/product/application/ProductBackfillJobMetadata.java
 M backend/src/main/java/com/colonel/saas/domain/product/query/ProductBackfillJobStatusQueryService.java
 M backend/src/main/java/com/colonel/saas/service/ProductActivityBackfillService.java
 M backend/src/test/java/com/colonel/saas/domain/product/query/ProductBackfillJobStatusQueryServiceTest.java
 M backend/src/test/java/com/colonel/saas/service/ProductActivityBackfillServiceTest.java
 D harness/reports/evidence-20260627-205740.md
 D harness/reports/evidence-20260627-205810.md
~~~

## Build Result

~~~text
RED: mvn test -Dtest='ProductActivityBackfillServiceTest,ProductBackfillJobStatusQueryServiceTest' initially failed as expected before implementation: status query returned activitiesScanned=0 instead of metadata total 5; real-run progress metadata lacked activitiesTotal.
GREEN: mvn test -Dtest='ProductActivityBackfillServiceTest,ProductBackfillJobStatusQueryServiceTest' PASS, 15 tests.
Targeted: mvn test -Dtest='ProductActivityBackfillServiceTest,ProductBackfillConcurrencyAndDeadlockTest,ProductBackfillJobMetadataTest,ProductBackfillJobStatusQueryServiceTest,ProductBackfillFailurePolicyTest' PASS, 32 tests.
Compile: mvn -DskipTests compile PASS.
Package: mvn -DskipTests package PASS.
Architecture/DDD/Guard/Contract: mvn test -Dtest='*Architecture*Test,*Ddd*Test,*Guard*Test,*Contract*Test' FAIL, 184 tests, 5 known failures in Order/User architecture debt; Product architecture tests passed.
Product: mvn test -Dtest='*Product*Test' PASS, 345 tests.
Full: mvn test FAIL, 2733 tests, 5 failures, 0 errors, 3 skipped; failures match the same Order/User architecture guard debt.
~~~

## Docker Status

### docker compose ps

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED              STATUS                        PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    About a minute ago   Up About a minute (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   About a minute ago   Up About a minute (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   About a minute ago   Up About a minute (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      2 weeks ago          Up 10 days (healthy)          6379/tcp
~~~

### docker ps

~~~text
NAMES                             STATUS                        PORTS
saas-active-frontend-real-pre-1   Up About a minute (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up About a minute (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up About a minute (healthy)   5432/tcp
rsshub-kb-test                    Up 3 days                     127.0.0.1:1200->1200/tcp
open_llm_vtuber_kb_postgres       Up 7 days                     127.0.0.1:25432->5432/tcp
campus_frontend                   Up 9 days                     0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 9 days (healthy)           0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 9 days (healthy)           0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-active-redis-real-pre-1      Up 10 days (healthy)          6379/tcp
saas-test-backend-1               Up 9 days (unhealthy)         0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
saas-test-postgres-1              Up 10 days (healthy)          0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp
saas-test-redis-1                 Up 10 days (healthy)          6379/tcp
~~~

## Health Check Result

~~~text
Safety: safety-check.ps1 -Env real-pre PASS.
Restart: restart-compose.ps1 -Env real-pre PASS; backend-real-pre rebuilt and healthy, frontend-real-pre rebuilt.
Health: verify-local.ps1 -Env real-pre PASS; backend /api/system/health returned 200 {"status":"UP"}, frontend /healthz returned 200.
Remote: not deployed; user rule says no push/no remote deploy.
~~~

## Business Validation Result

~~~text
Product backfill job status unauthenticated smoke: GET http://127.0.0.1:8081/api/product-sync/admin/backfill-jobs/product-backfill-nonexistent returned 401, confirming admin auth boundary and no real backfill execution.
Characterization/parity added for real-run progress metadata: request_params_json now records currentActivityId, activitiesTotal, activitiesProcessed during sorted activity execution without API/schema/state changes.
Status query parity added: when snapshot activitiesScanned is not finished/zero during RUNNING state, existing job-status view uses request metadata activitiesTotal for activitiesScanned visibility.
~~~

## Content Maintenance Result

~~~text
Archived 3 oldest report files to harness/archive/reports-overflow-20260629-ddd-product-backfill-progress-total and generated latest-evidence-20260629.md, keeping harness/reports at the 50-file direct limit.
Post-generation checks completed: harness limits PASS, git diff --check PASS, temporary .env.real-pre hardlink removed.
~~~

## Remote Deploy Result

~~~text
remote not deployed by user rule
~~~

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
- If real-pre lacks real orders or pick_source samples, record the result as PENDING or PARTIAL.

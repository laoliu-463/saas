# Evidence Report

## Metadata

- Time: 2026-07-18 15:54:57 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/ddd-user-role-application
- Commit: 19a16cfb
- Owned worktree: clean
- Deploy remote: true

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/dto/sample/SampleActionRequest.java
backend/src/main/java/com/colonel/saas/dto/sample/SampleBatchShipItem.java
backend/src/main/java/com/colonel/saas/service/sample/SampleApplicationService.java
backend/src/test/java/com/colonel/saas/controller/SampleControllerTest.java
docker-compose.real-pre.yml
frontend/src/api/sample.ts
frontend/src/utils/shippingBatch.test.ts
frontend/src/utils/shippingBatch.ts
frontend/src/views/sample/SampleDetail.vue
harness/scripts/commands/_lib.ps1
harness/scripts/commands/deploy-remote.ps1
scripts/run-real-pre-db-migrations.sh
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
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    4 minutes ago    Up 4 minutes (healthy)    127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   4 minutes ago    Up 3 minutes (healthy)    127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   13 minutes ago   Up 13 minutes (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      28 minutes ago   Up 28 minutes (healthy)   6379/tcp
NAMES                                                      STATUS                    PORTS
fervent_albattani                                          Up 2 minutes              0.0.0.0:45013->5432/tcp, [::]:45013->5432/tcp
saas-active-frontend-real-pre-1                            Up 3 minutes (healthy)    127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1                             Up 4 minutes (healthy)    127.0.0.1:8081->8080/tcp
testcontainers-ryuk-843cdec2-d0ae-4a4c-8b02-eff9f460f7fd   Up 4 minutes              0.0.0.0:45573->8080/tcp, [::]:45573->8080/tcp
saas-active-postgres-real-pre-1                            Up 13 minutes (healthy)   5432/tcp
saas-active-redis-real-pre-1                               Up 28 minutes (healthy)   6379/tcp
campus_frontend                                            Up 4 days                 0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                                             Up 4 days (healthy)       0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                                            Up 4 days (healthy)       0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1                                        Up 4 days (unhealthy)     0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
saas-test-postgres-1                                       Up 4 hours (healthy)      0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp
saas-test-redis-1                                          Up 4 hours (healthy)      6379/tcp
~~~

## Health Check Result

~~~text
Local health verification: PASS
~~~

## Business Validation Result

~~~text
Business validation: PASS (mvn -q -f backend/pom.xml -Dtest=SampleControllerTest test; if ($LASTEXITCODE -ne 0) { exit 1 }; npm --prefix frontend run test -- --run src/utils/shippingBatch.test.ts src/api/sample.test.ts; if ($LASTEXITCODE -ne 0) { exit 1 })
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

agent-do failed: Remote deploy failed with exit code 1.

## Conclusion

FAIL

## Residual Risk

- Items marked as not collected are not proof of success.

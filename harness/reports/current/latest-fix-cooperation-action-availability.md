# Evidence Report

## Metadata

- Time: 2026-07-21 14:55:23 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/fix-cooperation-action-availability
- Commit: 8df4147c
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/domain/sample/policy/SampleCooperationActionPolicy.java
backend/src/main/java/com/colonel/saas/domain/sample/policy/SampleStateMachine.java
backend/src/test/java/com/colonel/saas/architecture/DddSampleExceptionBranchCoverageContractTest.java
backend/src/test/java/com/colonel/saas/controller/SampleControllerTest.java
backend/src/test/java/com/colonel/saas/domain/sample/policy/SampleCooperationActionPolicyTest.java
backend/src/test/java/com/colonel/saas/domain/sample/policy/SampleStateMachineTest.java
frontend/src/views/sample/CooperationActionColumn.test.ts
harness/reports/current/latest-content-retire.md
~~~

## Owned Git Status

~~~text
M harness/reports/current/latest-content-retire.md
~~~

## Build Result

~~~text
not collected
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
Frontend build: PASS (npm --prefix frontend ci; npm --prefix frontend run build)
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                       PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    26 seconds ago   Up 23 seconds (healthy)      127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   24 seconds ago   Up 6 seconds (healthy)       127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   14 minutes ago   Up 14 minutes (healthy)      5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      2 days ago       Up About an hour (healthy)   6379/tcp
NAMES                             STATUS                            PORTS
saas-active-frontend-real-pre-1   Up 7 seconds (healthy)            127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 23 seconds (healthy)           127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 14 minutes (healthy)           5432/tcp
saas-active-redis-real-pre-1      Up About an hour (healthy)        6379/tcp
saas-test-backend-1               Up 5 seconds (health: starting)   0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
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
Content maintenance: Plan. Manifest=. DryRun=False.
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

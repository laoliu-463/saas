# Evidence Report

## Metadata

- Time: 2026-07-20 19:36:24 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/ddd-performance-single-consumer
- Commit: 31e97ad8
- Owned worktree: clean
- Deploy remote: false

## Owned Files

~~~text
backend/src/test/java/com/colonel/saas/architecture/DddPerformanceRecordGenerationEntrypointTest.java
harness/reports/current/latest-content-retire.md
~~~

## Owned Git Status

~~~text
(clean)
~~~

## Build Result

~~~text
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
Frontend build: PASS (npm --prefix frontend ci; npm --prefix frontend run build)
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    47 seconds ago   Up 33 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   45 seconds ago   Up 16 seconds (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   48 seconds ago   Up 44 seconds (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      47 hours ago     Up 47 hours (healthy)     6379/tcp
NAMES                             STATUS                    PORTS
saas-active-frontend-real-pre-1   Up 16 seconds (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 33 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 44 seconds (healthy)   5432/tcp
saas-active-redis-real-pre-1      Up 47 hours (healthy)     6379/tcp
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

Initial agent-do push attempted origin/main because the new branch inherited the base branch upstream and was rejected by the protected-branch rule. Corrected the upstream to origin/codex/ddd-performance-single-consumer and pushed HEAD with an explicit refspec: PASS.
- Push target must be checked after every new worktree is created; the first attempt inherited origin/main and was rejected.
- No standalone retro is required; the correction is recorded inline above.

## Conclusion

PASS

## Residual Risk

- Repository health remains PARTIAL because of pre-existing Harness report-budget debt; TASK_GATE passed.
- Frontend install reported 6 npm audit vulnerabilities; dependency remediation is out of scope for this slice.
- No remote deployment was performed; release still requires the normal main -> release/real-pre -> Jenkins queue.

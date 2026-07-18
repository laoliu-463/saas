# Evidence Report

## Metadata

- Time: 2026-07-18 20:11:29 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/ddd-user-role-application
- Commit: 4139e9ee
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/douyin/DouyinTokenService.java
backend/src/test/java/com/colonel/saas/douyin/DouyinTokenServiceTest.java
docs/对接/抖音授权与Token.md
docs/验收/验收证据索引.md
harness/reports/current/latest-content-retire.md
harness/rules/changelog.md
harness/rules/state/snapshots/01-当前项目状态.md
harness/rules/state/snapshots/DOMAIN_STATUS.md
harness/scripts/commands/git-push-safe.ps1
harness/scripts/commands/safety-check.ps1
runtime/qa/real-pre-preflight.cjs
runtime/qa/real-pre-preflight.test.cjs
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
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED             STATUS                       PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    46 seconds ago      Up 29 seconds (healthy)      127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   42 seconds ago      Up 13 seconds (healthy)      127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   About an hour ago   Up About an hour (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      46 seconds ago      Up 40 seconds (healthy)      6379/tcp
NAMES                             STATUS                       PORTS
saas-active-frontend-real-pre-1   Up 13 seconds (healthy)      127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 30 seconds (healthy)      127.0.0.1:8081->8080/tcp
saas-active-redis-real-pre-1      Up 41 seconds (healthy)      6379/tcp
saas-test-frontend-1              Up 40 minutes (healthy)      0.0.0.0:3000->3000/tcp, [::]:3000->3000/tcp
saas-test-backend-1               Up 41 minutes (healthy)      0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
saas-test-postgres-1              Up 45 minutes (healthy)      0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp
saas-active-postgres-real-pre-1   Up About an hour (healthy)   5432/tcp
campus_frontend                   Up 3 hours                   0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 3 hours (healthy)         0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 3 hours (healthy)         0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-redis-1                 Up 3 hours (healthy)         6379/tcp
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

# Evidence Report

## Metadata

- Time: 2026-07-18 19:29:51 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/ddd-user-role-application
- Commit: 1b2966ed
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/douyin/DouyinTokenService.java
backend/src/test/java/com/colonel/saas/douyin/DouyinTokenServiceTest.java
docs/对接/抖音授权与Token.md
docs/验收/验收证据索引.md
harness/reports/current/latest-douyin-token-local-bootstrap.md
harness/rules/changelog.md
harness/rules/state/snapshots/01-当前项目状态.md
harness/rules/state/snapshots/DOMAIN_STATUS.md
harness/scripts/commands/safety-check.ps1
harness/scripts/commands/git-push-safe.ps1
runtime/qa/real-pre-preflight.cjs
runtime/qa/real-pre-preflight.test.cjs
~~~

## Owned Git Status

~~~text
M docs/对接/抖音授权与Token.md
 M docs/验收/验收证据索引.md
 M harness/reports/current/latest-douyin-token-local-bootstrap.md
 M harness/rules/changelog.md
 M harness/rules/state/snapshots/01-当前项目状态.md
 M harness/rules/state/snapshots/DOMAIN_STATUS.md
 M harness/scripts/commands/safety-check.ps1
~~~

## Build Result

~~~text
Backend build: PASS (`mvn -f backend/pom.xml -DskipTests package`).
Frontend build: PASS (`npm --prefix frontend ci`; `npm --prefix frontend run build`).
Targeted backend test: PASS (`mvn -f backend/pom.xml -Dtest=DouyinTokenServiceTest test`, 31 tests).
Targeted preflight tests: PASS (`node --test runtime/qa/real-pre-preflight.test.cjs`, 9 tests).
Backend full regression: FAIL (`mvn -f backend/pom.xml test`, 3297 tests / 1 failure / 0 errors / 3 skipped); failure is the pre-existing unowned `LargeServiceDebtRedlineTest` for `service/sample/SampleApplicationService.java` (3665 > baseline 3613).
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    8 minutes ago    Up 8 minutes (healthy)    127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   8 minutes ago    Up 7 minutes (healthy)    127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   34 minutes ago   Up 34 minutes (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      22 minutes ago   Up 22 minutes (healthy)   6379/tcp
NAMES                             STATUS                    PORTS
saas-test-postgres-1              Up 4 minutes (healthy)    0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp
saas-active-frontend-real-pre-1   Up 7 minutes (healthy)    127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 8 minutes (healthy)    127.0.0.1:8081->8080/tcp
saas-active-redis-real-pre-1      Up 22 minutes (healthy)   6379/tcp
saas-active-postgres-real-pre-1   Up 34 minutes (healthy)   5432/tcp
campus_frontend                   Up 2 hours                0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 2 hours (healthy)      0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 2 hours (healthy)      0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-redis-1                 Up 2 hours (healthy)      6379/tcp
~~~

## Health Check Result

~~~text
Local health verification: PASS; backend `/api/system/health` and frontend `/healthz` returned success before the credential guard was added. Current containers remain healthy.
~~~

## Business Validation Result

~~~text
Business validation: PARTIAL.
- `npm run e2e:real-pre:p0:preflight`: PASS, but it only proved old Redis access/refresh keys were present.
- User authorization-code bootstrap: BLOCKED by upstream `40003 / isv.signature-invalid`.
- Live `POST /api/douyin/token-refreshes`: BLOCKED by upstream `40003 / isv.signature-invalid`.
- `.env.real-pre` and the running container were compared without printing values; `DOUYIN_CLIENT_SECRET` was confirmed to be the same placeholder in both.
- Follow-up fixed-entry run: BLOCKED by the new safety-check before build/restart, as intended.
~~~

## Content Maintenance Result

~~~text
Content maintenance: PASS on the functional run; the follow-up guard run was stopped at safety-check.
~~~

## Remote Deploy Result

~~~text
remote not deployed
~~~

## Retro Summary

Actionable improvement: `safety-check.ps1` now rejects placeholder `DOUYIN_APP_ID`, `DOUYIN_CLIENT_KEY`, and `DOUYIN_CLIENT_SECRET` before build/restart, preventing stale Redis Token status from masking an upstream signing configuration failure. Direct safety-check and fixed-entry rerun both produced `BLOCKED` without exposing values.

## Conclusion

PARTIAL

## Residual Risk

- Token persistence code and automated tests are PASS.
- Full backend regression is FAIL on an unrelated unowned sample service debt baseline; no Token test failed.
- Local real upstream bootstrap/refresh remains BLOCKED until a real `DOUYIN_CLIENT_SECRET` matching the working remote credential is supplied.
- Remote deployment was not requested or performed.

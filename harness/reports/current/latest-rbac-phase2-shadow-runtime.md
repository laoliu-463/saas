# Evidence Report

## Metadata

- Time: 2026-07-14 21:08:28 +08:00
- Environment: real-pre
- Scope: backend
- Branch: codex/rbac-shadow-runtime-plan
- Commit: 122ad1bd (Harness evidence commit; this report is finalized in a follow-up evidence-only commit)
- Owned worktree: clean after the Harness commit; report finalization is the only subsequent owned change
- Deploy remote: false

## Owned Files

~~~text
docs/07-权限与数据范围.md
docs/领域/用户域.md
harness/reports/current/latest-harness-limits-check.md
harness/reports/current/latest-rbac-phase2-shadow-runtime.md
~~~

## Worktree Status Timeline

- Harness collection snapshot: the pre-commit owned status shown below.
- After Harness commit `122ad1bd`: clean and aligned with upstream.
- Evidence finalization snapshot: dirty only because this report is being enriched; no source, test, configuration or migration file changed after the verified Harness run.

~~~text
M docs/07-权限与数据范围.md
 M docs/领域/用户域.md
 M harness/reports/current/latest-harness-limits-check.md
?? harness/reports/current/latest-rbac-phase2-shadow-runtime.md
~~~

## Build Result

~~~text
not collected
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    26 seconds ago   Up 23 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   45 minutes ago   Up 44 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   4 minutes ago    Up 4 minutes (healthy)    5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      4 weeks ago      Up 5 hours (healthy)      6379/tcp
NAMES                             STATUS                    PORTS
saas-active-backend-real-pre-1    Up 23 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 4 minutes (healthy)    5432/tcp
saas-active-frontend-real-pre-1   Up 44 minutes (healthy)   127.0.0.1:3001->80/tcp
campus_frontend                   Up 5 hours                0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 5 hours (healthy)      0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 5 hours (healthy)      0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-active-redis-real-pre-1      Up 5 hours (healthy)      6379/tcp
saas-test-backend-1               Up 5 hours (unhealthy)    0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
~~~

## Health Check Result

~~~text
Local health verification: PASS
~~~

## Full Backend Verification

- `mvn test`: PASS.
- Surefire XML written by the full run: 606 suites, 3419 tests, 0 failures, 0 errors, 3 skipped.
- One stale XML for the deleted `DddAuthorizationDormancyContractTest` was excluded by timestamp and file identity.
- `mvn -DskipTests package`: PASS.
- Scheduled-task shutdown connection noise did not produce a Maven failure or a Surefire failure/error.

## Local Migration Verification

- Approved file: `backend/src/main/resources/db/alter-authorization-foundation-20260713.sql`.
- SHA-256: `5E724B1B1E0999DC140BF42837C467640134F2B8BED741848F270912324AFE6C`.
- Static precheck: 0 DML statements, 0 DROP matches, 0 CASCADE matches.
- Pre-migration catalog: `0|0` (authorization tables | `sys_user.authz_version`), with 6 active users.
- The exact file was applied twice to local `real-pre`; both invocations exited 0. The second run emitted only expected already-exists notices.
- Post-migration catalog and seed counts: `4|1|0|0|0|0|0`.
- `sys_user.authz_version`: `bigint NOT NULL DEFAULT 1`; no user has a null or non-positive version.
- Rollback boundary is code/configuration back to LEGACY; additive tables, column, indexes and volumes are retained.

## Local LEGACY Runtime Verification

- Resolved mode: `LEGACY(default)`; `AUTHORIZATION_RUNTIME_DEFAULT_MODE` is absent from local `.env.real-pre` and the container.
- `/api/system/health`: HTTP 200 with `{"status":"UP"}`.
- Unauthenticated `GET /api/users?page=1&size=1`: HTTP 401 with the unified JSON envelope.
- Redis key-name scan: 0 `authz:snapshot:*` keys and 0 unversioned authorization snapshot keys; no values were read.
- The post-migration database catalog remained `4|1|0|0|0|0|0` after backend/PostgreSQL container recreation.

## Business Validation Result

~~~text
P0 preflight was executed before the evidence-only Harness rerun.
Frontend login and backend health probes passed.
Admin login returned HTTP 401 after 5 attempts; the environment guard then had no admin token.
Douyin token readiness and authenticated/token-version flows are BLOCKED_AUTH.
The Harness rerun used -SkipBusinessValidation only to collect and commit truthful PARTIAL evidence; it is not a business PASS.
~~~

## Code Review Graph Result

- MCP incremental build and detect calls both failed with `Transport closed`; this step is not recorded as MCP PASS.
- Local `code-review-graph` CLI fallback updated from implementation baseline `0935933c` and analyzed 74 changed files. It reported risk 0.00 but mapped 0 changed functions/flows, so this output is treated as limited coverage, not proof of no impact.
- Compensating evidence: every implementation task received independent specification and quality review; discovered transaction, MyBatis cache, writer rollback and YAML guard defects were fixed and re-reviewed before migration.

## Harness Governance

- `TASK_GATE=PASS`.
- `REPOSITORY_HEALTH=PARTIAL`: pre-existing `harness/reports` direct-file count is 23, above the target 20; this task did not worsen that root count.

## Content Maintenance Result

~~~text
Content maintenance skipped by -ContentMaintenance off.
~~~

## Remote Deploy Result

~~~text
remote not deployed
~~~

## Activation Boundary

- Local SHADOW activation was not authorized and was not performed.
- ENFORCE is absent from checked-in runtime profiles.
- No Controller, Aspect or business request consumer uses the new authorization runtime.
- Permission/role/domain-scope seed data remains empty; the business permission matrix is not approved.

## Retro Summary

Full backend: 3419 tests, 0 failures, 0 errors, 3 skipped; package PASS. Local migration ran twice and catalog=4|1|0|0|0|0|0. LEGACY health and unauthenticated 401 PASS; Redis authz snapshot keys=0. Actual P0 preflight ran and admin login returned HTTP 401 after 5 attempts, so token/business validation is BLOCKED_AUTH, not PASS. code-review-graph transport closed after two attempts. SHADOW and remote deployment were not authorized.

## Conclusion

PARTIAL

## Residual Risk

- Authenticated token-version, old access-token rejection and old refresh-token rejection remain `BLOCKED_AUTH` until a valid real-pre account and mutation window are provided.
- SHADOW remains PENDING separate authorization; ENFORCE remains prohibited.
- The role-permission and domain-scope business matrix remains unapproved and unseeded.
- Group membership mutation still has a per-user version-update/N+1 cost; request size and deduplication semantics require a business/API decision.
- Concurrent overlapping role version updates, PostgreSQL BIGINT overflow and real Redis failure behavior remain pre-ENFORCE hardening evidence gaps.
- code-review-graph MCP was unavailable and the CLI fallback had limited symbol mapping.
- Remote deployment and remote migration remain UNKNOWN / not performed.

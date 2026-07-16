# Evidence Report

## Metadata

- Time: 2026-07-16 16:59:55 +08:00
- Environment: real-pre
- Scope: full
- Branch: feature/auth-system
- Commit: 90edfa7a
- Owned worktree: clean
- Deploy remote: true

## Owned Files

~~~text
harness/reports/current/latest-role-aware-link-attribution.md
System.Object[]
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
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED         STATUS                   PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    2 minutes ago   Up 2 minutes (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   2 minutes ago   Up 2 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   2 minutes ago   Up 2 minutes (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      25 hours ago    Up 25 hours (healthy)    6379/tcp
NAMES                             STATUS                   PORTS
saas-active-frontend-real-pre-1   Up 2 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 2 minutes (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 2 minutes (healthy)   5432/tcp
saas-active-redis-real-pre-1      Up 25 hours (healthy)    6379/tcp
campus_frontend                   Up 2 days                0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 2 days (healthy)      0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 2 days (healthy)      0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 2 days (unhealthy)    0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
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
Remote deploy: PASS
~~~

## Remote Attribution Verification

~~~text
Read-only verification at 2026-07-16 17:02 +08:00:
- Remote runtime: feature/auth-system@90edfa7a; backend and frontend health checks PASS.
- User 壮云 (1c34b680-30b2-41ec-bdc7-2dde1f37e786) currently has only active role channel_staff.
- Promotion link 1df7d10a-50cc-4306-b773-81b71513bb00 and native pick_source mapping v.Ovyq8S were created by 壮云, but both attribution_owner_type values are NULL because they predate this release.
- Historical order 6927995582750227729 still has recruiter=招商组长测试 and recruiter_attribution_source=activity_owner; channel attribution is empty/unattributed.
- Under the deployed policy, channel_staff resolves as CHANNEL. That would give 壮云 channel attribution while still falling back to the activity recruiter for the recruiter dimension; it does not satisfy the requested recruiting-person attribution.
- No role change, mapping reconciliation, or order replay was executed.
~~~

## Retro Summary

Actionable: agent-do rendered the multi-file OwnedFiles argument as System.Object[] in this report. Add a PowerShell regression test that verifies semicolon-joined paths are preserved in evidence output before the next Harness change.

## Conclusion

PARTIAL

## Residual Risk

- The requested historic order is not yet attributed to 壮云 in the recruiter dimension.
- Correcting it requires an explicit business authorization to replace 壮云's channel_staff role with the intended unique recruiting role, then run the audited mapping reconciliation and order replay.
- Harness limit check is BLOCKED by the pre-existing untracked root report harness/reports/evidence-20260713-131800.md in the release worktree; it was not created, modified, moved, or deleted by this task.
- Items marked as not collected are not proof of success.

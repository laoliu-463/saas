# Evidence Report

## Metadata

- Time: 2026-07-18 16:48:30 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/merge-cooperation-workbench-actions
- Commit: 672baed6
- Owned worktree: clean
- Deploy remote: true

## Owned Files

~~~text
harness/reports/current/latest-cooperation-workbench-main-merge-deploy.md
~~~

## Owned Git Status

~~~text
(clean)
~~~

## Build Result

~~~text
PASS: remote Maven clean package -DskipTests; remote Compose frontend production build. Local merge regression: 183 targeted backend tests PASS; 23 targeted frontend tests PASS and frontend production build PASS before latest upstream sync. Final local backend re-run is PENDING because the host JVM failed native-memory allocation during javac.
~~~

## Docker Status

~~~text
not collected
not collected
~~~

## Health Check Result

~~~text
PASS: remote backend health status UP; remote frontend health status ok; backend, frontend, PostgreSQL and Redis all healthy.
~~~

## Business Validation Result

~~~text
PARTIAL: cooperation action flows have targeted test coverage. Real-pre external P0 remains BLOCKED_AUTH because Douyin token readiness is absent; no full external business-flow PASS is claimed.
~~~

## Content Maintenance Result

~~~text
No content maintenance. Aggregate migration script reached a legacy relative include failure after the required private-note DDL; required sample_private_note table and uk_sample_private_note_owner index were independently verified.
~~~

## Remote Deploy Result

~~~text
PASS: remote source and backend/frontend images deployed at 672baed6850c34ca060f737102ff286f6ddabbaf; remote health probes passed.
~~~

## Retro Summary

Action: make the aggregate migration runner resolve legacy relative SQL includes before execution; verification: a clean run returns zero missing-file errors. Deployment guard: continue comparing source HEAD, container image tags, and required schema objects.

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.

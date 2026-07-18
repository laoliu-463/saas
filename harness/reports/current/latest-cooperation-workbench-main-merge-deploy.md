# Evidence Report

## Metadata

- Time: 2026-07-18 16:37:22 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/merge-cooperation-workbench-actions
- Commit: 741c969d
- Owned worktree: clean
- Deploy remote: false

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
Post-merge targeted backend test: PASS (183 tests) before latest upstream sync; final re-run blocked by host native-memory allocation during javac, not a compilation diagnostic. Frontend targeted tests: PASS (23 tests) and production build: PASS before latest upstream sync.
~~~

## Docker Status

~~~text
not collected
not collected
~~~

## Health Check Result

~~~text
Not rerun for the final merged commit because the host JVM could not complete the final local build; prior local real-pre health is not used as final-merge proof.
~~~

## Business Validation Result

~~~text
PARTIAL: source action flows covered by targeted tests; complete real-pre external verification is BLOCKED_AUTH because Douyin token readiness is absent.
~~~

## Content Maintenance Result

~~~text
No content maintenance required.
~~~

## Remote Deploy Result

~~~text
BLOCKED: expected main commit 741c969d is on origin/feature/auth-system; Gitee and server remain at a9266601. deploy-remote.ps1 would pull the old Gitee commit, so it was not executed.
~~~

## Retro Summary

Action: require the deployment mirror to reach the intended origin commit before invoking the fixed remote deploy script; verification: compare origin, Gitee, and server HEAD hashes.

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.

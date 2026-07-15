# Evidence Report

## Metadata

- Time: 2026-07-15 15:58:56 +08:00
- Environment: real-pre
- Scope: frontend
- Branch: codex/fix-channel-product-access-20260715
- Commit: 2bea89aa
- Owned worktree: clean
- Deploy remote: true

## Owned Files

~~~text
frontend/src/views/profile/UserProfile.test.ts
frontend/src/views/profile/UserProfile.vue
~~~

## Owned Git Status

~~~text
(clean)
~~~

## Build Result

~~~text
PASS: npm --prefix frontend run build; backend Maven package PASS for local Compose dependency.
~~~

## Docker Status

~~~text
not collected
not collected
~~~

## Health Check Result

~~~text
PASS: local backend /api/system/health=UP and local frontend /healthz=ok before cleanup; remote backend /api/system/health=UP; remote frontend /healthz=ok; remote Compose 4/4 healthy.
~~~

## Business Validation Result

~~~text
PARTIAL: focused profile/auth/router regression 5 files, 49/49 tests PASS; UserProfile activation flow 3/3 PASS. Remote account is active channel_staff with authz_version=2; operation_log proves login SUCCESS at 2026-07-15 07:51:00 UTC and logout SUCCESS at 07:51:08 UTC. No product-library API request was recorded between login and logout, so actual product-library page access remains PENDING and is not claimed as PASS.
~~~

## Content Maintenance Result

~~~text
Not applicable.
~~~

## Remote Deploy Result

~~~text
PASS: deployed exact source commit 1bd75f081695070ec681b36f295d4baeabe9cc72; remote worktree clean; canonical env link and container provenance verified; frontend artifact contains forced-relogin hotfix; true ERROR/FATAL logs 0.
~~~

## Retro Summary

Root cause: activation password change updated database state but retained the old pendingActivation JWT in the browser, so business APIs remained blocked. Governance action implemented: clear local authentication and force navigation to /login after successful activation password change. The affected user has since completed one successful login and immediate logout; remaining validation requires keeping the new session open and navigating to /product because no product request was observed.

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.

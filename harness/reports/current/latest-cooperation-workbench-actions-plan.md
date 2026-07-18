# Evidence Report

## Metadata

- Time: 2026-07-18 22:42:14 +08:00
- Environment: local `real-pre` + remote `real-pre`
- Scope: cooperation workbench actions; remote frontend/backend deployment
- Branch: `codex/ddd-user-role-application`
- Deployed code commit: `7efb0f8e577b00a3abf14efb7ab0a0fa78426acb`
- Remote deployment requested: yes
- Database backup requested: no (user override during deployment)

## Change Evidence

- Cooperation action implementation and tests were committed in `7efb0f8e`.
- The operation column renders seven vertical actions: approve, reject, edit, progress,
  copy link, copy order, and private note.
- The empty action-column root cause was the child component using `n-button` and
  `n-tooltip` without importing `NButton` and `NTooltip`.
- `CooperationActionColumn.test.ts` now mounts the real Naive UI components and
  asserts that no unresolved `n-tooltip` component remains.

## Local Build And Test

- Backend package build: PASS.
- Frontend production build: PASS.
- Frontend full suite: PASS, 105 files / 758 tests.
- Backend cooperation-action targeted tests: PASS.
- Backend full suite: PARTIAL, 3296 passed / 3 skipped / 1 existing
  `LargeServiceDebtRedlineTest` failure (`3665 > 3613`).
- Local authenticated `/sample` browser validation: PASS; a pending-audit row showed
  all seven vertical enabled actions and unresolved `n-tooltip` count was zero.
- Local backend health and frontend health: PASS.

## Git And Remote Alignment

- GitHub branch push: PASS.
- Gitee deployment mirror push for `7efb0f8e`: PASS.
- Remote checkout: `7efb0f8e577b00a3abf14efb7ab0a0fa78426acb`.
- Remote `IMAGE_TAG`: `7efb0f8e577b00a3abf14efb7ab0a0fa78426acb`.
- Backend image revision label: `7efb0f8e577b00a3abf14efb7ab0a0fa78426acb`.
- Frontend image revision label: `7efb0f8e577b00a3abf14efb7ab0a0fa78426acb`.

## Remote Runtime Verification

- Backend container: healthy; `GET /api/system/health` returned `{"status":"UP"}`.
- Frontend container: healthy; `GET /healthz` returned `ok`.
- Frontend artifact contains `CooperationWorkbench-78-nbknH.js` and the labels
  `复制链接` / `复制订单`.
- PostgreSQL container: healthy; original named volume
  `saas-active_postgres_real_pre_data` is mounted.
- Redis container: healthy; original named volume
  `saas-active_redis_real_pre_data` is mounted.
- Read-only data probe: `sample_request=9`, active `sys_user=7`, and
  `sample_private_note` exists.
- Remote authenticated action API smoke: PENDING. One configured-admin login probe
  returned 401; no repeated login attempts were made to avoid lockout risk.

## Database Boundary And Override

- The standard remote script had already created
  `/opt/saas/backups/remote-20260718-223126` before the user changed the scope.
- The deployment chain was stopped and that exact backup directory (including the
  2.87 GB dump) was removed; the path is now absent.
- The standard script also recreated PostgreSQL and Redis containers before it was
  stopped. Their persistent volumes were not deleted or replaced, and both services
  are healthy with existing data readable.
- Flyway history after the stop contains only the existing successful versions
  `20260717`, `20260718.001`, and `20260718.002`; no new migration version was added
  by this deployment.
- No further database or Redis operation was performed after the override; final
  deployment/verification was limited to the frontend and backend runtime.

## Rollback Evidence

- Previous backend image digest:
  `sha256:c5d7a157999716b3bdae8c859c6cd19beefee5dcfb6238c9f561b02b9ecdfaef`.
- Previous frontend image digest:
  `sha256:d604b72e35c6b8c37fdb7e1418eeb36fed92129e8eb876b51c7f80ff3da25c66`.
- Rollback must restore the prior image references only; PostgreSQL/Redis volumes
  must remain untouched.

## Retro

- Root cause: the action column was moved into a child component, but the child did
  not own its Naive UI imports; parent imports do not register components for a
  child `<script setup>` scope.
- Prevention: component tests render the actual UI components and assert against
  unresolved custom components, not only stubbed click behavior.
- Deployment learning: the fixed remote script couples backup, migration, dependency
  recreation, and application rollout. A separately reviewed frontend/backend-only
  entry point is needed before a future no-database deployment.

## Conclusion

`PARTIAL`

The requested frontend/backend images are deployed and healthy, and the remote
frontend artifact contains the operation bar. The result is not marked `PASS`
because the remote authenticated action API was not verified and the standard
script had recreated dependency containers before the scope override, although
their persistent data remained available.

## Residual Risk

- Remote authenticated click-path validation still requires a known-valid operator
  session.
- The existing backend debt-redline failure remains unrelated to this change.

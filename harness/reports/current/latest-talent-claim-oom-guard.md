# Evidence Report — Talent Claim OOM Guard

## Metadata

- Time: 2026-07-20 (Asia/Shanghai)
- Environment: remote `real-pre`
- Application release: `release/real-pre` @ `4b426449fe520cf5f056b8f69fe322943c07e3d3`
- Included backend fix: `568d1648b475ac525e522f65f50efa2ff868c438` (claim release OOM guard)
- Deployment method: user explicitly directed a manual release after Jenkins checkout delays.

## Verified Code and CI

- `4b426449` contains the OOM fix; `git diff 568d1648..4b426449 -- backend` is empty.
- Targeted local governance test: `release-queue-governance.Tests.ps1` 11/11 passed.
- PR #193 merged the Jenkins tracking-ref fix to `main` (`844636f5`).
- PR #194 promoted it to `release/real-pre` (`4b426449`).
- For both PRs, two backend full suites, two frontend build suites, and two governance suites passed.
- Historical backend verification retained: full suite 3,322 tests, 0 failures, 0 errors, 3 skipped.

## Jenkins Recovery Evidence

- #19 checked out `0f870696`, then failed because a release-only refspec left `origin/main` absent while Preflight read that ref; no deployment occurred.
- Fixed with `git fetch --no-tags origin +refs/heads/main:refs/remotes/origin/main` and a matching governance assertion.
- #20 reached the corrected Preflight but stopped at the explicit `DEPLOY_REAL_PRE=false` approval gate; no deployment occurred.
- #21 was cancelled before deployment when the user switched to manual release; no concurrent Jenkins deployment ran.

## Manual Release Evidence

- Source archive from exact target commit SHA-256: `ea89cccd14079f6e94038630153b85b572891e17ea2c3c5fd41cacbab3289183`; server checksum matched.
- Reused JAR SHA-256: `6491440b4eabb5a76ae9fa394b9efb1d1ffe280c9f3bf2e37c7f272dcfb94b53`; source-equivalence check above justifies reuse and server checksum matched.
- Built images: `colonel-saas/backend:4b426449...` and `colonel-saas/frontend:4b426449...`; OCI revision labels match the full target SHA.
- Previous images (`02fcc18a83b85137954f9bd845344c296ae34b05`) were recorded before replacement for rollback.
- No database migration, destructive DDL, volume deletion, or database reset was executed.

## Remote Health and Business Validation

- Backend container: `running (healthy)`, target SHA image.
- Frontend container: `running (healthy)`, target SHA image.
- `GET /api/actuator/health/readiness`: `{"status":"UP"}`.
- `GET /api/system/health`: `status=UP`, `gitSha=4b426449...`.
- `GET /healthz`: `ok`; `GET /version.json`: `gitSha=4b426449...`.
- `APP_SCHEDULING_ENABLED=true`; schedulers were restored after backend readiness.
- User confirmed the functional group-test items before the direct release; this run additionally verified running revision and service health.

## Gitee Check and Residual Risk

- Gitee has no `main` or `release/real-pre`; only `feature/ddd/DDD-VERIFY-001` @ `26cf8764...` was found, so it cannot be used as the current release source.
- Jenkins still reads GitHub SSH. Moving later *test* releases to Gitee requires a controlled mirror, protected branches, and SHA-consistency gate; a direct switch to the stale branch is unsafe.
- Manual release was user-directed and healthy, but it has no Jenkins PASS artifact.

## Retro and Conclusion

- Improve release-only checkouts by explicitly mapping every remote-tracking ref used later in Preflight; this is now fixed and regression-checked.
- Improve remote build reliability with a controlled GitHub→Gitee mirror and warmed Maven cache; validate branch and SHA parity before use.
- Conclusion: `PARTIAL` — application deployment and remote health are PASS; CI governance remains PARTIAL because the final release intentionally bypassed Jenkins, and Gitee is not yet a valid test source.

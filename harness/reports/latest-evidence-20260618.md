# Evidence Report - 2026-06-18 CI/CD And Deploy Validation

- Time: 2026-06-18 14:40:35 +08:00
- Updated: 2026-06-18 14:54:16 +08:00
- Env: local real-pre and remote real-pre server
- Branch: feature/ddd/DDD-VERIFY-001
- CI/CD commit: fd15f448
- Remote deploy validation: attempted, blocked by env guard before rebuild
- Worktree clean: no
- Worktree note: unrelated existing changes remain in frontend runtime files and harness report archive moves; they were not included as task evidence.

## Scope

- Implemented Jenkins pipeline as CI-first with explicit manual CD gate.
- Updated Jenkins deployment documentation.
- Fixed pnpm workspace metadata so Jenkins frontend stages can run.
- Repaired stale backend tests that blocked full CI test execution.

## Build And Test

- `git diff --check -- <task files>`: PASS
- `mvn -f backend/pom.xml test`: PASS, 2225 tests, 0 failures, 0 errors, 3 skipped
- `mvn -f backend/pom.xml -DskipTests package`: PASS
- `npx --yes pnpm@9 --version`: PASS, 9.15.9
- `npx --yes pnpm@9 install --frozen-lockfile`: PASS
- `npx --yes pnpm@9 test`: PASS, 84 files, 640 tests
- `npx --yes pnpm@9 build`: PASS
- Build warning: Vite still reports chunks larger than 500 kB; this is an existing warning, not a failed gate.

## Docker And Health

- `docker compose --env-file .env.real-pre --project-name saas-active -f docker-compose.real-pre.yml config --quiet`: PASS
- Restarted local services: `backend-real-pre`, `frontend-real-pre`
- Docker status after restart: backend, frontend, PostgreSQL and Redis all healthy
- `verify-local.ps1 -Env real-pre -Scope full`: PASS
  - Backend: `http://127.0.0.1:8081/api/system/health` -> 200, `{"status":"UP"}`
  - Frontend: `http://127.0.0.1:3001/healthz` -> 200

## Safety And Harness

- `safety-check.ps1 -Env real-pre -Scope full`: PASS
- Secret value disclosure: none; safety output only confirmed presence or missing state.
- `check-harness-limits.ps1`: PASS
- Harness directory limit after report update: within limits.

## CI/CD Verification

- Jenkinsfile now defaults to CI-only.
- real-pre deployment only runs when `DEPLOY_REAL_PRE=true`.
- `DEPLOY_REAL_PRE=true` requires explicit `DEPLOY_BRANCH`.
- real promotion write requires `CONFIRM_REAL_PROMOTION_WRITE=true` when both real promotion write switches are enabled.
- CI evidence artifact now records pipeline parameters and target ref.

## Remote Deploy Validation

- Pushed `fd15f448` to `gitee/feature/ddd/DDD-VERIFY-001` so the server deployment source can fast-forward to the same CI/CD commit.
- Remote server `/opt/saas/app` fast-forwarded from `8d1119a` to `fd15f448` during `scripts/deploy-real-pre.sh`.
- Deployment command used `REAL_PROMOTION_WRITE_CONFIRMED=true` because real promotion write double switches are enabled.
- Deployment result: BLOCKED before database backup, migrations, image rebuild and container update.
- Blocker: `LOGISTICS_KD100_CALLBACK_URL` is empty or still a placeholder while `LOGISTICS_KD100_SUBSCRIBE_ENABLED=true`.
- Current remote running stack after the blocked deploy remains healthy:
  - Backend health: `http://127.0.0.1:8081/api/system/health` -> UP
  - Frontend health: `http://127.0.0.1:3001/healthz` -> ok
  - Docker services: backend, frontend, PostgreSQL and Redis healthy
- Remote preflight on the current running stack: PASS.
  - Evidence: `/opt/saas/app/runtime/qa/out/real-pre-preflight-20260618-145305`
  - Checks passed: frontend, backend health, admin login, env guard, Douyin token readiness, schema readiness, reusable promotion mapping, cleanup plan.

## Script Hardening

- `scripts/deploy-real-pre.sh`: moved real-pre env validation before `git pull --ff-only`; future config failures should stop before mutating server source checkout.
- `scripts/real-pre-startup-check.sh`: added required checks for `LOGISTICS_KD100_CALLBACK_URL` and `LOGISTICS_KD100_CALLBACK_SALT` when Kuaidi100 subscription is enabled.
- Local validation:
  - `bash -n scripts/deploy-real-pre.sh scripts/real-pre-startup-check.sh`: PASS
  - `bash scripts/real-pre-startup-check.sh .env.real-pre`: PASS
  - `mvn -f backend/pom.xml -DskipTests package`: PASS
  - `npx --yes pnpm@9 build`: PASS
  - Note: JaCoCo reported execution data mismatch for `SysUserService`; current worktree contains unrelated uncommitted changes in that file.
- Remote validation after pushing hardening commit:
  - Server source fast-forwarded to `97a1b7a`.
  - `bash scripts/real-pre-startup-check.sh /opt/saas/env/.env.real-pre`: FAIL as expected before deploy.
  - Expected blocker: `LOGISTICS_KD100_CALLBACK_URL` missing while Kuaidi100 subscription is enabled.

## Not Executed

- Jenkins controller execution: not executed; no Jenkins controller/runtime was available in this workspace.
- Remote image rebuild/container update: not executed because env guard blocked deployment before build/start.
- real-pre roles/P0 E2E after candidate rebuild: not executed because candidate rebuild did not happen.
- `mvn clean test`: BLOCKED locally because Windows Java language service held `backend/target/classes`; full `mvn test` recompiled and passed afterward.

## Conclusion

PARTIAL

The CI/CD implementation and local equivalent build/test/deploy-gate checks are passing. Actual remote deployment verification is blocked by missing Kuaidi100 callback configuration, so this is not yet production-applicable.

## Remaining Risk

- Jenkins Pipeline syntax has not been validated by a live Jenkins controller.
- CD behavior is guarded by parameters, but the real deployment stage still needs one successful controlled server run after `LOGISTICS_KD100_CALLBACK_URL` is configured.
- Remote OAuth callback is still IP/HTTP based in observed env summary; production-domain readiness requires explicit HTTPS domain validation.
- Existing unrelated worktree changes must be kept out of this task commit unless the user explicitly includes them.

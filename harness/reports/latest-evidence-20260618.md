# Evidence Report - 2026-06-18 CI/CD Pipeline

- Time: 2026-06-18 14:40:35 +08:00
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Commit: b0130b8b
- Remote deploy: no, not requested by user
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

## Not Executed

- Jenkins controller execution: not executed; no Jenkins controller/runtime was available in this workspace.
- Remote real-pre deployment: not executed; user did not explicitly request remote deployment.
- `mvn clean test`: BLOCKED locally because Windows Java language service held `backend/target/classes`; full `mvn test` recompiled and passed afterward.

## Conclusion

PARTIAL

The CI/CD implementation and local equivalent build/test/deploy-gate checks are passing. Final end-to-end PASS requires running the Jenkins job on the target Jenkins controller and preserving its build artifacts.

## Remaining Risk

- Jenkins Pipeline syntax has not been validated by a live Jenkins controller.
- CD behavior is guarded by parameters, but the real deployment stage still needs one controlled Jenkins run before declaring Jenkins CD fully verified.
- Existing unrelated worktree changes must be kept out of this task commit unless the user explicitly includes them.

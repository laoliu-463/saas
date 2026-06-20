# Evidence Report - GIT-BATCH-5 Frontend Auth Store

- Time: 2026-06-21 01:15:00 +08:00
- Env: local real-pre workspace
- Branch: feature/ddd/DDD-VERIFY-001
- Base commit: 8eca9c34
- Scope: frontend/auth-request
- Remote deploy: no
- Business data write: no
- Conclusion: PASS for local frontend validation; residual CSP font console error noted

## Scope

- `frontend/src/utils/request.ts`: read access and refresh tokens from Pinia `authStore` instead of direct `localStorage` access.
- `frontend/src/utils/request.test.ts`: initialize Pinia in request helper tests.
- `frontend/src/views/Login.vue`: cap password input at 128 characters.

## Explicitly Excluded

- Backend DDD changes and backend U-7 addendum reports currently dirty in the workspace.
- DDD decision drafts and `UBIQUITOUS_LANGUAGE.md`.

## Evidence Collected

- Targeted test: `vitest run src/utils/request.test.ts --maxWorkers=1 --minWorkers=1` => 1 file, 30 tests passed.
- Full frontend test: `vitest run --maxWorkers=1 --minWorkers=1 --reporter=dot` => 85 files, 642 tests passed.
- Typecheck: `pnpm --dir frontend typecheck` => PASS.
- Local production build: `pnpm --dir frontend build` => PASS.
- Docker rebuild/restart: `docker compose -f docker-compose.real-pre.yml up -d --build frontend-real-pre` => PASS.
- Compose side effect: `backend-real-pre` was also recreated by compose dependency/build resolution; post-check showed backend healthy.
- Docker status after restart: postgres, redis, backend and frontend all healthy.
- Backend health: `GET http://127.0.0.1:8081/api/system/health` => 200, `{"status":"UP"}`.
- Frontend health: `GET http://127.0.0.1:3001/healthz` => 200.
- Frontend `/login`: HTTP 200.
- Playwright login check: username input visible, password input visible, password internal `maxlength` is `128`.

## Residual Risk

- Playwright observed one console error caused by Google Fonts being blocked by the current CSP. This appears unrelated to this change, but browser console is not clean.
- Vite build still reports existing chunk-size warnings for large vendor/application chunks.
- Backend container was recreated during frontend compose rebuild; backend source changes were not staged in this batch.

## Closeout Verification

- Staged files: 5 paths.
- Staged out-of-scope files: 0.
- `powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\check-harness-limits.ps1`: PASS.
- `powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\safety-check.ps1 -Env real-pre -Scope docs -DryRun`: PASS.
- `git diff --cached --check`: PASS after removing trailing blank EOF lines in this evidence/retro pair.

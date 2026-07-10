# Evidence Report - 2026-07-09 F-10/F-11

## Scope

- Environment: real-pre
- Branch: codex/ddd-user-role-application
- HEAD: 6598d623
- Cards: F-10 frontend hardcoded business rule scan; F-11 frontend build/Vitest/E2E/screenshot evidence; G-4 admin/group/self permission regression evidence; A-9/A-11 dashboard role smoke evidence
- Raw agent-do report archived: `harness/archive/by-date/2026-07-09/f10-frontend-scan/evidence-20260709-233224.zip`
- Preflight report: `runtime/qa/out/real-pre-preflight-20260709-233222/report.md`
- Screenshot evidence: `runtime/qa/out/f11-frontend-evidence-20260709-235200/login-fullpage-after-5s.png`
- Role page smoke evidence: `runtime/qa/out/g4-role-page-smoke-20260709-235900/`
- Remote deploy: false

## Code Evidence

- Added `frontend/src/architecture/frontend-business-rule-boundary.test.ts`.
- The scan excludes tests and `.d.ts` files, then inspects production `frontend/src/**/*.ts|vue`.
- Hard-fail checks are empty: no `request/axios/fetch` direct third-party HTTP calls, no `buyin.*` SDK method call, no SQL/JdbcTemplate persistence access in frontend production code.
- Inventory checks now explicitly list permission/action hotspots: `hasAccess`, `ROLE_CODES`, `roleCodes.includes`, `canExport*`, `getProductActions`.
- Inventory checks now explicitly list status and money-display hotspots, including attribution/sample/product status codes and `commissionRate/serviceFeeRate/grossProfit/orderAmount` display fields.
- Money arithmetic is limited to display formatting and table sorting helpers.

## Matrix / Status

- `docs/ddd-completion-evidence-matrix.md`: F-10 moved to DONE.
- `docs/ddd-completion-evidence-matrix.md`: F-11 moved from TODO to PARTIAL.
- `docs/ddd-completion-evidence-matrix.md`: G-4 moved from TODO to PARTIAL.
- `docs/ddd-completion-evidence-matrix.md`: A-9 and A-11 moved from TODO to PARTIAL.
- Latest acceptance matrix: DONE 131 / PARTIAL 37 / TODO 4 / BLOCKED 6 / total 178.
- Whitelists: cross-domain mapper active 0; architecture redline active 0.
- Wide DDD architecture: 361 tests / 0 failures / 0 errors / 1 skipped.

## Verification

| Command | Result |
| --- | --- |
| code-review-graph `detect_changes` | REVIEWED; global dirty scope high, so F-10 stayed narrow |
| `npm --prefix frontend run test -- src/architecture/frontend-business-rule-boundary.test.ts` | PASS; 4 tests / 0 failures |
| `npm --prefix frontend run typecheck:test` | FAIL; existing test type debt, not caused by F-10 |
| `npm --prefix frontend run test` | TIMEOUT after 18 minutes; no full Vitest PASS evidence |
| `npm --prefix frontend run build` | PASS |
| `npx playwright screenshot --full-page --wait-for-timeout 5000 http://127.0.0.1:3001/login ...` | PASS; login screenshot captured and visually inspected |
| `node runtime/qa/page-role-smoke.cjs ... admin,biz_leader` | FAIL/PARTIAL; login and mustAccess routes worked, but text/fallback/CSP checks failed |
| `check-ddd-acceptance.ps1 -RequireRedlineZero` | PASS |
| `git diff --check` | PASS; CRLF/LF warnings only |
| `check-harness-limits.ps1` | PASS |
| `agent-do.ps1 -Env real-pre -Scope frontend` | PARTIAL/FAIL; frontend build, Docker restart and local health PASS; business preflight BLOCKED_AUTH |

## Runtime Evidence

- `frontend-real-pre`: rebuilt/recreated; final Docker status healthy.
- Frontend health: `http://127.0.0.1:3001/healthz` returned 200.
- Backend health: `http://127.0.0.1:8081/api/system/health` returned `{"status":"UP"}`.
- real-pre preflight status: BLOCKED; `canRunBusinessFlows=false`.
- Douyin token readiness: `hasAccessToken=false`, `hasRefreshToken=true`, `reauthorizeRequired=false`.

## Residual Risk

- F-10 proves scan/report/contract coverage only; it does not prove frontend full Vitest, E2E, screenshots, or real account permission differences.
- F-11 is PARTIAL: build and login screenshot evidence exist, but full Vitest, test typecheck, authenticated pages and business E2E are not PASS.
- `typecheck:test` still has existing debt in activityProduct/ProductSelectionCard/DouyinIntegration/QuickSample/SampleCreate tests and product index timeout typing.
- G-4 has only `admin` and `biz_leader` page-role coverage; `self/staff` and SQL/API row-level data-scope comparison remain missing.
- G-4 role smoke currently fails on stale sidebar text expectations, Google Fonts CSP console errors, and `/system` denial fallback expecting `/dashboard|/orders` while actual final path is `/data`.
- A-9/A-11 dashboard evidence is only route-level role smoke; no self account, no SQL/API dashboard reconciliation, and no authenticated dashboard screenshot PASS yet.
- `npm ci` reported 6 vulnerabilities during agent-do; this was not changed by F-10 and needs a separate dependency audit slice.
- Real-pre business flow remains blocked by Douyin access token readiness, so no full business E2E PASS is claimed.

## Conclusion

- F-10 is DONE as a local static scan/report contract.
- F-11 is PARTIAL with current build/screenshot/preflight/Vitest evidence.
- G-4 is PARTIAL with current role-page smoke evidence; it is not a permission regression PASS.
- A-9/A-11 are PARTIAL with current dashboard route smoke evidence; they are not dashboard E2E PASS.
- Build, container restart, frontend health, backend health, DDD acceptance, diff check and harness limits passed.
- Full real-pre business validation remains BLOCKED_AUTH, not PASS.
- Retro: no harness upgrade needed; this slice reused the existing frontend test and evidence workflow.

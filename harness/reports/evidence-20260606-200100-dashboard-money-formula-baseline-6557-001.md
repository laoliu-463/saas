# Evidence Report - dashboard-money-formula-baseline-6557-001

## Metadata

- Time: 2026-06-06 20:01:00 +08:00
- Environment: local real-pre
- Branch: feature/auth-system
- Base commit: 696cc902518ef981287c639a32b0a094228b8c59
- Remote deploy: false
- Worktree: dirty; existing staged/dirty files present before this task
- Conclusion: PARTIAL

## Build

- Backend targeted tests: PASS, 84 tests, 0 failures/errors.
- Backend package: PASS, `mvn -f backend/pom.xml -DskipTests package`.
- Frontend tests: PASS, `npm run test -- index.test.ts OrderDetailTab.test.ts`, 51 tests.
- Frontend typecheck: PASS.
- Frontend build: PASS, existing chunk-size warning.

## Restart And Health

- `agent-do.ps1 -Env real-pre -Scope full` executed without remote deploy.
- Backend image rebuilt and container recreated.
- Frontend image rebuilt and container recreated.
- Backend health: PASS, `GET http://127.0.0.1:8081/api/system/health` returned 200 `{"status":"UP"}`.
- Frontend health: PASS, `GET http://127.0.0.1:3001/healthz` returned 200.
- Compose status after verification: backend, frontend, postgres and redis all healthy.

## Business Validation

### SQL Snapshot

- `colonelsettlement_order` for `order_create_time` on 2026-06-06: 6598 orders, 14239383 cents, estimate service fee 218217 cents, estimate tech fee 21936 cents.
- `performance_records` valid for `order_create_time` on 2026-06-06: 6131 records, 13193503 cents, service fee 203340 cents, tech fee 20442 cents, expense 0 cents, profit 182898 cents, recruiter 27386 cents, channel 27386 cents, gross 128126 cents.
- `system_config`: business default ratio 0.15, channel default ratio 0.15.

### API Snapshot

- `/api/data/orders/summary`: today 6598 orders, 142393.83 yuan, service fee income 2182.17 yuan, tech service fee 219.36 yuan, service fee expense 133.83 yuan, service fee profit 1828.98 yuan, gross profit 1240.16 yuan.
- `/api/data/orders/summary?timeField=payTime`: returned business error `非法时间字段: payTime`; this endpoint accepts default/createTime path for the current validation.
- `/api/dashboard/metrics`: performance_records estimate track returns live valid-order metrics; browser smoke captured 6137 orders and 132048.33 yuan during a later live-sync window.
- `/api/performance/summary`: estimate track returns serviceFeeExpense 0 because DB has no independent expense source.

### Browser Smoke

- Tooling: Browser plugin was not exposed by `tool_search`; fallback used local Playwright.
- URL: `http://127.0.0.1:3001/data`.
- Desktop screenshot: `runtime/qa/out/dashboard-money-formula-baseline-6557-001-20260606-120023/desktop-data.png`.
- Mobile screenshot: `runtime/qa/out/dashboard-money-formula-baseline-6557-001-20260606-120023/mobile-data.png`.
- Network failures: 0.
- Console errors: 2 CSP errors for Google Fonts stylesheet blocked by `style-src 'self' 'unsafe-inline'`.

## Git Gate

- `agent-do` generated `harness/reports/evidence-20260606-195517.md` and `harness/reports/evidence-20260606-195538.md`.
- Final git safe step failed: `git diff --cached --check` reported trailing whitespace in pre-existing staged files, including `build-docker.txt`, `build-docker3.txt`, and `harness/reports/order-sync-freshness-optimize-001-20260606-164500.md`.
- No commit and no push were performed in this task.

## Residual Risk

- User baseline 6557/141508.04 is not reproducible on current live real-pre data.
- Live upstream sync continues changing today counts, so future snapshots may differ minute to minute.
- Service fee expense cannot be proven against user target without a confirmed upstream expense field or business rule.
- Commission/gross profit cannot match any non-15% target until business confirms and changes the commission configuration/rules.

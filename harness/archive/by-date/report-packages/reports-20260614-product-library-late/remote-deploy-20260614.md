# FINAL-GLOBAL-CHECK-AND-REMOTE-DEPLOY-001 Evidence

- time: 2026-06-14 15:40 Asia/Shanghai
- environment: local real-pre + remote real-pre
- remote host: VM-0-12-ubuntu
- remote app dir: /opt/saas/app
- branch: feature/ddd/DDD-VERIFY-001
- deployed commit: 6c04f6b623a540bf153387dc87b2b26b625a5b0a
- conclusion: PARTIAL

## Scope

Final global check and remote deployment were executed after user confirmation that remote commission defaults must be 招商 10% + 渠道 10%.

This report does not mark business validation PASS because remote historical order facts still differ from the local/baseline service-fee-expense evidence, and full P0 E2E ended PENDING.

## Local Checks

- git initial state: clean on feature/ddd/DDD-VERIFY-001.
- backend tests: `mvn -f backend/pom.xml test` PASS, 2191 tests, 0 failures, 0 errors, 3 skipped.
- frontend tests: `npm --prefix frontend run test` PASS, 84 files, 640 tests.
- full harness command: `agent-do.ps1 -Env real-pre -Scope full` PASS.
- local build: backend package PASS; frontend build PASS.
- local restart/health: backend/frontend/postgres/redis healthy; backend `/api/system/health` UP; frontend `/healthz` ok.
- local business preflight: `npm run e2e:real-pre:p0:preflight` PASS.
- known local risk: frontend build reported npm audit 6 vulnerabilities (4 high, 2 critical).

## Remote Backup

- database backup: `/opt/saas/backups/db/saas_20260614_151025.dump`
- database backup size: 117005084 bytes
- env backup dir: `/opt/saas/backups/env/20260614_151025`
- no destructive volume/database operation was executed.

## Remote Deploy

- remote pre-state: branch `feature/auth-system`, commit `2fa0549`, worktree clean.
- remote post-state: branch `feature/ddd/DDD-VERIFY-001`, commit `6c04f6b623a540bf153387dc87b2b26b625a5b0a`.
- deploy script fixed before final run: `deploy-remote.ps1` now prevents `docker compose exec -T` from consuming the SSH script stdin.
- remote Maven build: SUCCESS.
- remote jar: `backend/target/colonel-saas.jar`, size 80445647 bytes.
- remote backend image: `sha256:a92b0c2ab8b8481554d52da92e19768addb20af8151cac49d43b84b3e5f9dd82`
- remote frontend image: `sha256:1f2e9763e7ae208fb3ee659787c5d35839df2a81020bbcf1db2d6c303b5c6cf5`
- remote health: backend `{"status":"UP"}`, frontend `ok`, `/login` HTTP 200, unauthenticated `/api/data/orders` HTTP 401.
- remote containers: backend/frontend/postgres/redis healthy.

## Commission Config

Remote schema uses `system_config`, not `system_configs`. Effective keys are:

| key | before | after | version |
| --- | --- | --- | --- |
| `commission.business_default_ratio` | 0.15 | 0.10 | 2 |
| `commission.channel_default_ratio` | 0.15 | 0.10 | 2 |

Change log evidence:

- `commission.business_default_ratio`: old 0.15 -> new 0.10, source `REMOTE_DEPLOY`, reason `FINAL-GLOBAL-CHECK-AND-REMOTE-DEPLOY-001 user confirmed 10%+10%`.
- `commission.channel_default_ratio`: old 0.15 -> new 0.10, source `REMOTE_DEPLOY`, reason `FINAL-GLOBAL-CHECK-AND-REMOTE-DEPLOY-001 user confirmed 10%+10%`.
- legacy requested keys `recruiter_commission_rate` / `channel_commission_rate` are not the effective runtime keys.
- `commissions` count: 0; `commission_config` count: 0.
- Redis `*config*` scan returned no keys before/after, so no config cache key was deleted.

## Remote Daily Recon

SQL used the backend create-time track columns: `order_amount`, `estimate_service_fee`, `estimate_tech_service_fee`, `estimate_service_fee_expense`.

| date | orders | pay | service income | tech fee | remote expense | remote gross after 10%+10% |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 2026-06-08 | 9337 | 196721.25 | 3621.73 | 299.56 | 0.00 | 2657.74 |
| 2026-06-09 | 8667 | 193286.89 | 3567.10 | 271.88 | 0.00 | 2636.18 |
| 2026-06-10 | 10846 | 273206.45 | 5418.99 | 476.83 | 0.00 | 3953.73 |
| 2026-06-11 | 9014 | 194910.94 | 3804.20 | 282.09 | 0.00 | 2817.69 |
| 2026-06-12 | 10352 | 212655.30 | 4045.76 | 316.92 | 0.00 | 2983.07 |
| 2026-06-13 | 10455 | 206303.18 | 3962.39 | 339.94 | 0.00 | 2897.96 |

Observed risk: remote `estimate_service_fee_expense` and `effective_service_fee_expense` are 0 for every order in 2026-06-08..2026-06-13. The local/baseline evidence had non-zero service fee expense on these dates, including 2026-06-13 = 12.09. Therefore the remote 2026-06-13 gross after 10%+10% is 2897.96, not the baseline expected about 2888.29.

This is a data-fact mismatch, not a commission-config mismatch. I did not backfill or rewrite historical order facts.

## 2026-06-14 Snapshot

- orders: 5156
- pay amount: 103316.58
- service income: 1958.99
- tech fee: 176.63
- service expense: 0.00
- gross after 10%+10% formula: 1425.89

This current-day snapshot is informational only.

## Business E2E

`npm run e2e:real-pre:p0` was executed against local real-pre endpoints `http://localhost:3001` and `http://localhost:8081`.

- finalStatus: PENDING
- evidence: `runtime/qa/out/real-pre-p0-20260614-152936/report.md`
- PASS: preflight, 08 Douyin integration, 31 product chain, 35 RBAC.
- PENDING: 32 order attribution, 33 sample chain, 34 performance dashboard.
- PENDING reasons: no upstream attributed orders, no real completed order for sample auto-completion, no readable performance sample for formula verification.
- 36 cleanup plan: PASS_NEEDS_CLEANUP, PlanOnly.

This cannot be used as remote browser multi-role PASS evidence because the script resolved to local endpoints.

## Remote Logs

- recent OrderSyncJob logs show `failed=0`.
- recent backend grep only matched INFO timing lines with blank `error=` field.
- recent frontend/postgres/redis error scans showed no continuous runtime error after validation probes.
- earlier PostgreSQL ERROR entries were caused by my incorrect verification probes against non-existent columns (`change_source`, `stat_date`, `estimated_commission`), not by application traffic.

## Rollback

- code rollback: checkout prior remote branch/commit and rerun `deploy-remote.ps1`, then health check.
- config rollback: restore values from change log or database backup; must write a new change-log entry.
- data rollback: restore `/opt/saas/backups/db/saas_20260614_151025.dump` only with explicit approval.

## Final Status

PARTIAL.

Remote deploy and 10%+10% config correction are complete and verified. Full global business validation is not complete because remote historical service-fee-expense facts differ from the baseline, and full P0 E2E is PENDING due missing real samples.

# DDD ProductService ProductId Acceptance Evidence

## Metadata

- Time: 2026-07-04 14:38 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/ddd-user-role-application
- HEAD: db9c6c9c
- Deploy remote: false
- Conclusion: PARTIAL

## Conclusion

Architecture redline acceptance remains PASS: `architecture-redline-legacy-whitelist.txt` active entries are 0, and `check-ddd-acceptance.ps1 -RequireRedlineZero` passes.

Product task `P-15 productId 精确查询` is DONE by current evidence: `/products` accepts `productId`, `ProductLibraryPageQueryService` passes it into `ProductService.SelectedLibraryFilter`, and `ProductService` uses exact equality so `9001` does not match `90010`.

Full real-pre business acceptance is PARTIAL/BLOCKED because Douyin token readiness is blocked: `hasAccessToken=false`, `hasRefreshToken=true`, `reauthorizeRequired=true`.

## Scope

- Current slice: ProductService read-side closure, `P-15 productId exact query`.
- No API path, request/response field, schema, permission, state machine, amount, commission, settlement, or attribution rule was intentionally changed.
- Worktree remains dirty with historical and parallel changes; no commit and no push were performed.

## Whitelist And Matrix

| Item | Result |
| --- | --- |
| cross-domain mapper whitelist | active=0 |
| architecture redline whitelist | active=0 |
| redline burn-down | 12 -> 0 |
| core 178 business card matrix | DONE 5 / PARTIAL 123 / TODO 46 / BLOCKED 4 |
| current evidence rows including baseline/redline rows | DONE 22 / PARTIAL 124 / TODO 46 / BLOCKED 4 |

## Command Evidence

| Command | Result | Evidence |
| --- | --- | --- |
| `mvn -DskipTests compile` | PASS | backend compile BUILD SUCCESS |
| `mvn test -Dtest='ProductServiceFilterTest,ProductControllerTest,ProductLibraryPageQueryServiceTest,DddProduct003ProductRoutingTest'` | PASS | 54 tests / 0 failures / 0 errors / 0 skipped |
| `mvn test -Dtest='DddArchitectureRedlineGuardTest,DddControllerRedlineCleanupTest,DouyinControllerActivityDiagnosticTest,DouyinActivityDiagnosticServiceTest,DouyinActivityDiagnosticGatewayAdapterTest'` | PASS | 26 tests / 0 failures / 0 errors / 0 skipped |
| `mvn test -Dtest='*Architecture*Test,*Ddd*Test,*Guard*Test,*Contract*Test'` | PASS | 198 tests / 0 failures / 0 errors / 1 skipped |
| `mvn test -Dtest='*Douyin*Test'` | PASS | 213 tests / 0 failures / 0 errors / 0 skipped |
| `check-ddd-acceptance.ps1 -RequireRedlineZero` | PASS | latest DDD acceptance conclusion PASS; redline active=0 |
| `agent-do.ps1 -Env real-pre -Scope full` | FAIL / BLOCKED_AUTH | backend package PASS, frontend build PASS, Docker restart PASS, backend/frontend health PASS, business preflight blocked |

## Real-Pre Evidence

| Check | Result | Evidence |
| --- | --- | --- |
| backend package | PASS | `agent-do.ps1` executed backend package successfully |
| frontend build | PASS | Vite build completed; npm audit still reports existing vulnerabilities |
| Docker restart | PASS | real-pre backend/frontend containers rebuilt/recreated |
| backend health | PASS | `GET http://127.0.0.1:8081/api/system/health` returned `UP` |
| frontend health | PASS | `GET http://127.0.0.1:3001/healthz` returned 200 |
| business preflight | BLOCKED_AUTH | `runtime/qa/out/real-pre-preflight-20260704-142117/report.md` |

## Artifacts

- DDD acceptance report: `harness/reports/latest-ddd-acceptance-report.md`
- This evidence report: `harness/reports/latest-evidence-20260704.md`
- Retro summary: `harness/reports/retro-20260704-143955.md`
- Preflight report: `runtime/qa/out/real-pre-preflight-20260704-142117/report.md`

## Apifox / OpenAPI Harness Evidence

### Current Status

| Item | Result |
| --- | --- |
| Scope | Apifox/OpenAPI local harness |
| Commit before | `3fda3d61` |
| Commit after | not committed in this step |
| OpenAPI local guard | PASS |
| Apifox CLI guard | PASS (`2.2.5`) |
| Apifox harness | PASS |
| Cloud import | NOT EXECUTED; expected blocked by development endpoint placeholders |
| Docs site/shared-doc | NOT PUBLISHED |

### Root Cause

Apifox branch was created but endpoint documentation/details were not visible because the sync did not use the Apifox development port / development endpoint.

### New Guards

- Development endpoint guard.
- Target branch / `targetBranchId` guard.
- Secret safety guard.
- Endpoint detail readback guard.
- Optional environment Base URL readback guard.
- Docs-site/shared-doc separation guard.

### Local OpenAPI Stats

| Item | Result |
| --- | --- |
| OpenAPI file | `docs/openapi/saas-openapi.json` |
| paths | 221 |
| operations | 252 |
| schemas | 345 |
| servers | 2 |
| securitySchemes | `bearerAuth` |
| bearerAuth | true |

### CLI And Development Endpoint Checks

| Check | Result |
| --- | --- |
| `apifox -v` | PASS; `2.2.5` |
| `apifox import --help` | PASS; `--project`, `--format`, `--file`, `--branch` present |
| `apifox endpoint list/get --help` | PASS; branch readback supported |
| `apifox environment list/get --help` | PASS |
| `APIFOX_DEV_BASE_URL` | placeholder/missing in local cloud config |
| `APIFOX_DEV_PORT` | placeholder/missing in local cloud config |
| OpenAPI servers contain dev endpoint | placeholder mode; real cloud import blocked |
| Cloud environment checked | NOT EXECUTED; no real environment id used |

### Secret And Cloud Sync Checks

| Check | Result |
| --- | --- |
| `.env` ignored | PASS |
| `.env` staged | PASS; not staged |
| staged token scan | PASS |
| staged project id scan | PASS |
| logs redacted | PASS; token not printed, Project ID masked |
| target branch | `ddd-sync` |
| endpoint list/get cloud readback | Implemented in `scripts/sync-apifox.sh`; not executed locally |
| environment Base URL readback | Implemented when `APIFOX_ENVIRONMENT_ID` is real; not executed locally |
| Retro summary | `harness/reports/retro-20260704-150831.md` |

### Apifox Commands

| Command | Result |
| --- | --- |
| `apifox.cmd -v` | PASS; `2.2.5` |
| `bash -n scripts/sync-apifox.sh` | PASS |
| `bash -n scripts/verify-openapi-apifox.sh` | PASS |
| `bash scripts/verify-openapi-apifox.sh` via Git Bash login shell | PASS |
| `safety-check.ps1 -Env real-pre -Scope apifox` | PASS |
| `agent-do.ps1 -Env real-pre -Scope apifox -DryRun -ContentMaintenance off` | PASS; parameter binding and Git Bash path resolved; dry-run did not execute local harness |
| `git diff --check` | PASS; existing CRLF/LF warning only |
| `check-harness-limits.ps1` | PASS |

### Rollback

- Revert this Apifox harness patch.
- Restore local `.env` placeholders to block cloud import.
- Keep local OpenAPI export unaffected.

## Remaining Risk

- real-pre full business flow cannot be counted as PASS until Douyin authorization is refreshed and preflight becomes PASS.
- Worktree has unrelated/historical dirty files; `-FailOnUnexpectedDirty` is expected to fail and should remain a guard before commit.
- Next DDD phase should move to ProductService / Dashboard / Order / Performance slices, with parity tests before marking additional cards DONE.

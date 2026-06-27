# Evidence - DDD100 Metric Issue #32

## Metadata

| Field | Value |
|---|---|
| Date | 2026-06-27 |
| Env | local real-pre repo context |
| Branch | `feature/ddd/DDD-VERIFY-001` |
| Scope | docs / harness script |
| Gate | Gate 0 - Docs/Harness metric script |
| Issue | #32 `[DDD100-METRIC] DDD 迁移率脚本与 evidence 指标固化` |

## Changed Files

- `harness/scripts/probes/ddd-migration-metrics.ps1`
- `harness/reports/2026-06-21/ddd-migration-metric-032/evidence-20260627-120000-ddd-migration-metrics.md`
- `harness/reports/2026-06-21/ddd-migration-metric-032/retro-20260627-120000-ddd-migration-metrics.md`
- `harness/engineering/issues-index.md`
- `harness/rules/state/snapshots/01-当前项目状态.md`
- `harness/rules/state/snapshots/DOMAIN_STATUS.md`
- `harness/rules/changelog.md`

## Script Contract

Command:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\probes\ddd-migration-metrics.ps1 -RepoRoot . -Format Markdown
```

The script is read-only. It scans production Java files under `backend/src/main/java/com/colonel/saas`, counts nonblank non-comment source LOC, and reports:

- global raw `domain/` share;
- business migration proxy;
- per-domain DDD layer LOC: application/query/port/policy/facade/infrastructure/api/domain/event/other;
- per-domain legacy service LOC and legacy entry LOC.

## Current Output Summary

| Metric | Value |
|---|---:|
| Counted production Java files | 841 |
| Production Java source LOC | 75,848 |
| DDD domain source LOC | 15,286 |
| Legacy service source LOC | 32,845 |
| Legacy entry source LOC | 42,250 |
| Raw domain share | 20.2% |
| Business migration proxy | 26.6% |

## Per-Domain Snapshot

| Domain | DDD | App | Query | Port | Policy | Facade | Infra | API | Model | Event | Other DDD | Legacy Service | Legacy Entry | Proxy |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| product | 2,905 | 756 | 1 | 160 | 1,029 | 454 | 148 | 1 | 1 | 354 | 1 | 8,137 | 10,302 | 22.0% |
| other | 25 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 25 | 3,841 | 7,046 | 0.4% |
| order | 2,809 | 697 | 289 | 0 | 868 | 665 | 40 | 1 | 1 | 247 | 1 | 5,182 | 6,624 | 29.8% |
| talent | 762 | 307 | 1 | 0 | 134 | 194 | 50 | 1 | 26 | 48 | 1 | 4,038 | 4,509 | 14.5% |
| sample | 1,391 | 753 | 1 | 0 | 234 | 26 | 1 | 78 | 1 | 296 | 1 | 3,865 | 4,175 | 25.0% |
| performance | 952 | 224 | 1 | 0 | 435 | 239 | 39 | 1 | 11 | 1 | 1 | 2,951 | 3,645 | 20.7% |
| analytics | 384 | 326 | 1 | 0 | 1 | 1 | 26 | 1 | 1 | 26 | 1 | 2,916 | 3,269 | 10.5% |
| user | 5,038 | 2,266 | 1 | 308 | 856 | 311 | 1,102 | 52 | 1 | 93 | 48 | 1,107 | 1,686 | 74.9% |
| config | 323 | 1 | 1 | 0 | 1 | 248 | 1 | 1 | 1 | 68 | 1 | 808 | 994 | 24.5% |
| colonel | 90 | 83 | 0 | 0 | 0 | 0 | 0 | 0 | 7 | 0 | 0 | 0 | 0 | 100.0% |
| event | 587 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 587 | 0 | 0 | 100.0% |
| shared | 20 | 1 | 1 | 0 | 12 | 1 | 1 | 1 | 1 | 1 | 1 | 0 | 0 | 100.0% |

## Verification

| Check | Result | Evidence |
|---|---|---|
| code-review-graph intake | PASS | 13,449 nodes, 153,352 edges, 1,670 files; risk low for script slice |
| Markdown output | PASS | `ddd-migration-metrics.ps1 -RepoRoot . -Format Markdown` |
| JSON output | PASS | `ddd-migration-metrics.ps1 -RepoRoot . -Format Json \| ConvertFrom-Json` returned expected summary fields |
| Harness limits | PASS | `check-harness-limits.ps1` returned `PASS` |
| Build / Docker / Health | SKIP | docs/harness script only; no backend/frontend/runtime artifact changed |
| Business E2E | SKIP | metric script only; no business behavior changed |

## Result

Status: PASS for #32 metric codification.

The DDD migration metric is now repeatable through `harness/scripts/probes/ddd-migration-metrics.ps1`. The script output should be included in later evidence reports and final DDD100 closeout.

## Remaining Risks

- Domain classification is still path/name based. `other` remains visible instead of being hidden.
- This metric is a progress proxy, not a proof that all DDD boundaries are semantically correct.
- #86/#87/#89 must decide when legacy service LOC is sufficiently retired for final closeout.

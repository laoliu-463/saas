# Evidence - DDD100 Baseline Issue #30

## Metadata

| Field | Value |
|---|---|
| Date | 2026-06-27 |
| Env | local real-pre repo context |
| Branch | `feature/ddd/DDD-VERIFY-001` |
| Scope | docs |
| Gate | Gate 0 - Docs Only |
| Issue | #30 `[DDD100-BASELINE] 当前 100% 迁移率与风险基线重算` |

## Changed Files

- `harness/reports/ddd100-baseline-20260627.md`
- `harness/reports/evidence-20260627-ddd100-baseline.md`
- `harness/reports/retro-20260627-ddd100-baseline.md`
- `harness/engineering/issues-index.md`
- `harness/rules/state/snapshots/01-当前项目状态.md`
- `harness/rules/state/snapshots/DOMAIN_STATUS.md`
- `harness/rules/changelog.md`

## Evidence Collected

| Check | Result | Evidence |
|---|---|---|
| Git intake | PASS | worktree clean before edits; branch `feature/ddd/DDD-VERIFY-001`; HEAD `b38d8073` |
| code-review-graph context | PASS | 13,425 nodes, 153,134 edges, 1,668 files; risk low for docs slice |
| Issue source | PASS | `gh issue view 30` confirmed open, ready-for-agent, no blockers |
| Open DDD100 leaf count | PASS | `gh issue list` found #30-#89 = 60 open leaf issues before this slice |
| Baseline metric recompute | PASS | production Java source LOC 76,257; `domain/` 15,306; legacy business-entry 42,924 |
| Raw DDD share | PASS | 20.1% of production Java source LOC under `domain/` |
| Business migration proxy | PASS | 26.3% = 15,306 / (15,306 + 42,924) |
| Main legacy hotspots | PASS | ProductService, SampleApplicationService, DataApplicationService, TalentService, OrderSyncService remain top risks |
| Harness limits | PASS | `check-harness-limits.ps1` returned `PASS`; reports direct file count = 49; edited markdown files <= 200 lines |
| agent-do docs dry-run | PARTIAL | safety check passed, docs health/build skipped as expected; dry-run showed unrelated untracked #60 backend test would be staged by script, so real submit uses explicit file staging instead |
| Backend build | SKIP | docs-only; no backend source, SQL, config, or Docker change in #30 commit |
| Frontend build | SKIP | docs-only; no frontend source change |
| Docker restart | SKIP | docs-only; no runtime artifact change |
| Health check | SKIP | docs-only; no service reload |
| Business verification | SKIP | docs-only baseline; no behavior change to verify |

## Metric Command Summary

The metric was computed from:

```powershell
backend/src/main/java/com/colonel/saas/**/*.java
```

Counted source LOC excludes blank lines and lines beginning with `//`, `/*`, or `*`. Buckets:

- `ddd_domain`: `domain/*`
- `legacy_business_entry`: `service/*`, `controller/*`, `auth/*`, `job/*`, `listener/*`
- `support_infra_or_dto`: mapper/entity/config/common/security/exception/thirdparty/gateway/dto/vo/testsupport
- `other`: unmatched production Java files

Full result is recorded in `harness/reports/ddd100-baseline-20260627.md`.

## Result

Status: PASS for Gate 0 docs baseline.

This slice did not prove DDD completion or runtime behavior. It established a reproducible 2026-06-27 planning baseline and recorded the remaining risks for #31-#89.

## Remaining Risks

- Metric remains a path/name proxy until #32 codifies a reusable script and locks report format.
- `other/unclassified` legacy business-entry LOC needs human classification before final 100% reporting.
- Runtime E2E remains pending for later behavior-changing slices.
- Registered dirty outside #30: `backend/src/test/java/com/colonel/saas/service/ProductServiceCharacterizationTest.java` is an untracked Issue #60 characterization test file. It is not staged or committed in #30.

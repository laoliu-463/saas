# Evidence: DDD-COMPLETE-100-PRODUCT #96 Partial Closeout

## Scope

- Issue: #96 `[DDD-COMPLETE-100-PRODUCT] 商品域完整 DDD 收口`
- Date: 2026-06-27
- Environment: local real-pre evidence review
- Branch: `feature/ddd/DDD-VERIFY-001`
- Remote deploy: not requested
- Conclusion status: `PARTIAL / BLOCKED_BY_SAMPLE`

## Issue State

GitHub sub-issues verified with `gh issue view <number> --json number,state,closedAt,url` where applicable:

| Issue | State | Closed At |
| --- | --- | --- |
| #130 | CLOSED | 2026-06-27 12:19:58 |
| #131 | CLOSED | 2026-06-27 12:45:20 |
| #132 | CLOSED | 2026-06-27 13:21:02 |
| #133 | CLOSED | 2026-06-27 14:04:35 |
| #134 | CLOSED | 2026-06-27 14:04:38 |
| #135 | OPEN | reopened 2026-06-27 22:xx |
| #136 | CLOSED | 2026-06-27 14:04:49 |

Parent #96 was also reopened because #135 is an explicit child of the product epic.

## Current Metrics

Command:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\probes\ddd-migration-metrics.ps1 -RepoRoot . -Format Markdown
```

Result excerpt:

| Metric | Value |
| --- | ---: |
| Production Java source LOC | 76526 |
| DDD domain source LOC | 16978 |
| Legacy service source LOC | 31815 |
| Legacy entry source LOC | 41233 |
| Raw domain share | 22.2% |
| Business migration proxy | 29.2% |
| Product proxy | 30.8% |

## Verification Evidence

- #130 evidence: `harness/reports/2026-06-27/ddd-complete-product-130/evidence-20260627-200829-product-service-inventory.md`
- #131 evidence: `harness/reports/2026-06-27/ddd-complete-product-131/evidence-20260627-204000-product-backfill-application.md`
- #132 evidence: `harness/reports/2026-06-27/ddd-complete-product-132/evidence-20260627-211900-product-operation-policy.md`
- #133 evidence: `harness/reports/evidence-20260627-215952.md`
- Targeted #133 verification rerun: `mvn -f backend/pom.xml "-Dtest=ActivityProductReadModelQueryServiceTest" test` -> 3/3 PASS
- Harness limits check: PASS
- #134 real-pre read-only SQL: promoting snapshots `18201`, repaired states `18201`, unrepaired states `0`.
- #135 real-pre read-only SQL: active `pick_source_mapping` rows `13`, active product/activity mappings `5`, orders with `pick_source` `0`, mapped orders `0`.

## Boundary

This review covers the product domain epic and its seven product sub-issues. Because #135 explicitly requires a real promotion-link order carrying `pick_source`, the current zero-order SQL result blocks product epic completion. Related order-domain observation also remains tracked by #117.

## Conclusion

`PARTIAL / BLOCKED_BY_SAMPLE` for #96. Product query, repair, policy, backfill and legacy-retire slices have substantial evidence, but #135 lacks the required real order sample. GitHub #96 and #135 must remain open until at least one real `colonelsettlement_order.pick_source` row can be traced to an active `pick_source_mapping`, or the user explicitly accepts the residual risk.

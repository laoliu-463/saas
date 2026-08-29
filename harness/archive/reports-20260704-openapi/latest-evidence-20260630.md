# Evidence 2026-06-30 - Dual Commission Product Field Audit

## Scope

- Env: local `real-pre`
- Task: check whether dual commission fields are supported by backend and product views.
- Mode: read-only investigation; no code or config changes.
- Skill: `$grill-me` evidence-chain review.
- code-review-graph: unavailable in this Codex tool context; fallback to `rg`, source reads, Docker/DB probes.

## Evidence

### Docs / Boundary

- `docs/领域/商品域.md`: product domain owns product display inputs; does not calculate final commission.
- `docs/领域/业绩域.md`: performance domain owns final attribution, commission and dual-track amount calculation.
- `docs/06-数据模型总表.md`: product tables store price and commission display inputs, not final performance facts.

### Backend Source

- `product_snapshot` schema includes:
  - `activity_cos_ratio`
  - `activity_cos_ratio_text`
  - `cos_type`
  - `cos_type_text`
  - `ad_service_ratio`
  - `activity_ad_cos_ratio`
- `ProductSnapshot` entity maps the same six fields.
- Douyin product/activity real gateways parse upstream `cos_type`, `activity_cos_ratio`, `ad_service_ratio`, `activity_ad_cos_ratio`; `cos_type == 1` maps to `双佣金`.
- `ProductSnapshotMapper.xml` upsert persists and updates all six fields.
- Activity product list/detail views expose:
  - `activityCosRatio`
  - `activityCosRatioText`
  - `cosType`
  - `cosTypeText`
  - `adServiceRatio`
  - `activityAdCosRatio`
  - derived `commissionRate` and `serviceFeeRate`
- Shared product library `/products` returns legacy `Product`; it currently carries `activityCosRatioText` and `estimatedServiceFee`, but not full `cosType/adServiceRatio/activityAdCosRatio` fields.
- `doubleCommission` product-library filter currently matches audit supplement / product tag, not `product_snapshot.cos_type = 1`.

### Frontend Source

- `ProductSelectionCard.vue` displays one `commissionRate` and one `serviceFeeRate`.
- `product-library-display.ts` maps `commissionRate` from `commissionRateText || activityCosRatioText || commissionRate`.
- `product-library-display.ts` does not consume `cosType/cosTypeText/adServiceRatio/activityAdCosRatio` for a full dual-commission display.
- `ProductDetail.vue` displays `activityCosRatioText` and `serviceFeeRate`, not `cosTypeText` or activity ad commission fields.
- legacy/product manage table has `普通/日常/投放期` rendering structure, but it reads `campaignCommissionRateText/putCommissionRateText` style fields, not the backend's `activityAdCosRatio/adServiceRatio` directly.

### DB Probe

Command: read-only SQL in `postgres-real-pre`.

Schema result:

- all six `product_snapshot` dual commission columns exist in live DB.

Count result:

- total non-deleted snapshots: 95135
- rows matching `cos_type = 1` or `cos_type_text ILIKE '%双佣%'`: 24328
- rows with `activity_cos_ratio`: 95135
- rows with `ad_service_ratio`: 24328
- rows with `activity_ad_cos_ratio`: 95135

### Runtime Probe

- `docker compose -f docker-compose.real-pre.yml ps`: backend, frontend, postgres and redis containers are up.
- `docker inspect` reports backend health status `healthy`.
- Direct host `curl http://127.0.0.1:8081/api/system/health` timed out after 5s; backend log around the same time recorded `/api/system/health status=200 durationMs=0`.

## Conclusion

Status: `PARTIAL`

Backend storage, sync and activity-product API view support are present for dual commission source fields. Shared product-library API and frontend product card/detail display are only partially aligned: they can show the normal/activity commission rate, but do not yet fully expose/display the dual-commission type and ad/activity service-fee fields.

## Not Verified

- Authenticated `GET /api/products` and `GET /api/colonel/activities/{activityId}/products` JSON response was not captured in this slice.
- No frontend browser screenshot was captured.
- No build/test was run because no code was changed.
- No Docker restart was performed because no code/config was changed.

## Retro Summary

No Harness rule upgrade needed. The reusable lesson is to separate three meanings: upstream dual-commission source fields, product display fields, and performance-domain final commission calculation.

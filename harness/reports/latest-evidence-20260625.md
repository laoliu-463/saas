# Evidence Report - 2026-06-25

## Scope
- Task: 排查数据看板创建轨订单总数与订单明细订单不同步。
- Env: local `real-pre`.
- Branch: `feature/auth-system`.
- Commit at investigation: `1be87f2c`.
- Code changes: none.
- Build/restart/remote deploy: not executed because this was read-only diagnosis.
- Result status: `PARTIAL` - root-cause evidence collected; no fix applied.

## Reproduction
- Existing read-only script:
  - `npm run e2e:real-pre:dashboard-reconcile`
  - First run: `FAIL`, admin summary lagged DB by `8` orders.
  - Second run after cache TTL: `PASS`, admin summary `orderCount=321018`, DB `321018`.
  - Evidence dirs:
    - `runtime/qa/out/real-pre-dashboard-reconcile-20260625-181647`
    - `runtime/qa/out/real-pre-dashboard-reconcile-20260625-181813`
- Targeted API check at `2026-06-25`:
  - `/api/dashboard/metrics`: `estimate.todayOrderCount=4654`, `estimate.totalOrders=4654`, source `performance_records`.
  - `/api/data/orders/detail?timeField=createTime&startDate=2026-06-25&endDate=2026-06-25&page=1&size=1`: `total=4759`.
  - `/api/data/orders/detail?timeField=createTime&page=1&size=1`: `total=286280`.

## Evidence
- SQL current-day counts:
  - valid `performance_records` joined to orders by `create_time`: `4654`.
  - order facts by `create_time`: `4759`.
  - `order_status IN (4,5)`: `105`.
- SQL default detail window:
  - order facts from `CURRENT_DATE - INTERVAL '30 days'` to tomorrow: `286280`.
- Missing valid performance rows for today are cancelled/invalid facts:
  - `order_status=4`
  - `attribution_status=UNATTRIBUTED`
  - `attribution_remark=COLONEL_MAPPING_NOT_FOUND`
- Code evidence:
  - `DataApplicationService.buildMetrics()` sets both `todayOrderCount` and `totalOrders` from `performanceMetricsQueryService.aggregateRange(todayStart, tomorrowStart, "createTime", ...)`.
  - `DataApplicationService.getOrderDetailPage()` defaults to `LocalDate.now().minusDays(30)` through tomorrow and queries `colonelsettlement_order`.
  - `PerformanceMetricsQueryService.aggregateRange()` filters `performance_records pr WHERE pr.is_valid = TRUE`.
  - `PerformanceCalculationService` marks cancelled/invalid orders as `is_valid=false`.
  - Frontend `frontend/src/views/data/index.vue` displays `createTrack.totalOrders ?? createTrack.todayOrderCount` as "总订单数 / 成交".

## Stage Conclusion
- The mismatch is reproducible and has two concrete causes:
  1. Time range mismatch: the data-board create track is "today", while order detail without date filters is the default recent 30-day window.
  2. Source/status mismatch: create-track metrics count valid `performance_records`; order detail counts order facts in `colonelsettlement_order`, including invalid/cancelled order facts.
- The remaining same-day gap is explained by invalid/cancelled order facts: `4759 - 4654 = 105`.
- The earlier 8-order `/dashboard/summary` mismatch disappeared after TTL and is consistent with short cache / live sync timing, not a stable root cause.

## Options
- Temporary UI clarification: label metrics as "今日有效订单数（创建轨）" and keep order detail as full order facts.
- Root-cause alignment option A: if business expects equality, add equivalent date/status filters when drilling from create-track metrics into order detail.
- Root-cause alignment option B: if business expects detail to remain full facts, add a visible status breakdown and do not compare it directly with valid performance metrics.
- Long-term governance: add a read-only reconcile script covering `dashboard/metrics` vs `data/orders/detail` with explicit time range and status semantics.

## Verification Gap
- No code was changed.
- No build, restart, or E2E was executed for a fix.
- Business decision still required: whether "创建轨订单总数" should mean valid performance orders or all order facts.
- Retro: no Harness upgrade needed; existing scripts were sufficient but a focused metrics/detail reconcile script would reduce future ambiguity.

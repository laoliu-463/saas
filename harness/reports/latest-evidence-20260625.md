# Evidence Report - 2026-06-25

## Scope
- Task: 排查数据看板创建轨订单总数与订单明细订单不同步。
- Env: local `real-pre`.
- Branch: `feature/auth-system`.
- Commit at investigation: `1be87f2c`.
- Fix commit: `55aab956` plus follow-up QA evidence commit pending at report update time.
- Code changes: dashboard metrics, performance summary, frontend copy, and reconcile QA script now align counts with order facts.
- Build/restart/remote deploy: local `real-pre` full harness executed; remote deploy not requested.
- Result status: `PASS` for local `real-pre` after fix.

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

## Fix
- `PerformanceMetricsQueryService`:
  - `aggregateRange()` and `trendByDay()` now start from `colonelsettlement_order co`.
  - `performance_records pr` is left-joined only for attribution/profit/commission fields.
  - Order count, order amount, service fee income, tech fee, and expense use order fact columns.
- `PerformanceSummaryService`:
  - `/performance/summary` also starts from order facts.
  - Status, activity, product, talent, and cohort time filters use `co.*` facts.
- `frontend/src/views/data/index.vue`:
  - Removed "仅统计有效订单" copy.
  - Copy now states order facts align with details, while收益/提成来自业绩记录.
- `runtime/qa/real-pre-dashboard-reconcile.cjs`:
  - DB expectation updated to order facts + left join performance records.

## Verification
- Red/green tests:
  - `mvn -Dtest=PerformanceMetricsQueryServiceTest test`: red before fix, pass after fix.
  - `mvn -Dtest=PerformanceSummaryServiceTest test`: red before fix, pass after fix.
- Targeted regression:
  - `mvn "-Dtest=PerformanceMetricsQueryServiceTest,PerformanceSummaryServiceTest,DataControllerTest" test`: `60` tests pass.
  - `node --test runtime/qa/real-pre-dashboard-reconcile.test.cjs`: `5` tests pass.
- Build:
  - `npm --prefix frontend run build`: pass.
  - Harness backend package and frontend build: pass.
- Restart / health:
  - `agent-do.ps1 -Env real-pre -Scope full`: Docker backend/frontend rebuilt and restarted.
  - Backend `/api/system/health`: `200`, body `{"status":"UP"}`.
  - Frontend `/healthz`: `200`.
- Business validation:
  - `npm run e2e:real-pre:p0:preflight`: pass.
  - `npm run e2e:real-pre:dashboard-reconcile`: pass at `runtime/qa/out/real-pre-dashboard-reconcile-20260625-184120`.
  - Admin `/dashboard/summary`: `orderCount=331938`, DB order facts `331938`, diff `0`.
  - Admin create-track API check: `/dashboard/metrics` estimate `todayOrderCount=4900`; `/data/orders/detail` today create-time total `4900`; diff `0`.
- Remote deploy:
  - `agent-do.ps1 -Env real-pre -Scope full -DeployRemote true`: pass, commit `5c9e4b96`.
  - Remote backend `http://127.0.0.1:8081/api/system/health` via `ssh saas`: `{"status":"UP"}`.
  - Remote frontend `http://127.0.0.1:3001/healthz` via `ssh saas`: `ok`.
  - Remote in-host create-track API check: `/dashboard/metrics` estimate `todayOrderCount=5345`; `/data/orders/detail` today create-time total `5345`; diff `0`.

## Remaining Risk
- `metricsSource` remains `performance_records` for API compatibility; actual count source is now order facts with performance left join.
- Real-pre data is still live-synchronizing. Reconcile checks should wait one short-cache cycle when comparing point-in-time values.
- `npm ci` reports existing dependency audit findings; not introduced by this fix.

## Retro
- Harness behavior did not require structural changes.
- QA reconcile SQL was updated because its expected value encoded the old business口径.

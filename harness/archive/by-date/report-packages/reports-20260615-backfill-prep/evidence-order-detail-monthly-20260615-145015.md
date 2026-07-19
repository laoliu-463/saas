# ORDER-DETAIL-MONTHLY-001 baseline evidence

- Probe timestamp: 20260615-145015
- Compose project: saas-active
- Probe SQL: harness/scripts/probes/order-detail-monthly-probe.sql
- Report: harness\reports\evidence-order-detail-monthly-20260615-145015.md

## [P0] Git workspace
```
 M backend/src/main/java/com/colonel/saas/job/JobLockKeys.java  M backend/src/main/java/com/colonel/saas/job/ProductActivitySyncJob.java  M backend/src/main/java/com/colonel/saas/job/ProductDisplayRuleJob.java  M backend/src/main/java/com/colonel/saas/mapper/ProductSyncJobLogMapper.java  M backend/src/main/java/com/colonel/saas/service/ProductActivityBackfillService.java  M backend/src/main/java/com/colonel/saas/service/ProductDisplayRuleService.java  M backend/src/main/java/com/colonel/saas/service/ProductService.java  M backend/src/main/resources/application.yml  M backend/src/main/resources/mapper/ProductSnapshotMapper.xml  M backend/src/test/java/com/colonel/saas/controller/ProductSyncAdminControllerTest.java  M backend/src/test/java/com/colonel/saas/job/ProductActivitySyncJobTest.java  M backend/src/test/java/com/colonel/saas/job/ProductDisplayRuleJobTest.java  M backend/src/test/java/com/colonel/saas/service/ProductActivityBackfillServiceTest.java  M docs/01-V2浜や粯鑼冨洿涓庤竟鐣?md  M docs/README.md RM docs/00-V1鑼冨洿鍐荤粨璇存槑.md -> docs/archive/2026-06-v1-retire/01-contracts/00-V1鑼冨洿鍐荤粨璇存槑.md RM docs/01-V1浜や粯鍚堝悓.md -> docs/archive/2026-06-v1-retire/01-contracts/01-V1浜や粯鍚堝悓.md RM docs/01-V1棰嗗煙瑁佸壀琛?md -> docs/archive/2026-06-v1-retire/01-contracts/01-V1棰嗗煙瑁佸壀琛?md RM docs/02-V1涓嶅仛娓呭崟.md -> docs/archive/2026-06-v1-retire/01-contracts/02-V1涓嶅仛娓呭崟.md RM docs/02-V1涓氬姟娴佺▼涓庨鍩熻璁?md -> docs/archive/2026-06-v1-retire/01-contracts/02-V1涓氬姟娴佺▼涓庨鍩熻璁?md RM docs/08-V1闇€姹傛帴鍙ｆ祴璇曠煩闃?md -> docs/archive/2026-06-v1-retire/01-contracts/08-V1闇€姹傛帴鍙ｆ祴璇曠煩闃?md RM docs/10-V1涓婄嚎鍑嗗叆缁撹.md -> docs/archive/2026-06-v1-retire/01-contracts/10-V1涓婄嚎鍑嗗叆缁撹.md RM docs/V1瀵归綈-涓氱哗鍩?md -> docs/archive/2026-06-v1-retire/02-domain-work/V1瀵归綈-涓氱哗鍩?md RM docs/V1瀵归綈-鍒嗘瀽妯″潡.md -> docs/archive/2026-06-v1-retire/02-domain-work/V1瀵归綈-鍒嗘瀽妯″潡.md RM docs/V1瀵归綈-鍟嗗搧鍩?md -> docs/archive/2026-06-v1-retire/02-domain-work/V1瀵归綈-鍟嗗搧鍩?md RM docs/V1瀵归綈-瀵勬牱鍩?md -> docs/archive/2026-06-v1-retire/02-domain-work/V1瀵归綈-瀵勬牱鍩?md RM docs/V1瀵归綈-鐢ㄦ埛鍩?md -> docs/archive/2026-06-v1-retire/02-domain-work/V1瀵归綈-鐢ㄦ埛鍩?md RM docs/V1瀵归綈-璁㈠崟鍩?md -> docs/archive/2026-06-v1-retire/02-domain-work/V1瀵归綈-璁㈠崟鍩?md RM docs/V1瀵归綈-璺ㄥ煙娴佺▼.md -> docs/archive/2026-06-v1-retire/02-domain-work/V1瀵归綈-璺ㄥ煙娴佺▼.md RM docs/V1瀵归綈-杈句汉鍩?md -> docs/archive/2026-06-v1-retire/02-domain-work/V1瀵归綈-杈句汉鍩?md RM docs/V1瀵归綈-閰嶇疆鍩?md -> docs/archive/2026-06-v1-retire/02-domain-work/V1瀵归綈-閰嶇疆鍩?md RM docs/V1-鍒嗘瀽妯″潡鐜扮姸瀹¤.md -> docs/archive/2026-06-v1-retire/03-domain-audits/V1-鍒嗘瀽妯″潡鐜扮姸瀹¤.md RM docs/V1-鍟嗗搧鍩熺幇鐘跺璁?md -> docs/archive/2026-06-v1-retire/03-domain-audits/V1-鍟嗗搧鍩熺幇鐘跺璁?md RM docs/V1-瀵勬牱鍩熺幇鐘跺璁?md -> docs/archive/2026-06-v1-retire/03-domain-audits/V1-瀵勬牱鍩熺幇鐘跺璁?md RM docs/V1-鐢ㄦ埛鍩熺幇鐘跺璁?md -> docs/archive/2026-06-v1-retire/03-domain-audits/V1-鐢ㄦ埛鍩熺幇鐘跺璁?md RM docs/V1-杈句汉鍩熺幇鐘跺璁?md -> docs/archive/2026-06-v1-retire/03-domain-audits/V1-杈句汉鍩熺幇鐘跺璁?md RM docs/V1-閰嶇疆鍩熺幇鐘跺璁?md -> docs/archive/2026-06-v1-retire/03-domain-audits/V1-閰嶇疆鍩熺幇鐘跺璁?md RM docs/V1棰嗗煙瀵归綈鎬绘姤鍛?md -> docs/archive/2026-06-v1-retire/04-summary/V1棰嗗煙瀵归綈鎬绘姤鍛?md RM docs/V1棰嗗煙瀵归綈鎬昏〃.md -> docs/archive/2026-06-v1-retire/04-summary/V1棰嗗煙瀵归綈鎬昏〃.md  M harness/reports/latest-harness-limits-check.md ?? __add_retired_header.py ?? __list.txt ?? __v1_list.json ?? backend/src/main/java/com/colonel/saas/job/StaleProductSyncJobReconcileJob.java ?? backend/src/test/java/com/colonel/saas/job/StaleProductSyncJobReconcileJobTest.java ?? backend/src/test/java/com/colonel/saas/service/ProductBackfillConcurrencyAndDeadlockTest.java ?? backend/src/test/java/com/colonel/saas/service/ProductBackfillLockOrderTest.java ?? backend/src/test/java/com/colonel/saas/service/ProductLibraryDisplayRegressionTest.java ?? docs/archive/2026-06-v1-retire/README.md ?? docs/鏂规/PLAN-005-璁㈠崟鏄庣粏鍒嗛〉鎸夋湀瓒呮椂璇婃柇涓庢敼閫?md ?? harness/CURRENT_STATE.md ?? harness/FORBIDDEN_SCOPE.md ?? harness/TASK_ROUTING.md ?? harness/reports/_phase4-1-rerun-baseline/ ?? harness/reports/evidence-order-detail-monthly-20260615-144833.md ?? harness/scripts/probes/order-detail-monthly-probe.ps1 ?? harness/scripts/probes/order-detail-monthly-probe.sql
```

## [P1] Docker services
```
```

## [P2] SQL probe (read-only EXPLAIN / COUNT / statistics)
```
```

## [P3] End-to-end API latency (3 runs, take median)

Endpoint: ${apiBase}/api/data/orders/detail?startDate=2026-05-01&endDate=2026-05-31&timeField=createTime&page=1&size=20
Note: 3 sequential calls. Total / Connect / TTFB / ContentTransfer reported per call.
This endpoint requires a valid session token (cookie / Authorization). 401 means the latency
is purely the unauthenticated rejection path. To measure real end-to-end latency, run from a
logged-in browser session OR supply a token via the BearerAuthHeader env var, e.g.:
    $env:BearerAuthHeader = "Bearer eyJ..."
    powershell -File harness/scripts/probes/order-detail-monthly-probe.ps1

### Run 1
```
time_total=0.006634s time_connect=0.001397s time_starttransfer=0.006531s size_download=133B http_code=401
```
### Run 2
```
time_total=0.006278s time_connect=0.001897s time_starttransfer=0.006180s size_download=133B http_code=401
```
### Run 3
```
time_total=0.030630s time_connect=0.026187s time_starttransfer=0.030533s size_download=133B http_code=401
```

Median time_total: 0.006634 s (over 3 runs)

## [P4] Harness limits
```
```


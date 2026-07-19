# ORDER-DETAIL-MONTHLY-001 baseline evidence

- Probe timestamp: 20260615-145948
- Compose project: saas-active
- Probe SQL: harness/scripts/probes/order-detail-monthly-probe.sql
- Report: harness\reports\evidence-order-detail-monthly-20260615-145948.md

## [P0] Git workspace
```
 M CLAUDE.md  M backend/src/main/java/com/colonel/saas/job/JobLockKeys.java  M backend/src/main/java/com/colonel/saas/job/ProductActivitySyncJob.java  M backend/src/main/java/com/colonel/saas/job/ProductDisplayRuleJob.java  M backend/src/main/java/com/colonel/saas/mapper/ProductSyncJobLogMapper.java  M backend/src/main/java/com/colonel/saas/service/ProductActivityBackfillService.java  M backend/src/main/java/com/colonel/saas/service/ProductDisplayRuleService.java  M backend/src/main/java/com/colonel/saas/service/ProductService.java  M backend/src/main/resources/application.yml  M backend/src/main/resources/mapper/ProductSnapshotMapper.xml  M backend/src/test/java/com/colonel/saas/controller/ProductSyncAdminControllerTest.java  M backend/src/test/java/com/colonel/saas/job/ProductActivitySyncJobTest.java  M backend/src/test/java/com/colonel/saas/job/ProductDisplayRuleJobTest.java  M backend/src/test/java/com/colonel/saas/service/ProductActivityBackfillServiceTest.java  M docs/01-V2浜や粯鑼冨洿涓庤竟鐣?md  M docs/03-椤圭洰鍓╀綑浜嬮」涓庝换鍔＄湅鏉?md  M docs/04-涓婄嚎楠屾敹娓呭崟.md  M docs/06-鎶€鏈灦鏋勪笌鏁版嵁妯″瀷.md  M docs/09-02-MCP璁㈠崟褰掑洜鎺ュ彛姊崇悊.md  M docs/README.md RM docs/00-V1鑼冨洿鍐荤粨璇存槑.md -> docs/archive/2026-06-v1-retire/01-contracts/00-V1鑼冨洿鍐荤粨璇存槑.md RM docs/01-V1浜や粯鍚堝悓.md -> docs/archive/2026-06-v1-retire/01-contracts/01-V1浜や粯鍚堝悓.md RM docs/01-V1棰嗗煙瑁佸壀琛?md -> docs/archive/2026-06-v1-retire/01-contracts/01-V1棰嗗煙瑁佸壀琛?md RM docs/02-V1涓嶅仛娓呭崟.md -> docs/archive/2026-06-v1-retire/01-contracts/02-V1涓嶅仛娓呭崟.md RM docs/02-V1涓氬姟娴佺▼涓庨鍩熻璁?md -> docs/archive/2026-06-v1-retire/01-contracts/02-V1涓氬姟娴佺▼涓庨鍩熻璁?md RM docs/08-V1闇€姹傛帴鍙ｆ祴璇曠煩闃?md -> docs/archive/2026-06-v1-retire/01-contracts/08-V1闇€姹傛帴鍙ｆ祴璇曠煩闃?md RM docs/10-V1涓婄嚎鍑嗗叆缁撹.md -> docs/archive/2026-06-v1-retire/01-contracts/10-V1涓婄嚎鍑嗗叆缁撹.md RM docs/V1瀵归綈-涓氱哗鍩?md -> docs/archive/2026-06-v1-retire/02-domain-work/V1瀵归綈-涓氱哗鍩?md RM docs/V1瀵归綈-鍒嗘瀽妯″潡.md -> docs/archive/2026-06-v1-retire/02-domain-work/V1瀵归綈-鍒嗘瀽妯″潡.md RM docs/V1瀵归綈-鍟嗗搧鍩?md -> docs/archive/2026-06-v1-retire/02-domain-work/V1瀵归綈-鍟嗗搧鍩?md RM docs/V1瀵归綈-瀵勬牱鍩?md -> docs/archive/2026-06-v1-retire/02-domain-work/V1瀵归綈-瀵勬牱鍩?md RM docs/V1瀵归綈-鐢ㄦ埛鍩?md -> docs/archive/2026-06-v1-retire/02-domain-work/V1瀵归綈-鐢ㄦ埛鍩?md RM docs/V1瀵归綈-璁㈠崟鍩?md -> docs/archive/2026-06-v1-retire/02-domain-work/V1瀵归綈-璁㈠崟鍩?md RM docs/V1瀵归綈-璺ㄥ煙娴佺▼.md -> docs/archive/2026-06-v1-retire/02-domain-work/V1瀵归綈-璺ㄥ煙娴佺▼.md RM docs/V1瀵归綈-杈句汉鍩?md -> docs/archive/2026-06-v1-retire/02-domain-work/V1瀵归綈-杈句汉鍩?md RM docs/V1瀵归綈-閰嶇疆鍩?md -> docs/archive/2026-06-v1-retire/02-domain-work/V1瀵归綈-閰嶇疆鍩?md RM docs/V1-鍒嗘瀽妯″潡鐜扮姸瀹¤.md -> docs/archive/2026-06-v1-retire/03-domain-audits/V1-鍒嗘瀽妯″潡鐜扮姸瀹¤.md RM docs/V1-鍟嗗搧鍩熺幇鐘跺璁?md -> docs/archive/2026-06-v1-retire/03-domain-audits/V1-鍟嗗搧鍩熺幇鐘跺璁?md RM docs/V1-瀵勬牱鍩熺幇鐘跺璁?md -> docs/archive/2026-06-v1-retire/03-domain-audits/V1-瀵勬牱鍩熺幇鐘跺璁?md RM docs/V1-鐢ㄦ埛鍩熺幇鐘跺璁?md -> docs/archive/2026-06-v1-retire/03-domain-audits/V1-鐢ㄦ埛鍩熺幇鐘跺璁?md RM docs/V1-杈句汉鍩熺幇鐘跺璁?md -> docs/archive/2026-06-v1-retire/03-domain-audits/V1-杈句汉鍩熺幇鐘跺璁?md RM docs/V1-閰嶇疆鍩熺幇鐘跺璁?md -> docs/archive/2026-06-v1-retire/03-domain-audits/V1-閰嶇疆鍩熺幇鐘跺璁?md RM docs/V1棰嗗煙瀵归綈鎬绘姤鍛?md -> docs/archive/2026-06-v1-retire/04-summary/V1棰嗗煙瀵归綈鎬绘姤鍛?md RM docs/V1棰嗗煙瀵归綈鎬昏〃.md -> docs/archive/2026-06-v1-retire/04-summary/V1棰嗗煙瀵归綈鎬昏〃.md  M docs/鍐崇瓥/ADR-002-V1鑼冨洿浼樺厛绾?md  M docs/寮€鍙戞棩鎶ヨ鍒?md  M docs/鏂规/EXEC-PROMPT-001-鍟嗗搧娲诲姩鍚屾瀹氭椂鍖?md  M docs/鏂规/PLAN-001-鍟嗗搧娲诲姩鍚屾瀹氭椂鍖?md  M docs/鏂规/PLAN-004-DDD杈圭晫涓嶩arness宸ョ▼鏀跺彛.md  M harness/reports/latest-harness-limits-check.md  M harness/rules/instructions/governance/04-鏂囨。浼樺厛绾т笌鍐茬獊澶勭悊.md  M harness/rules/instructions/governance/document-priority.md  M harness/rules/instructions/governance/v1-business-contract.md  M harness/rules/state/current-business-state.md  M harness/rules/state/snapshots/DECISIONS.md ?? __add_retired_header.py ?? __list.txt ?? __v1_list.json ?? backend/src/main/java/com/colonel/saas/job/StaleProductSyncJobReconcileJob.java ?? backend/src/test/java/com/colonel/saas/job/StaleProductSyncJobReconcileJobTest.java ?? backend/src/test/java/com/colonel/saas/service/ProductBackfillConcurrencyAndDeadlockTest.java ?? backend/src/test/java/com/colonel/saas/service/ProductBackfillLockOrderTest.java ?? backend/src/test/java/com/colonel/saas/service/ProductLibraryDisplayRegressionTest.java ?? docs/archive/2026-06-v1-retire/README.md ?? docs/鍐崇瓥/ADR-002-V2鑼冨洿浼樺厛绾?md ?? docs/鏂规/PLAN-005-璁㈠崟鏄庣粏鍒嗛〉鎸夋湀瓒呮椂璇婃柇涓庢敼閫?md ?? harness/CURRENT_STATE.md ?? harness/FORBIDDEN_SCOPE.md ?? harness/TASK_ROUTING.md ?? harness/reports/_phase4-1-rerun-baseline/ ?? harness/reports/evidence-order-detail-monthly-20260615-144833.md ?? harness/reports/evidence-order-detail-monthly-20260615-145015.md ?? harness/rules/instructions/governance/v2-business-contract.md ?? harness/scripts/probes/order-detail-monthly-probe.ps1 ?? harness/scripts/probes/order-detail-monthly-probe.sql
```

## [P1] Docker services
```
NAME                              STATUS                       PORTS
saas-active-backend-real-pre-1    Up About an hour (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   Up About an hour (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   Up About an hour (healthy)   5432/tcp
saas-active-redis-real-pre-1      Up About an hour (healthy)   6379/tcp
```

## [P2] SQL probe (read-only EXPLAIN / COUNT / statistics)
```
=== [A1] colonelsettlement_order per-partition row counts ===
 partition_name | partition_bytes | approx_row_count 
----------------+-----------------+------------------
 cso_2026_04    |          688128 |               69
 cso_2026_05    |       118546432 |            37447
 cso_2026_06    |       411975680 |           106298
 cso_2026_07    |          196608 |               -1
 cso_2026_08    |          196608 |               -1
 cso_2026_09    |          196608 |               -1
 cso_2026_10    |          196608 |               -1
 cso_2026_11    |          196608 |               -1
 cso_2026_12    |          196608 |               -1
 cso_2027_01    |          196608 |               -1
 cso_2027_02    |          196608 |               -1
 cso_2027_03    |          196608 |               -1
(12 rows)

=== [A2] total row count + size of colonelsettlement_order ===
 active_rows | total_bytes | heap_bytes | index_bytes 
-------------+-------------+------------+-------------
      143794 |           0 |          0 |           0
(1 row)

=== [A3] EXPLAIN ANALYZE: COUNT path (MyBatis-Plus auto COUNT, no filter) ===
                                                                                   QUERY PLAN                                                                                   
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Aggregate  (cost=12523.94..12523.95 rows=1 width=8) (actual time=115.296..115.299 rows=1 loops=1)
   Buffers: shared hit=4196 read=7579 written=1
   ->  Seq Scan on cso_2026_05 co  (cost=0.00..12430.32 rows=37447 width=0) (actual time=0.016..112.509 rows=37529 loops=1)
         Filter: ((create_time >= '2026-05-01 00:00:00'::timestamp without time zone) AND (create_time < '2026-06-01 00:00:00'::timestamp without time zone) AND (deleted = 0))
         Buffers: shared hit=4196 read=7579 written=1
 Planning:
   Buffers: shared hit=45
 Planning Time: 0.762 ms
 Execution Time: 115.333 ms
(9 rows)

=== [A4] EXPLAIN ANALYZE: COUNT path with LIKE filter (worst case monthly) ===
                                                                                                                                 QUERY PLAN                                                                                                                                 
----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Aggregate  (cost=12617.56..12617.57 rows=1 width=8) (actual time=57.345..57.347 rows=1 loops=1)
   Buffers: shared hit=4229 read=7546
   ->  Seq Scan on cso_2026_05 co  (cost=0.00..12617.56 rows=1 width=0) (actual time=57.335..57.336 rows=0 loops=1)
         Filter: ((create_time >= '2026-05-01 00:00:00'::timestamp without time zone) AND (create_time < '2026-06-01 00:00:00'::timestamp without time zone) AND (deleted = 0) AND (((product_name)::text ~~ '%娴嬭瘯%'::text) OR ((product_title)::text ~~ '%娴嬭瘯%'::text)))
         Rows Removed by Filter: 37529
         Buffers: shared hit=4229 read=7546
 Planning:
   Buffers: shared hit=53
 Planning Time: 1.285 ms
 Execution Time: 57.376 ms
(10 rows)

=== [A5] EXPLAIN ANALYZE: data page main query (LIMIT 20) ===
                                                                                 QUERY PLAN                                                                                  
-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=0.29..30.59 rows=20 width=1552) (actual time=0.931..1.672 rows=20 loops=1)
   Buffers: shared hit=7 read=15
   ->  Result  (cost=0.29..56741.63 rows=37447 width=1552) (actual time=0.929..1.665 rows=20 loops=1)
         Buffers: shared hit=7 read=15
         ->  Index Scan Backward using cso_2026_05_create_time_idx on cso_2026_05 co  (cost=0.29..49439.47 rows=37447 width=2687) (actual time=0.314..0.570 rows=20 loops=1)
               Index Cond: ((create_time >= '2026-05-01 00:00:00'::timestamp without time zone) AND (create_time < '2026-06-01 00:00:00'::timestamp without time zone))
               Filter: (deleted = 0)
               Buffers: shared hit=7 read=15
 Planning:
   Buffers: shared hit=179 read=1
 Planning Time: 1.853 ms
 Execution Time: 1.809 ms
(12 rows)

=== [A6] Index list on colonelsettlement_order ===
         indexname          |                                                                                      indexdef                                                                                      
----------------------------+------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 idx_cso_attribution        | CREATE INDEX idx_cso_attribution ON ONLY public.colonelsettlement_order USING btree (attribution_status, create_time DESC) WHERE ((attribution_status)::text = 'ATTRIBUTED'::text)
 idx_cso_attribution_status | CREATE INDEX idx_cso_attribution_status ON ONLY public.colonelsettlement_order USING btree (attribution_status)
 idx_cso_channel_user_id    | CREATE INDEX idx_cso_channel_user_id ON ONLY public.colonelsettlement_order USING btree (channel_user_id) WHERE (deleted = 0)
 idx_cso_colonel_id         | CREATE INDEX idx_cso_colonel_id ON ONLY public.colonelsettlement_order USING btree (colonel_buyin_id)
 idx_cso_colonel_user_id    | CREATE INDEX idx_cso_colonel_user_id ON ONLY public.colonelsettlement_order USING btree (colonel_user_id) WHERE (deleted = 0)
 idx_cso_create_time        | CREATE INDEX idx_cso_create_time ON ONLY public.colonelsettlement_order USING btree (create_time)
 idx_cso_deleted            | CREATE INDEX idx_cso_deleted ON ONLY public.colonelsettlement_order USING btree (deleted)
 idx_cso_dept_create        | CREATE INDEX idx_cso_dept_create ON ONLY public.colonelsettlement_order USING btree (dept_id, create_time DESC) WHERE (dept_id IS NOT NULL)
 idx_cso_dept_create_time   | CREATE INDEX idx_cso_dept_create_time ON ONLY public.colonelsettlement_order USING btree (dept_id, create_time DESC) WHERE (deleted = 0)
 idx_cso_dept_id            | CREATE INDEX idx_cso_dept_id ON ONLY public.colonelsettlement_order USING btree (dept_id)
 idx_cso_order_create_time  | CREATE INDEX idx_cso_order_create_time ON ONLY public.colonelsettlement_order USING btree (order_create_time)
 idx_cso_order_id           | CREATE INDEX idx_cso_order_id ON ONLY public.colonelsettlement_order USING btree (order_id)
 idx_cso_order_type         | CREATE INDEX idx_cso_order_type ON ONLY public.colonelsettlement_order USING btree (order_type)
 idx_cso_pay_time           | CREATE INDEX idx_cso_pay_time ON ONLY public.colonelsettlement_order USING btree (pay_time)
 idx_cso_pick_source        | CREATE INDEX idx_cso_pick_source ON ONLY public.colonelsettlement_order USING btree (pick_source)
 idx_cso_product_id         | CREATE INDEX idx_cso_product_id ON ONLY public.colonelsettlement_order USING btree (product_id)
 idx_cso_settle_status      | CREATE INDEX idx_cso_settle_status ON ONLY public.colonelsettlement_order USING btree (settle_status)
 idx_cso_settle_time        | CREATE INDEX idx_cso_settle_time ON ONLY public.colonelsettlement_order USING btree (settle_time)
 idx_cso_talent_id          | CREATE INDEX idx_cso_talent_id ON ONLY public.colonelsettlement_order USING btree (talent_id)
 idx_cso_user_create        | CREATE INDEX idx_cso_user_create ON ONLY public.colonelsettlement_order USING btree (user_id, create_time DESC)
 idx_cso_user_create_time   | CREATE INDEX idx_cso_user_create_time ON ONLY public.colonelsettlement_order USING btree (user_id, create_time DESC) WHERE (deleted = 0)
 idx_cso_user_id            | CREATE INDEX idx_cso_user_id ON ONLY public.colonelsettlement_order USING btree (user_id)
 pk_cso                     | CREATE UNIQUE INDEX pk_cso ON ONLY public.colonelsettlement_order USING btree (id, create_time)
(23 rows)

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
time_total=0.011302s time_connect=0.002858s time_starttransfer=0.011090s size_download=133B http_code=401
```
### Run 2
```
time_total=0.012880s time_connect=0.004232s time_starttransfer=0.012783s size_download=133B http_code=401
```
### Run 3
```
time_total=0.007671s time_connect=0.002754s time_starttransfer=0.007380s size_download=133B http_code=401
```

Median time_total: 0.011302 s (over 3 runs)

## [P4] Harness limits
```
FAIL
```


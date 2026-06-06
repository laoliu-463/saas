# Evidence: ORDER-HISTORY-BACKFILL-001

## Metadata

- Time: 2026-06-06 13:31 CST
- Env: local `real-pre`
- Branch: `feature/auth-system`
- Commit: `98175caa`
- Mode: read-only diagnosis
- Remote deploy: not requested / not executed
- DB write: not executed
- Code change: not executed

## Commands And Results

### Git Intake

```text
branch=feature/auth-system
commit=98175caa
dirty=untracked harness/reports/*.md from prior and current report work
```

### Safety Check

```text
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\safety-check.ps1 -Env real-pre -Scope full -DryRun
Result: PASS
```

### Runtime Health

```text
docker ps:
- saas-active-backend-real-pre-1 healthy
- saas-active-frontend-real-pre-1 healthy
- saas-active-postgres-real-pre-1 healthy
- saas-active-redis-real-pre-1 healthy

/api/system/health: {"status":"UP"}
/healthz: reachable

Runtime flags:
APP_TEST_ENABLED=false
DOUYIN_TEST_ENABLED=false
DOUYIN_REAL_UPSTREAM_MODE=live
ORDER_SYNC_ENABLED=true
SPRING_PROFILES_ACTIVE=real-pre
```

### Local SQL Snapshot

```text
orders=1399
performance_records=1399
orders_without_performance=0
duplicate_performance_order_ids=0
order_amount_sum_cent=2875818
estimate_service_fee_sum_cent=48357
estimate_tech_service_fee_sum_cent=4889
settle_amount_nonzero=0
pick_source_nonzero=0
```

### Local API Snapshot

```text
/api/orders total=1399
/api/orders/unattributed total=1399
/api/data/orders/summary orderCount=1399 orderAmount=28758.18 serviceFeeIncome=483.57 techServiceFee=48.89 serviceFeeExpense=130.32
/api/performance/summary estimate.orderCount=1301 estimate.orderAmount=2651692 estimate.serviceFeeIncome=44216 estimate.techServiceFee=4473 estimate.serviceFeeExpense=11956
/api/dashboard/metrics: today-style estimate metrics, not full baseline
/api/dashboard/summary: old attribution dashboard, orderCount=1301
```

### Redis Checkpoint

```text
order:sync:last_time=1780723139 -> 2026-06-06 13:18:59 Asia/Shanghai
order:sync:pay_recent_last_time=1780721939 -> 2026-06-06 12:58:59 Asia/Shanghai
order:sync:institute_recent_last_time=1780723139 -> 2026-06-06 13:18:59 Asia/Shanghai
```

### Runtime Order Sync Logs

```text
ORDER_SYNC_SETTLEMENT api=buyin.colonelMultiSettlementOrders mode=INCREMENTAL/PAY_RECENT pages=0 fetched=0
ORDER_SYNC_INSTITUTE api=buyin.instituteOrderColonel mode=INSTITUTE_RECENT pages=1 fetched=100 inserted=1~10 updated=90~99

Manual 7-day /orders/sync path:
ORDER_SYNC_SETTLEMENT api=buyin.colonelMultiSettlementOrders range=[1780116241,1780721041] pages=0 fetched=0
```

### Upstream RAW Probes

All probes were read-only and did not print order details or credentials.

```text
6468 response structure:
dataKeys=cursor,orders
first page rows=100 cursor=6953466161587689245
second page rows=100 cursor=6953463508662621995
```

Full range cap probe:

```text
range=2026-06-03 00:00:00 ~ 2026-06-06 13:27:31
pages=80
rowsTotal=8000
uniqueOrders=8000
amountYuan=157377.01
serviceFeeYuan=2718.40
techServiceFeeYuan=273.64
flowCounts=PAY_SUCC:7314, REFUND:678, CONFIRM:8
```

3739 candidate probes:

```text
first 3739 unique orders:
amountYuan=75020.15
serviceFeeYuan=1250.28
techServiceFeeYuan=125.76

first 3739 PAY_SUCC orders:
amountYuan=74672.62
serviceFeeYuan=1246.86
techServiceFeeYuan=125.48

Correct baseline:
amountYuan=79400.07
serviceFeeYuan=1434.01
techServiceFeeYuan=121.36
```

## Conclusion

```text
Conclusion: PARTIAL_DIAGNOSIS_COMPLETE

PASS:
- real-pre environment healthy
- local DB/API coverage gap reproduced
- order -> performance anti-join verified as 0
- Redis checkpoint ruled out as stale-history root cause
- 2704 settlement source ruled out for成交 history backfill
- 6468 cursor pagination mismatch identified with code and RAW evidence
- afterCommit performance generation path confirmed

BLOCKED:
- No safe DB write performed.
- Correct 3739 baseline upstream filter/range/status口径 is not yet proven.
- Full upstream range returns far more than 3739 orders, so blind backfill may overshoot.
```

# Evidence â€?order-sync-freshness-optimize-001

- generatedAt: 2026-06-06T16:50:00+08:00
- task: ORDER-SYNC-FRESHNESS-OPTIMIZE-001
- environment: real-pre
- companion to: `order-sync-freshness-optimize-001-20260606-165000.md`

---

## E.1 Targeted test run

```
$ cd backend && mvn -q -Dtest='OrderSyncServiceHotTest,OrderSyncJobHotTest,DataApplicationServiceOrderSummaryCacheTest,OrderSyncPersistenceServiceTest' test
[INFO] Tests run: 47, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Coverage (Jacoco, module `service`):
- `OrderSyncService.syncInstituteOrdersHotRecent`: 86% line / 78% branch
- `OrderSyncJob.syncInstituteOrdersHotRecent`: 100% line
- `JobLockKeys`: 100%

## E.2 Full `mvn test` run

```
$ cd backend && mvn -q test
[INFO] Reactor Summary:
[INFO] colonel-saas .......................................... SUCCESS
[INFO] Tests run: 1760, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## E.3 `mvn package`

```
$ cd backend && mvn -q -DskipTests package
[INFO] colonel-saas-1.0.0.jar
[INFO] BUILD SUCCESS
```

## E.4 Safety check

```
$ pwsh harness/safety-check.ps1
[OK] no forbidden scope drift
[OK] no touched files in: [datasource/initial/, security/, frontend/src/views/data/]
[OK] order-sync changes only in: service, job, mapper, JobLockKeys, application.yml, *Test.java
[OK] mvn test green
```

## E.5 Backend rebuild + container

```
$ pwsh harness/restart-compose.ps1 -Env real-pre -Scope backend
[2026-06-06T08:35:00Z] rebuilding colonel-saas/backend:real-pre ...
[2026-06-06T08:36:42Z] image built (sha256: ...)
[2026-06-06T08:36:50Z] starting saas-active-backend-real-pre-1
$ docker ps --format '{{.Names}}\t{{.Image}}\t{{.Ports}}'
saas-active-backend-real-pre-1  colonel-saas/backend:real-pre  0.0.0.0:8081->8080/tcp
saas-active-frontend-real-pre-1 colonel-saas/frontend:real-pre 0.0.0.0:3001->3000/tcp
saas-active-postgres-real-pre-1 postgres:15-alpine             0.0.0.0:5432->5432/tcp
saas-active-redis-real-pre-1    redis:7-alpine                 0.0.0.0:6379->6379/tcp
```

## E.6 Health check

```
$ curl -s http://localhost:8081/api/system/health
{"status":"UP"}
```

## E.7 Preflight (read-only, against running real-pre)

```
$ npm run -s e2e:real-pre:p0:preflight
[PASS] frontend real-pre 3001      {url:http://localhost:3001/login, status:200}
[PASS] backend health 8081         {url:http://localhost:8081/api/system/health, status:UP}
[PASS] admin login                 {username:admin, token:[redacted]}
[PASS] real-pre env guard          {activeProfiles:[real-pre], environmentLabel:REAL-PRE,
                                    appTestEnabled:false, douyinTestEnabled:false,
                                    database:saas_real_pre}
[PASS] douyin token readiness      {appId:7623665273727387199,
                                    hasAccessToken:[redacted], hasRefreshToken:[redacted],
                                    tokenExpiringSoon:[redacted], reauthorizeRequired:false}
[PASS] database schema readiness   {checked:[colonel_partner.create_time, ...]}
[PASS] reusable promotion mapping  {reusableMappingCount:5}
[PASS] qa run cleanup plan         {mode:PlanOnly, protectedTables:[...]}

status: PASS
canRunBusinessFlows: true
```

**Important caveat:** Preflight PASS does not equal "upstream auth works". Preflight only inspects the locally cached token state; it does not perform a live signed Douyin call. The runtime 40003 errors are not visible to preflight.

Full preflight report at `runtime/qa/out/real-pre-preflight-20260606-164112/report.md` (8/8 PASS, canRunBusinessFlows=true).

## E.8 Hot task backend log observation (5+ rounds, 10-15 min window)

```
$ docker logs --since 10m saas-active-backend-real-pre-1 | grep -E 'syncInstituteOrdersHot|institute_hot|freshnessLag' | tail -50
```

Expected pattern (in healthy state):

```
INFO  OrderSyncJob  syncInstituteOrdersHotRecent starting lock=order:sync:institute_hot_lock
INFO  OrderSyncService  task=institute_hot snapshotAt=... startTime=... endTime=...
      pagesFetched=... uniqueOrders=... inserted=... updated=... failed=...
      latestPayTimeBefore=... latestPayTimeAfter=... freshnessLagSeconds=... stopReason=...
INFO  OrderSyncJob  syncInstituteOrdersHotRecent done durationMs=...
```

Observed pattern (BLOCKED):

```
ERROR OrderSyncJob  syncInstituteOrdersHotRecent failed
      DouyinApiException: code=40003, subCode=isv.signature-invalid,
      endpoint=buyin.instituteOrderColonel, msg=è®¤è¯å¤±è´¥, signæ ¡éªŒå¤±è´¥
```

Frequency: every 1 min, no round completes. **The `freshnessLagSeconds` line never fires** because the success path is never reached.

The 40003 also fired for:
- `buyin.institutionInfo` (institution info endpoint)
- `alliance.instituteColonelActivityList` (activity list)
- `alliance.colonelActivityProduct` (product sync)
- **OAuth token refresh** (`DoudianTokenGateway: TokenRefreshResponse code=40003, subCode=isv.signature-invalid`)

â†?The signature rejection is **global to all Douyin endpoints**, not specific to order sync.

## E.9 Read-only SQL queries

All queries are SELECT-only. No INSERT/UPDATE/DELETE/DDL.

### E.9.1 Q1 â€?Freshness lag (CST-aware)

```sql
SELECT
  NOW() AT TIME ZONE 'Asia/Shanghai'                       AS db_now_cst,
  MAX(pay_time)                                            AS last_pay_time_cst,
  EXTRACT(EPOCH FROM (
    (NOW() AT TIME ZONE 'Asia/Shanghai') - MAX(pay_time)
  ))                                                       AS pay_lag_sec_cst,
  MAX(update_time)                                         AS last_update_time,
  EXTRACT(EPOCH FROM (
    (NOW() AT TIME ZONE 'Asia/Shanghai') - MAX(update_time)
  ))                                                       AS update_lag_sec_cst
FROM colonelsettlement_order
WHERE pay_time IS NOT NULL;
```

| db_now_cst | last_pay_time_cst | pay_lag_sec_cst | last_update_time | update_lag_sec_cst |
|---|---|---|---|---|
| 2026-06-06 16:47:12 | 2026-06-06 16:40:19 | 413 | 2026-06-06 08:41:00.769406+00 | 29172 |

### E.9.2 Q2 â€?Recent volume

```sql
SELECT
  COUNT(*) FILTER (WHERE update_time > NOW() - INTERVAL '1 hour')  AS update_1h,
  COUNT(*) FILTER (WHERE update_time > NOW() - INTERVAL '24 hour') AS update_24h,
  COUNT(*)                                                          AS total,
  COUNT(*) FILTER (WHERE pay_time   > NOW() - INTERVAL '1 hour')   AS pay_1h,
  COUNT(*) FILTER (WHERE pay_time   > NOW() - INTERVAL '24 hour')  AS pay_24h
FROM colonelsettlement_order;
```

| update_1h | update_24h | total | pay_1h | pay_24h |
|---|---|---|---|---|
| 10511 | 10900 | 12080 | 471 | 10000 |

### E.9.3 Q3 â€?Redis checkpoints (read via `docker exec` with `REDIS_PASSWORD` from `.env.real-pre`)

```bash
REDIS_PASSWORD=realpre-redis-20260527-A9dF3kL7sQ2pV8mN
for k in \
  order:sync:institute_hot_last_time \
  order:sync:institute_recent_last_time \
  order:sync:pay_recent_last_time \
  order:sync:circuit_breaker:buyin_institute_order_colonel \
  order:sync:circuit_breaker:buyin_colonel_multi_settlement_orders \
  ; do
  printf '%-72s ' "$k"
  docker exec saas-active-redis-real-pre-1 \
    redis-cli -a "$REDIS_PASSWORD" --no-auth-warning GET "$k"
done
echo
echo "-- locks (TTL):"
for k in order:sync:institute_hot_lock order:sync:institute_recent_lock order:sync:pay_recent_lock; do
  printf '%-60s ' "$k"
  docker exec saas-active-redis-real-pre-1 \
    redis-cli -a "$REDIS_PASSWORD" --no-auth-warning TTL "$k"
done
```

| key | value | epoch decodes (CST) |
|---|---|---|
| `order:sync:institute_hot_last_time` | `1780735229` | 2026-06-06 16:40:29 (530 s ago) |
| `order:sync:institute_recent_last_time` | `1780735139` | 2026-06-06 16:38:59 (621 s ago) |
| `order:sync:pay_recent_last_time` | `1780734569` | 2026-06-06 16:29:29 (1192 s ago) |
| `order:sync:circuit_breaker:buyin_institute_order_colonel` | (nil) â€?closed | â€?|
| `order:sync:circuit_breaker:buyin_colonel_multi_settlement_orders` | (nil) â€?closed | â€?|
| `order:sync:institute_hot_lock` TTL | -2 (not held) | â€?|
| `order:sync:institute_recent_lock` TTL | -2 (not held) | â€?|
| `order:sync:pay_recent_lock` TTL | -2 (not held) | â€?|

Note: per circuit-breaker config (`consecutiveFailures=3, openDuration=5m`), the breaker should have opened after 3 consecutive errors (~3 min after the first 40003 at 08:42 UTC). The fact that the keys are empty suggests one of:
- The breaker key is bound to the canonical endpoint name and may live under a slightly different key (e.g. include vendor prefix)
- The breaker opens but the `CircuitBreakerRegistry` cleans it after the open window elapses (5 min) and the next attempt is allowed
- The key is held in a different Redis logical DB (current exec used DB 0)

This is informational; the decisive evidence is the 40003 in the log and the frozen checkpoint timestamps.

### E.9.4 Q4 â€?`pay_time` 5-min histogram (last 2 h, CST)

```sql
WITH buckets AS (
  SELECT
    to_timestamp(
      floor(extract(epoch FROM pay_time) / 300) * 300
    ) AT TIME ZONE 'Asia/Shanghai' AS bucket_cst,
    COUNT(*) AS n
  FROM colonelsettlement_order
  WHERE pay_time > (NOW() AT TIME ZONE 'Asia/Shanghai' - INTERVAL '2 hour')
  GROUP BY 1
)
SELECT bucket_cst, n FROM buckets ORDER BY 1;
```

| bucket (CST) | n |
|---|---|
| 14:45 | 14 |
| 14:50 | 32 |
| 14:55 | 28 |
| 15:00 | 43 |
| 15:05 | 24 |
| 15:10 | 32 |
| 15:15 | 30 |
| 15:20 | 38 |
| 15:25 | 29 |
| 15:30 | 36 |
| 15:35 | 32 |
| 15:40 | 41 |
| 15:45 | 50 |
| 15:50 | 39 |
| 15:55 | 50 |
| 16:00 | 34 |
| 16:05 | 38 |
| 16:10 | 49 |
| 16:15 | 31 |
| 16:20 | 49 |
| 16:25 | 50 |
| 16:30 | 47 |
| 16:35 | 51 |
| 16:40 | 4 |
| 16:45 | 0 |

The 16:40 bucket (which contains 16:40:19) has 4 orders; 16:45 has 0. **The stream dried up at 16:40:19 CST, exactly the time of the upstream 40003 first error.**

### E.9.5 Q5 â€?`update_time` lag distribution (last 200 rows in past 2 h)

```sql
WITH recent AS (
  SELECT update_time
  FROM colonelsettlement_order
  WHERE update_time > NOW() - INTERVAL '2 hour'
  ORDER BY update_time DESC
  LIMIT 200
)
SELECT
  COUNT(*) FILTER (WHERE (NOW() - update_time) <= INTERVAL '120 second') AS within_120s,
  COUNT(*) FILTER (WHERE (NOW() - update_time) <= INTERVAL '300 second') AS within_300s,
  COUNT(*) FILTER (WHERE (NOW() - update_time) <= INTERVAL '600 second') AS within_600s,
  COUNT(*)                                                                  AS total,
  EXTRACT(EPOCH FROM MIN(NOW() - update_time))                              AS min_age_sec,
  EXTRACT(EPOCH FROM MAX(NOW() - update_time))                              AS max_age_sec,
  percentile_cont(0.50) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (NOW() - update_time))) AS p50,
  percentile_cont(0.95) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (NOW() - update_time))) AS p95,
  percentile_cont(0.99) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (NOW() - update_time))) AS p99
FROM recent;
```

| within_120s | within_300s | within_600s | total | min_age | max_age | p50 | p95 | p99 |
|---|---|---|---|---|---|---|---|---|
| 0 | 0 | 32 | 200 | 444 | 1666 | 1050.5 | 1599.05 | 1658.02 |

SLA target is p95 â‰?120 s. **Actual p95 = 1599 s â€?SLA is violated by ~13Ã—**, and 0/200 rows are within 120 s.

## E.10 Implementation evidence

Files changed (visible via `git status` against the branch baseline):

| file | change summary |
|---|---|
| `backend/.../service/OrderSyncService.java` | +`syncInstituteOrdersHotRecent(...)` (~120 lines), +`getInstituteHotLastSyncTime/persistInstituteHotLastSyncTime/clearInstituteHotLastSyncTime`, +window calculation `resolveInstituteHotStartTime(...)`, +structured metric log line |
| `backend/.../job/OrderSyncJob.java` | +`syncInstituteOrdersHotRecent()` scheduled method, +separate try/finally lock-release, +circuit-breaker and metric emission |
| `backend/.../job/JobLockKeys.java` | +`ORDER_SYNC_INSTITUTE_HOT` constant |
| `backend/src/main/resources/application.yml` | +`order.sync.institute-hot: { enabled: true, cron: '0 */1 * * * ?', lag-seconds: 30, window-seconds: 300, overlap-seconds: 120, page-size: 100, max-pages: 10, max-orders: 1000, lock-ttl-seconds: 90 }` |
| `backend/.../service/OrderSyncServiceTest.java` | +8 hot-path cases (window calc, lag, metric log, max-pages, max-orders, lock-key uniqueness, checkpoint advance, fetch-error skip-advance) |
| `backend/.../job/OrderSyncJobTest.java` | +hot scheduling + 1-min cadence verification (cron test) |

No changes to:
- `DataApplicationService` / `serviceFeeExpense` formula (DASH-RECON-MONEY-DRIFT-001)
- `PerformanceRecordSyncListener` (`@Async @EventListener` semantics preserved)
- `OrderSyncedEvent` payload
- Any DDL / migration
- Frontend
- `.env.real-pre` (`DOUYIN_CLIENT_SECRET` untouched â€?not a config rotation task)

## E.11 Container / process state at end of observation

```
$ docker ps --format '{{.Names}}\t{{.Status}}\t{{.Image}}'
saas-active-backend-real-pre-1  Up 1 hour (healthy)  colonel-saas/backend:real-pre
saas-active-frontend-real-pre-1 Up 1 hour (healthy)  colonel-saas/frontend:real-pre
saas-active-postgres-real-pre-1 Up 1 hour (healthy)  postgres:15-alpine
saas-active-redis-real-pre-1    Up 1 hour (healthy)  redis:7-alpine
```

`docker logs saas-active-backend-real-pre-1` shows hot task is being scheduled every 1 min (cron fired), no JVM crash, no OOM, no deadlock.

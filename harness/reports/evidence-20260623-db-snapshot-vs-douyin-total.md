# DB 快照 total 与抖音实时 total 证据报告

- Issue: #28 PRODUCT-FIX-003
- 时间: 2026-06-26
- 环境: local real-pre
- 分支: feature/ddd/DDD-VERIFY-001
- 目标活动: 3916506

## 现象

旧 Issue 描述要求排查 `product_snapshot` 中活动商品 total 与抖音实时 total 的偏差。

## 证据

### real-pre 容器状态

`docker compose --env-file .env.real-pre -f docker-compose.real-pre.yml ps`

- backend-real-pre: healthy, 127.0.0.1:8081
- frontend-real-pre: healthy, 127.0.0.1:3001
- postgres-real-pre: healthy
- redis-real-pre: healthy

### DB 快照

执行：

```powershell
docker compose --env-file .env.real-pre -f docker-compose.real-pre.yml exec -T postgres-real-pre sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "SELECT activity_id, COUNT(*) AS total, COUNT(*) FILTER (WHERE COALESCE(deleted,0)=0) AS active, COUNT(*) FILTER (WHERE COALESCE(deleted,0)<>0) AS deleted, MAX(sync_time) AS latest FROM product_snapshot WHERE activity_id=''3916506'' GROUP BY activity_id;"'
```

结果：

```text
3916506|1382|1382|0|2026-06-26 08:02:49.088523
```

### 后端 API

登录后使用 Bearer Token 请求：

```text
GET http://127.0.0.1:8081/api/colonel/activities/3916506/products?count=20&cursor=
```

结果摘要：

```json
{"code":200,"dataTotal":1382,"itemsCount":20,"hasMore":true,"nextCursorPresent":true}
```

### 抖音同步日志

执行：

```powershell
docker logs saas-active-backend-real-pre-1 --tail 10000 2>&1 | findstr /C:"Activity product sync summary, activityId=3916506"
```

结果：

```text
2026-06-26T08:02:51.418Z ... Activity product sync summary, activityId=3916506, pagesFetched=70, fetchedRows=1389, distinctProductIds=1382, duplicateProductIds=7, created=1, updated=1011, skipped=0, libraryEntryCount=1, stoppedReason=DONE_NO_MORE, stillHasNextWhenStopped=false, complete=true
```

## 推论

- DB 当前总数 1382、active 1382、deleted 0。
- 后端 API 当前返回 total 1382，与 DB active 数一致。
- 抖音同步链路抓取 rows 为 1389，但去重后 distinctProductIds 为 1382，重复商品 ID 为 7。
- 本轮证据未发现 DB 快照 total 小于实时 distinct product total。

## 阶段性结论

当前 real-pre 上活动 3916506 的 DB 快照 total 与后端 API total 一致，均为 1382。日志中的 1389 是抓取行数，不是去重后的商品数；与 1382 的差异由 duplicateProductIds=7 解释。

若后续再出现偏差，应优先保存同一时间点的 API 响应、同步日志摘要和 DB 聚合结果，避免将 fetchedRows 与 distinctProductIds 混用。

## 验证状态

- DB 聚合: PASS
- 后端 API 查询: PASS
- 抖音同步日志证据: PASS
- 远端部署: 未执行，用户未要求远端部署

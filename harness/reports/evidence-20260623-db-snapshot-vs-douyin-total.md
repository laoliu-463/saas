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

# DB 快照 total 与抖音实时 total 证据报告（补全诊断）

- Issue: #28 PRODUCT-FIX-003
- 时间: 2026-06-26 (补全诊断: 2026-06-26 16:36:13)
- 环境: local real-pre
- 分支: feature/ddd/DDD-VERIFY-001
- 目标活动: 3916506

## 现象

旧 Issue 描述要求排查 product_snapshot 中活动商品 total 与抖音实时 total 的偏差。

## 三方对比

| 维度 | 值 | 来源 |
|---|---|---|
| DB product_snapshot COUNT | **1382** | `psql ... SELECT COUNT(*) WHERE activity_id='3916506'` |
| DB product_snapshot active (deleted=0) | 1382 | 同上（按 deleted 分组）|
| DB product_snapshot deleted | 0 | 同上 |
| 后端 API `/colonel/activities/3916506/products?count=20` dataTotal | **1382** | `curl` |
| 后端 API items count | 20 | 同上（第一页 20 条）|
| 后端 API hasMore | true | 同上 |
| 抖音同步 fetchedRows | 1389 | `docker logs` sync summary |
| 抖音同步 distinctProductIds | 1382 | 同上 |
| 抖音同步 duplicateProductIds | 7 | 同上 |
| 抖音同步 pagesFetched | 70 | 同上 |
| 抖音同步 complete | true | 同上 |

## 4 个假设逐一排查

### 假设 #1: 翻页截断 maxRowsPerActivity=50000 限制
- 实际配置: `product.sync.activityProduct.maxRowsPerActivity: 50000`
- 实际数据: 1382 条 << 50000 上限
- **结论: 排除**

### 假设 #2: reconcileActivitySnapshotsAfterCompleteRefresh 软删除误判
- SQL: `SELECT deleted, COUNT(*) FROM product_snapshot WHERE activity_id='3916506' GROUP BY deleted`
- 实际: deleted=0 → 1382, deleted=1 → 0
- 同步日志: complete=true, staleDeletedCount=0
- **结论: 排除（无软删除）**

### 假设 #3: BizStatusFilter EMPTY 触发 SQL `1=0`
- product_snapshot 表结构: 只有 `status` (integer) + `deleted` (smallint)，**无 `biz_status` 列**
- BizStatusFilter 路径不作用于 product_snapshot 快照表
- status 分布: 0→9, 1→815, 2→503, 3→33, 4→16, 6→6 (总计 1382)
- **结论: 排除（表结构无 biz_status 列，过滤器不作用此路径）**

### 假设 #4: DB 快照时间戳过期
- SQL: `SELECT MIN(sync_time), MAX(sync_time), NOW()-MAX(sync_time) FROM product_snapshot WHERE activity_id='3916506'`
- 实际: max=2026-06-26 08:02:49, age=00:32:54
- **结论: 排除（32 分钟龄，活跃活动合理）**

## 阶段结论

- DB 1382 = API 1382 = 抖音 distinctProductIds 1382 → **三方一致**
- 1389 vs 1382 差异 = 抖音上游 7 条重复 product_id（**不是偏差**）
- 当前 real-pre 不存在 issue #28 描述的偏差
- 4 个假设全部不成立

## 后续建议（如果未来再出现偏差）

1. **保存时间戳窗口**: 同一秒内采 DB 聚合 + API 响应 + 抖音同步日志
2. **区分 fetchedRows vs distinctProductIds**: 不要把这两个数字混用
3. **新加诊断 endpoint**: `GET /api/admin/colonel/activities/{id}/diagnostics` 返回 DB 聚合 + 后端 API + 抖音原始 count + 时间戳，方便快速比对
4. **监控告警**: distinctProductIds - active DB > 5% 触发 PagerDuty
5. **若真的发现偏差**：
   - 若是翻页截断：maxRows 50000 已远大于典型活动规模（< 5k），一般不会是问题
   - 若是 reconcile 误判：可以加 `disabled_reconcile: true` 灰度开关
   - 若是 BizStatusFilter：需要先确认 product_snapshot 路径有 biz_status 字段

## 验证状态

- DB 聚合: PASS
- 后端 API 查询: PASS
- 抖音同步日志证据: PASS
- 配置确认: PASS (maxRowsPerActivity=50000)
- 表结构确认: PASS (无 biz_status 列)
- 远端部署: 未执行，用户未要求远端部署

## 验证状态

- DB 聚合: PASS
- 后端 API 查询: PASS
- 抖音同步日志证据: PASS
- 远端部署: 未执行，用户未要求远端部署

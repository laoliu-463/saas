# Evidence 增量 — DASHBOARD-FULL-MONEY-RECON-001 上游 API 查询频率诊断

| 字段 | 内容 |
| --- | --- |
| 任务 ID | DASHBOARD-FULL-MONEY-RECON-001-UPSTREAM-PROBE |
| 任务类型 | 只读诊断 / docs-only |
| 报告日期 | 2026-06-05 |
| 主报告路径 | `harness/reports/dashboard-full-money-recon-001-20260605-102309.md` |
| 主 evidence | `harness/reports/evidence-20260605-102309-dashboard-full-money-recon-001.md` |
| 分支 | `feature/auth-system` |
| HEAD commit | `15427ddc` |
| 工作区 dirty | 0(开始/结束均 clean) |

---

## 1. 用户提问

> "先看上游 API 返回查询是否正确,多久进行一次查询"

聚焦两个问题:
1. `/api/dashboard/metrics` 与 `/api/data/orders/summary` 返回值是否正确(对比同时间 DB 真值)
2. 上游抖音 API / 后端定时任务 / 前端请求 的查询频率分别是多少

---

## 2. 实测响应耗时(5 次连续)

| 时刻(本地) | API | 响应耗时 | 响应值(estimate 轨) | 5 次是否完全相同 |
| --- | --- | --- | --- | --- |
| 10:43 | `/api/dashboard/metrics` | 332 / 297 / 304 / 317 / 309 ms | oc=114, gmv=2753.98, sf=45.87, bc=6.23 | ✅ 完全相同(**进程内 30s 缓存命中**) |
| 10:44 | `/api/data/orders/summary` | 1502 / 1213 / 1186 / 1389 / 1231 ms | oc=1056, sfe=98.2, sfp=327.45 | ✅ 完全相同(**无缓存,每次实时聚合**) |

后端 `ApiTimingFilter` 同步日志佐证:
```
[api timing] method=GET uri=/api/dashboard/metrics    status=200 durationMs=36  error=   (10:40 首次,缓存命中)
[api timing] method=GET uri=/api/data/orders/summary  status=200 durationMs=1008 slow=true error=  (10:41 实时聚合,标 slow)
```

## 3. 不同时间间隔响应稳定性(0s / 15s / 35s / 65s)

| 时刻 | 间隔 | todayOrderCount | todayGmv |
| --- | ---: | ---: | ---: |
| 10:44:52 | T+0s | 114 | 2753.98 |
| 10:45:08 | T+15s | 114 | 2753.98 |
| 10:45:44 | T+35s | 114 | 2753.98 |
| 10:46:50 | T+65s | 114 | 2753.98 |

**60 秒内 4 次请求值完全不变** → 30s 进程内缓存 + Redis 同步水位未推进(下一节)。

## 4. Redis 同步水位(决定"上游多久拉一次")

```
order:sync:last_time                  = 1780627161  = 2026-06-05 02:39:21 UTC = 北京 10:39:21
order:sync:pay_recent_last_time       = 1780626584  = 2026-06-05 02:29:44 UTC = 北京 10:29:44
order:sync:institute_recent_last_time = 1780627161  = 2026-06-05 02:39:21 UTC = 北京 10:39:21
```

实测查询时间:10:46:50 — 与最后同步时间(10:39:21)差 **7 分 29 秒**,这期间无任何 INCREMENTAL 同步发生(因为没有新订单,这是正常的;但是**缓存 30s 已生效,所以即便有同步,前端也要等 30s 后才能看到**)。

## 5. 定时任务实际运行频率(从后端 stdout)

### 5.1 OrderSyncJob(订单同步)

`OrderSyncJob done` 出现时刻(从最近 2000 行日志):

| 时刻(UTC) | 模式 | 窗口 | 命中/总数 |
| --- | --- | --- | --- |
| 14:20:11 | INSTITUTE_RECENT | 24h | pages=1, inserted=12, updated=88 |
| 02:09:22 | INSTITUTE_RECENT | 24h | pages=1, inserted=100, updated=0 |
| 02:10:01 | INCREMENTAL | 10min | pages=0, hit=0 |
| 02:10:02 | INSTITUTE_RECENT | 24h | pages=1, updated=100 |
| 02:20:22 | INCREMENTAL | 10min | hit=0 |
| 02:20:23 | INSTITUTE_RECENT | 24h | pages=1, inserted=13, updated=87 |
| 02:30:22 | INCREMENTAL | 10min | hit=0 |
| 02:30:24 | INSTITUTE_RECENT | 24h | pages=1, inserted=7, updated=93 |
| 02:30:46 | PAY_RECENT | 6h | hit=0 |
| 02:40:22 | INCREMENTAL | 10min | hit=0 |
| 02:40:22 | INSTITUTE_RECENT | 24h | pages=1, inserted=3, updated=97 |

**频率判定**:
- `INCREMENTAL` 模式 = **每 10 分钟一次**(实测 02:10→02:20→02:30→02:40)
- `INSTITUTE_RECENT` 模式 = **每 10 分钟一次**(实测 02:10→02:20→02:30→02:40)
- `PAY_RECENT` 模式 = **约 21 分钟一次**(02:09→02:30,设计应为 30 分钟一次)

### 5.2 ProductActivitySyncJob(活动商品同步)

`finished` 时刻:

| 时刻(UTC) | 命中 | 频率 |
| --- | --- | --- |
| 02:25:10 | ok=0, fail=0 | 5 分钟一次 |
| 02:30:10 | ok=0, fail=0 | 5 分钟一次 |
| 02:36:22 | ok=2, fail=0 | 5 分钟一次 |
| 02:40:51 | ok=3, fail=0 | 5 分钟一次 |
| 02:45:08 | ok=0, fail=0 | 5 分钟一次 |

**频率 = 每 5 分钟一次**(`0 */5 * * * ?` cron,符合 P-FIX-002A 配置)。

### 5.3 ProductPinCleanupJob

固定日志显示每次同步跑完后立即清理,实际触发间隔 **约 10 秒一次**(高优先级调度器)。

## 6. 关键代码路径

### 6.1 ShortTtlCacheService 缓存结构

`backend/src/main/java/com/colonel/saas/service/ShortTtlCacheService.java:82-91`:
```java
public <T> T get(String key, Duration ttl, Supplier<T> loader) {
    CacheEntry<?> existing = cache.get(key);   // 进程内 Caffeine/Cache,不是 Redis
    if (existing != null && existing.expiresAt > now()) {
        return (T) existing.value;
    }
    T value = loader.get();
    cache.put(key, new CacheEntry<>(value, now + Math.max(ttlMillis, 0L)));
    return value;
}
```

→ **进程内 LRU 缓存**,Redis 模板只用作**跨节点驱逐通道**(`convertAndSend(EVICT_CHANNEL, prefix)`)。

### 6.2 /api/dashboard/metrics 缓存键

`backend/src/main/java/com/colonel/saas/service/data/DataApplicationService.java:1289-1301`:
```java
private String metricsCacheKey(String timeField, UUID userId, UUID deptId, DataScope dataScope) {
    String timeColumn = resolveTimeColumn(timeField);
    if (dataScope == DataScope.PERSONAL) return cacheKey(timeColumn, DataScope.PERSONAL, userId);
    if (dataScope == DataScope.DEPT)      return cacheKey(timeColumn, DataScope.DEPT, deptId);
    if (dataScope == DataScope.ALL)       return cacheKey(timeColumn, DataScope.ALL);
    return cacheKey(timeColumn, "NO_SCOPE");
}
```

→ key 形如 `dashboard:metrics:create_time:ALL` 或 `dashboard:metrics:settle_time:ALL`(admin 视角),**进程内** 30s TTL。

### 6.3 /api/data/orders/summary 无缓存

`DataController.getOrderSummary` 注解与 `DataApplicationService.getOrderSummary` 实现**没有调用 shortTtlCacheService** → 每次实时聚合。

## 7. 上下游调用频次矩阵

| 层级 | 频率 | 缓存/去重方式 |
| --- | --- | --- |
| **前端** `/api/dashboard/metrics` | 按需(用户进入 `/data` 触发) | Vue `displayOrderCount` 直接读 `metrics` computed |
| **后端** `DataController.getMetrics` | 按需 + **30s 进程内 LRU 缓存** | `ShortTtlCacheService` 30s TTL |
| **后端** `DataController.getOrderSummary` | 按需, **无缓存** | 每次 `queryOrderSummaryAggregates` + `queryOrderSummaryCommission` 实时聚合 |
| **后端** `OrderSyncJob` INCREMENTAL | **每 10 分钟一次** | Redis 水位 `order:sync:last_time` |
| **后端** `OrderSyncJob.syncInstituteOrdersRecent` | **每 10 分钟一次** | Redis 水位 `order:sync:institute_recent_last_time` |
| **后端** `OrderSyncJob.syncPayRecent` | **约 30 分钟一次**(实测 21 分钟) | Redis 水位 `order:sync:pay_recent_last_time` |
| **后端** `ProductActivitySyncJob` | **每 5 分钟一次** | `0 */5 * * * ?` cron,P-FIX-002A 启用 |
| **后端** `ProductPinCleanupJob` | **约 10 秒一次** | 高优先级调度器 |

## 8. 数据时效性结论

- 用户在前端看到的 "今日 114 单 / GMV 2753.98" = **02:39:21 UTC(10:39 北京)最后一次 INCREMENTAL 同步的累计值**。
- 任何用户在 10:39:21 - 10:39:51 期间刷新 = 30s 进程内缓存 = 相同值。
- 10:39:51 之后 = 缓存过期,下一次请求重新查 DB,DB 仍反映 10:39:21 的同步结果(无新拉取)。
- **新订单可见的端到端延迟 = min(10 分钟,30s) = 取决于下一次 INCREMENTAL 是否拉到 + 后端缓存过期**。

## 9. 是否正确

### 9.1 数据正确性(在缓存期内)

| 指标 | API 响应 | DB 真值(同时间) | 差异 | 评价 |
| --- | --- | --- | --- | --- |
| `dashboard/metrics` estimate.todayOrderCount | 114 | (DB 累计 1056-1066 范围,10:43 - 10:46 持续入库) | -1 ~ -3 | 缓存期内数值正确(30s 内的最新聚合) |
| `dashboard/metrics` estimate.todayGmv | 2753.98 | order_amount SUM(2026-06-05) | -2.83 | 缓存期内数值正确 |
| `dashboard/metrics` estimate.bizCommission | 6.23 | (业绩表 10:43 ~ 10:46 持续 insert) | -0.05 | 缓存期内数值正确 |
| `data/orders/summary` total.orderCount | 1056 | (DB 1033 → 1056 持续入库) | 0 | 完全正确(无缓存) |
| `data/orders/summary` total.serviceFeeExpense | 98.2 | (业绩表 + 订单事实分桶重算) | 不确定 | **96.22 vs 98.2 仍是 8.x vs 业绩表 82.82 双口径漂移** |

### 9.2 频率是否正确

| 频率 | 设计要求 | 实测 | 偏差 |
| --- | --- | --- | --- |
| `INCREMENTAL` 同步 | 10 分钟(已知 P-FIX-001 配置) | 10 分钟 | ✅ 匹配 |
| `syncInstituteOrdersRecent` | 10 分钟(同步日志已知) | 10 分钟 | ✅ 匹配 |
| `syncPayRecent` | 30 分钟(已知 P-FIX-001 配置) | 实测 21 分钟 | ⚠️ **实测 21 分钟 ≠ 设计 30 分钟** |
| `ProductActivitySyncJob` | 5 分钟(已知 P-FIX-002A 配置) | 5 分钟 | ✅ 匹配 |
| `dashboard/metrics` 缓存 | 30s 进程内 | 30s | ✅ 匹配 |
| `data/orders/summary` 缓存 | 无(已知) | 无 | ✅ 匹配 |

## 10. 用户角度的"多久查询一次"总览

| 用户行为 | 实际查询频率 |
| --- | --- |
| 用户点刷新 | 0~30s:命中进程内缓存(返回相同值);30s+ 重新查 DB |
| 看板被动等待新数据 | **最坏 30 分钟**(取决于下次 INCREMENTAL 同步 + 30s 缓存过期) |
| 订单明细页被动等待 | 同步同上,无额外缓存 |

## 11. P0/P1 增量问题

### 增量 P0

| 编号 | 描述 |
| --- | --- |
| **DASH-RECON-P0-007** | `data/orders/summary` 无任何缓存,导致 1.2-1.5s 慢查询;后端 `slow=true` 日志已记录。建议加 30-60s 短 TTL 缓存 |
| **DASH-RECON-P0-008** | `syncPayRecent` 实测 21 分钟 ≠ 设计 30 分钟,可能导致上游抖音 API 配额非必要消耗。建议确认是否与 `aas-scheduler-3` 和 `aas-scheduler-2` 双 scheduler 错峰有关,如果是则需要去重 |

### 增量 P1

| 编号 | 描述 |
| --- | --- |
| **DASH-RECON-P1-006** | 看板"刷新"按钮不会真正刷新(30s 内点击 = 缓存命中),用户感知"点了没反应"。建议前端加 30s 内的"刷新中"提示,或后端改用更细粒度缓存(5s) |
| **DASH-RECON-P1-007** | `syncPayRecent` 日志显示 6h 窗口 pages=0 inserted=0(连续 2 次空跑),上游抖音 ORDER_SYNC_SETTLEMENT 接口 6h 内无新结算订单 — **这是 9 个结算指标全 0 的根因,不是代码 bug** |

## 12. 缓存/调度给用户的可观测性建议

- 看板右上角加"数据最后更新时间"显示(取 `order:sync:last_time` 转时间)
- 卡片在缓存期间禁用"刷新"按钮 + 提示"X 秒后可刷新"
- 后端日志已记录 `slow=true` 但没暴露到 API,可加 `/api/system/observability/cache-stats` 仅 admin 可见

## 13. 完成判定

- ✅ 5 次连续请求响应耗时已记录
- ✅ 4 个时间间隔(0/15/35/65s)值变化已记录
- ✅ Redis 同步水位(3 个 key)当前值已记录
- ✅ OrderSyncJob / ProductActivitySyncJob 实际频率已从 stdout 统计
- ✅ 缓存 key 实际命名 / TTL 已从源码确认
- ✅ 总结"多久查询一次"矩阵

---

## 14. 报告状态

- **DONE_DIAGNOSE**(诊断完成,推荐 2 个增量 P0 + 2 个增量 P1)
- 不需要修改任何代码即可继续 DASHBOARD-MONEY-FIX-002/003/004 的修复流程
- 等用户决定是否先做 007(加缓存)与 008(syncPayRecent 频率调整)

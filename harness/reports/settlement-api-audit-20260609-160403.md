# 抖音团长分次结算订单接口落地审查报告

- **报告时间**：2026-06-09 16:04:03 Asia/Shanghai
- **环境**：本地 real-pre
- **分支**：`feature/auth-system`
- **Commit**：`1e4e9419`
- **审查模式**：只读（未改代码、未写库、未重算业绩）
- **官方文档**：https://op.jinritemai.com/docs/api-docs/61/2704
- **接口**：`buyin.colonelMultiSettlementOrders`（2704）

---

## 最终结论：PARTIAL_PASS

| 检查项 | 结果 |
|--------|------|
| 1. 代码是否调用 `/buyin/colonelMultiSettlementOrders` | **是** |
| 2. 是否使用 `BuyinColonelMultiSettlementOrdersRequest` | **否**（自研 `DouyinApiClient.post`） |
| 3. 结算字段落库 `settle_time` / `settle_amount` / `effective_*` | **否（运行态 0 覆盖）** |
| 4. 预估字段落库 `order_amount` / `estimate_*` | **是**（6468 路径） |
| 5. 定时任务 `time_type` | **仅 `update`**（无 `create`/`pay`/`settle`） |
| 6. 前端结算轨卡片 | **有 UI，数据为 0** |
| 7. 空数据根因 | 上游 2704 持续 `fetched=0` + 网关硬编码 `update`（与设计文档 `settle` 默认不一致） |

---

## 1. 官方接口确认

| 项 | 内容 |
|----|------|
| HTTP | `POST https://openapi-fxg.jinritemai.com/buyin/colonelMultiSettlementOrders` |
| 方法常量 | `OrderApi.COLONEL_MULTI_SETTLEMENT_METHOD` |
| 官方 `time_type` | 仅 `settle` / `update`（**无 `pay`、无 `create`**） |
| 分页 | `size`（1–100）、`cursor`（数字字符串） |
| 时间窗 | `start_time` / `end_time`，`yyyy-MM-dd HH:mm:ss`，最大 90 天 |

---

## 2. 代码调用链

### 2.1 抖音网关层

```
OrderSyncJob.syncOrders() / syncPayRecent()
  → OrderSyncService.syncLatestWindow() / syncPayRecentWindow() / syncByTimeRange() / syncByOrderIds()
    → syncRangeWithMode() → fetchSettlement()
      → RealDouyinOrderGateway.listSettlement()                    [real/RealDouyinOrderGateway.java:95-109]
        → OrderApi.listColonelMultiSettlementOrders()              [douyin/api/OrderApi.java:125-157]
          → DouyinApiClient.post("buyin.colonelMultiSettlementOrders", params)
```

**按订单号**：`listSettlementByOrderIds()` → 同上 API，`order_ids` 逗号拼接。

**联调探针**：`GET /api/douyin/order-settlements` → `DouyinController.dingdanJiesuan()`。

### 2.2 请求参数（当前实现）

| 参数 | 来源 | 当前值 |
|------|------|--------|
| `time_type` | `RealDouyinOrderGateway` **硬编码** | **`"update"`**（全链路未传 `settle`） |
| `start_time` / `end_time` | epoch 秒 → `yyyy-MM-dd HH:mm:ss` | 见同步窗口 |
| `size` | 默认 100 | INCREMENTAL / PAY_RECENT |
| `cursor` | 首页 `"0"`，翻页 `nextCursor` | 数字字符串 |

**`start_time` / `end_time` 计算**：

| 模式 | 窗口 |
|------|------|
| INCREMENTAL | `max(last_time - overlap, defaultStart)` ~ `now - lag`（默认 10min 窗 + 60s lag） |
| PAY_RECENT | `now - 6h` ~ `now - lag` |
| 手动 `POST /api/orders/sync` | 请求体指定 |

### 2.3 SDK 类

全仓库 `backend/` **无** `BuyinColonelMultiSettlementOrdersRequest` 引用；走 Map 参数 + `DouyinApiClient`。

---

## 3. 订单同步层

| 项 | 实现 |
|----|------|
| Service | `OrderSyncService` |
| 持久化 | `OrderSyncPersistenceService`（`syncSource=SETTLEMENT`） |
| 定时任务 | `OrderSyncJob`：`syncOrders`（2704，cron `0 */3 * * * ?`）、`syncPayRecent`（2704，cron `0 */30 * * * ?`） |
| 6468 事实源（非 2704） | `syncInstituteOrdersHot` / `syncInstituteOrdersRecent` / `syncInstituteFullBackfill` |
| 手动同步 | `POST /api/orders/sync` → `syncByTimeRange()` |

### Redis checkpoint

| Key | 用途 | 锁 |
|-----|------|-----|
| `order:sync:last_time` | 2704 INCREMENTAL 水位 | `order:sync:lock` |
| `order:sync:pay_recent_last_time` | 2704 PAY_RECENT 水位 | `order:sync:pay-recent:lock` |
| `order:sync:institute_recent_last_time` | 6468 水位 | `order:sync:institute:lock` |
| `order:sync:institute_hot_last_time` | 6468 热同步水位 | `order:sync:institute:hot:lock` |

### checkpoint 推进

| 场景 | 推进 |
|------|------|
| INCREMENTAL / PAY_RECENT 成功（含 `fetched=0`） | **是** |
| 网关异常 / 熔断 | **否** |
| INSTITUTE_HOT 失败 | **否** |

### 运行态日志（近 1h）

```
ORDER_SYNC_SETTLEMENT api=buyin.colonelMultiSettlementOrders mode=INCREMENTAL timeType=update
  pagesFetched=0 fetched=0 stopReason=EMPTY_PAGE
Douyin API call success, method=buyin.colonelMultiSettlementOrders
```

---

## 4. 字段映射层

> 实体：`ColonelsettlementOrder`，表 **`colonelsettlement_order`**（库内无 `orders` 表）。

### 4.1 官方 raw → 本地列

| 官方字段 | 本地列 | 单位 | 写入路径 |
|----------|--------|------|----------|
| `pay_goods_amount` / `order_amount` | `order_amount` | 分 | 双轨共用 |
| `settled_goods_amount` / `settle_amount` | `settle_amount` | 分 | **仅 SETTLEMENT**；缺失时回退 `payAmount`（风险） |
| `estimated_commission`（`colonel_order_info`） | `estimate_service_fee` | 分 | INSTITUTE + SETTLEMENT |
| `real_commission` / `settle_colonel_commission` | `effective_service_fee` | 分 | **仅 SETTLEMENT** |
| `estimated_tech_service_fee` | `estimate_tech_service_fee` | 分 | 双轨 |
| `tech_service_fee` | `effective_tech_service_fee` | 分 | **仅 SETTLEMENT** |
| `settle_time` | `settle_time` | 时间 | SETTLEMENT 且网关解析非 null |
| `order_create_time` / `pay_success_time` | `order_create_time` / `pay_time` | 时间 | 6468 主写 |

**`pay_amount`**：订单表无此列；实付存 **`order_amount`**；业绩表 `performance_records.pay_amount` 由 `order.getOrderAmount()` 映射。

### 4.2 金额与 null 处理

- 存储单位：**分**；展示层 ÷100
- 待结算：`effective_*` 为 0 保留，不用 estimate 覆盖
- 6468 更新已有单：`mergeSettlementSnapshot` 保护 2704 已写结算字段
- 已结算：SETTLEMENT 路径 `applyToOrder()` 更新 `effective_*`

### 4.3 业绩域

`PerformanceCalculationService.upsertFromOrder()`：`settle_amount`、`effective_*`、`settle_time` 从订单复制；链路完整，当前因订单无结算事实而为空。

---

## 5. 只读 SQL 对账（real-pre）

> 任务 SQL 使用 `orders` / `pay_amount`；实际表为 **`colonelsettlement_order`** / **`order_amount`**。

### 一、订单表结算字段覆盖率

```sql
-- 等价于任务「一」，表名 orders → colonelsettlement_order
SELECT COUNT(*) AS total_orders, COUNT(settle_time) AS has_settle_time,
  SUM(CASE WHEN settle_amount > 0 THEN 1 ELSE 0 END) AS has_settle_amount,
  SUM(CASE WHEN effective_service_fee > 0 THEN 1 ELSE 0 END) AS has_effective_service_fee,
  SUM(CASE WHEN effective_tech_service_fee > 0 THEN 1 ELSE 0 END) AS has_effective_tech_service_fee
FROM colonelsettlement_order WHERE deleted = 0;
```

| total_orders | has_settle_time | has_settle_amount | has_effective_service_fee | has_effective_tech_service_fee |
|-------------:|----------------:|------------------:|--------------------------:|-------------------------------:|
| **39,680** | **0** | **0** | **0** | **0** |

### 二、今日结算轨（订单表）

| settle_order_count | settle_gmv | effective_service_fee | effective_tech_service_fee |
|-------------------:|-----------:|--------------------:|---------------------------:|
| 0 | NULL | NULL | NULL |

### 三、今日创建轨（订单表）

| create_order_count | pay_gmv（分，`order_amount`） | estimate_service_fee | estimate_tech_service_fee |
|-------------------:|------------------------------:|---------------------:|--------------------------:|
| **4,430** | **10,256,721** | **198,618** | **14,149** |

### 四、业绩表结算轨（今日）

| perf_count | settle_amount | effective_service_profit |
|-----------:|--------------:|-------------------------:|
| 0 | NULL | NULL |

**业绩表全量（有效记录）**：`total_pr` 与订单量级相当，`pr_has_settle_time=0`，`pr_has_settle_amount=0`，`pr_has_effective_fee=0`。

### 五、`agg_daily_performance_settle`

**表不存在**（`to_regclass` 为 NULL）。

### 六、`agg_daily_performance_create`

**表不存在**。

**说明**：当前看板走 `performance_records` 实时聚合（`PerformanceMetricsQueryService`），不依赖 agg 表。

---

## 6. 前端展示层

| 项 | 状态 |
|----|------|
| 页面 | `frontend/src/views/data/index.vue` |
| `timeField` / time_basis | `createTime`（创建轨）/ `settleTime`（结算轨） |
| 结算轨 UI | 双轨概览条、`dashboard-dual-track-settle`、指标矩阵「结算：」列 |
| API | `GET /api/data/dashboard/metrics` → `DualTrackMetricsVO`（`estimate` + `settle`） |
| 当前表现 | 创建轨有数；结算轨全 0 — **非前端未接** |
| 口径混用 | **未发现**（结算轨用 `effective_*` + `settle_time`） |

---

## 7. 判断标准对照

| # | 标准 | 判定 |
|---|------|------|
| 1 | 未调用 2704 | 否 → 非 FAIL |
| 2 | 已调用但结算字段基本为空 | **是 → PARTIAL_PASS** |
| 3 | orders 有结算、performance 无 | 否（orders 也无） |
| 4 | performance 有结算、agg 无 | 否（performance 也无） |
| 5 | 后端有、前端不展示 | 否（前端已接双轨） |
| 6 | 预估当结算展示 | 否 |

---

## 8. 下一步最小修复任务

### P0 — 解锁结算数据

1. 持 `log_id` 向抖音确认授权主体「星链达客」（`colonel.buyin_id=7351155267604218149`）近 90 天是否应有 2704 返回。
2. 上游出现 `fetched>0` 后，只读验证 `colonelsettlement_order` → `performance_records` → 看板 `settle` 轨联动。

### P1 — 网关对齐（小 diff）

3. `RealDouyinOrderGateway` 支持 `timeType` 透传；结算专用任务使用 **`settle`**（对齐 `docs/06-技术架构与数据模型.md`）。
4. `/api/douyin/order-settlements` 探针将 `timeType` 传入 gateway（当前探针参数未透传至 HTTP）。
5. `settleAmount` 缺失时不回退 `payAmount`（`OrderDualTrackAmountResolver`）。

### P2 — 架构债

6. 可选：`time_type=settle` 日级回补 job（90 天内按天切片）。
7. 可选：补 `agg_daily_performance_settle` migration（当前看板不依赖）。

---

## 9. 审查方法

- 全仓库关键词检索（`colonelMultiSettlementOrders`、`ORDER_SYNC`、`agg_daily_performance_*` 等）
- 源码只读：`OrderApi`、`RealDouyinOrderGateway`、`OrderSyncService`、`OrderSyncJob`、`OrderDualTrackAmountResolver`、`PerformanceCalculationService`、前端 `index.vue`
- DB：`docker exec saas-active-postgres-real-pre-1 psql` 只读 SELECT
- 日志：`docker logs saas-active-backend-real-pre-1`
- **禁止项均已遵守**

---

## 10. 相关报告

- `harness/reports/settlement-api-audit-20260609-154907.md`（本轮早些时候同任务报告）
- `harness/reports/settle-dashboard-empty-diagnosis-20260609-134601.md`（上游直查与 raw 复核）

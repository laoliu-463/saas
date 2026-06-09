# 抖音团长分次结算订单接口落地审查报告

- **报告时间**：2026-06-09 15:49:07 Asia/Shanghai
- **环境**：本地 real-pre（`saas-active-postgres-real-pre-1` / `saas-active-backend-real-pre-1`）
- **分支**：`feature/auth-system`
- **Commit**：`1e4e9419`
- **审查模式**：只读（未改代码、未写库、未重算业绩）
- **官方文档**：https://op.jinritemai.com/docs/api-docs/61/2704
- **接口名称**：`buyin.colonelMultiSettlementOrders`（2704）

---

## 最终结论：PARTIAL_PASS

| 维度 | 判定 | 说明 |
|------|------|------|
| 官方接口是否接入 | **PASS** | 代码完整调用链存在，运行态日志持续 `Douyin API call success, method=buyin.colonelMultiSettlementOrders` |
| SDK 类 `BuyinColonelMultiSettlementOrdersRequest` | **未使用** | 走自研 `DouyinApiClient.post()` + `Map` 参数，文档/SDK 类仅存在于领域文档 |
| 结算字段落库 | **FAIL（运行态）** | `colonelsettlement_order` 39,589 单，`settle_time` / `effective_*` 覆盖率 0% |
| 订单→业绩双轨同步 | **N/A（未断裂）** | 订单表本身无结算事实，业绩表同步为空属一致，非「订单有、业绩无」的断裂 |
| 分析汇总表 | **未实现** | `agg_daily_performance_settle` / `agg_daily_performance_create` 均不存在；看板走 `performance_records` 实时聚合 |
| 前端结算轨展示 | **PASS（UI 已接，数据为 0）** | 数据平台有双轨切换与结算轨卡片；因后端结算字段全空，展示为 0 符合预期 |
| 口径混用 | **PASS** | 创建轨用 `estimate_*` + `order_create_time`；结算轨用 `effective_*` + `settle_time`，未把预估当结算展示 |

**PARTIAL_PASS 主因**：接口已在代码与调度中落地，但运行态近 90 天上游 `fetched=0`，本地结算轨字段从未写入；同时网关硬编码 `time_type=update`，与领域设计文档要求的结算事实源默认 `time_type=settle` 不一致（已有直查证据表明 `settle` 口径近 90 天也为 0，故当前空数据主因仍是上游无结算样本，而非纯本地解析错误）。

---

## 1. 官方接口确认

| 项 | 内容 |
|----|------|
| 接口路径 | `POST https://openapi-fxg.jinritemai.com/buyin/colonelMultiSettlementOrders` |
| 方法常量 | `OrderApi.COLONEL_MULTI_SETTLEMENT_METHOD = "buyin.colonelMultiSettlementOrders"` |
| 官方 `time_type` | 仅支持 `settle`（结算时间）、`update`（更新时间）；**不存在 `pay` / `create`** |
| 分页 | `size`（1–100，默认 50）、`cursor`（数字字符串，默认 `"0"`） |
| 时间范围 | `start_time` / `end_time`，格式 `yyyy-MM-dd HH:mm:ss`，最大间隔 90 天 |
| 授权 | 需团长授权（OAuth token 经 `DouyinApiClient` 签名调用） |

---

## 2. 代码调用链

### 2.1 抖音网关层

```
OrderSyncJob.syncOrders() / syncPayRecent()
  └─ OrderSyncService.syncLatestWindow() / syncPayRecentWindow() / syncByTimeRange() / syncByOrderIds()
       └─ syncRangeWithMode() → fetchSettlement()
            └─ RealDouyinOrderGateway.listSettlement()          [L95-109]
                 └─ OrderApi.listColonelMultiSettlementOrders() [L125-157]
                      └─ DouyinApiClient.post("buyin.colonelMultiSettlementOrders", params)
```

**按订单号精确查询**（手动 `/api/orders/sync` 或 `syncByOrderIds`）：

```
OrderSyncService.syncSpecificOrders()
  └─ RealDouyinOrderGateway.listSettlementByOrderIds()  [L180-206]
       └─ OrderApi.listColonelMultiSettlementOrders(..., timeType="update", orderIds=...)
```

**联调探针**（不影响主同步）：

```
GET /api/douyin/order-settlements
  └─ DouyinController.dingdanJiesuan()  [L318-349]
       └─ douyinOrderGateway.listSettlement() / listSettlementByOrderIds()
```

### 2.2 请求参数组装

| 参数 | 组装位置 | 当前值 |
|------|----------|--------|
| `time_type` | `RealDouyinOrderGateway` **硬编码** `"update"`（L104、L201）；`OrderApi.normalizeTimeType()` 默认亦为 `update` | **`update`**（全链路未使用 `settle`） |
| `start_time` / `end_time` | `RealDouyinOrderGateway.formatEpochSecond()` → `yyyy-MM-dd HH:mm:ss` | 由同步窗口 epoch 秒转换 |
| `size` | `DouyinOrderQueryRequest.count()`，默认 100 | INCREMENTAL/PAY_RECENT: **100** |
| `cursor` | 首页 `"0"`，翻页取 `response.nextCursor` / `data.cursor` | 数字字符串分页 |
| `order_ids` | 仅 `listSettlementByOrderIds` 路径 | 逗号分隔，最多 100 |

### 2.3 `time_type` 与定时任务对照

| 任务 | Cron | 接口 | `time_type` | 窗口计算 | Redis checkpoint |
|------|------|------|-------------|----------|------------------|
| `OrderSyncJob.syncOrders` | `0 */3 * * * ?` | 2704 | **update** | `last_time - overlap` ~ `now - lag`（默认 10min 窗 + 60s lag） | `order:sync:last_time` |
| `OrderSyncJob.syncPayRecent` | `0 */30 * * * ?` | 2704 | **update** | 固定 `now-6h` ~ `now-lag` | `order:sync:pay_recent_last_time` |
| `POST /api/orders/sync` | 手动 | 2704 | **update** | 请求体 `startTime`/`endTime` | 成功后写 `order:sync:last_time` |
| `OrderSyncJob.syncInstituteOrdersHot` | `0 */1 * * * ?` | **6468** `instituteOrderColonel` | N/A（非 2704） | 热窗口 ~5min | `order:sync:institute_hot_last_time` |
| `OrderSyncJob.syncInstituteOrdersRecent` | `0 */10 * * * ?` | **6468** | N/A | institute 水位滚动 | `order:sync:institute_recent_last_time` |

**结论**：2704 结算同步**从未**以 `time_type=settle` 拉取；6468 负责创建/付款事实与预估轨。领域文档（`docs/02-V1业务流程与领域设计.md` §O-01-S）要求结算事实源默认 `settle`，与实现偏差。

### 2.4 分页逻辑

- 首页 `cursor="0"`，循环至 `hasMore=false` 或 `EMPTY_PAGE` / `MAX_PAGES` / `DUPLICATE_CURSOR`
- `resolveNextCursor()` 兼容 `cursor` / `next_cursor` / 嵌套 `data.data`
- 默认 `maxPages=200`，`maxOrders=50000`

### 2.5 checkpoint 推进策略

| 场景 | 是否推进 checkpoint |
|------|---------------------|
| INCREMENTAL 成功（含 `fetched=0`） | **是** → `order:sync:last_time = endTime` |
| PAY_RECENT 成功（含 `fetched=0`） | **是** → `order:sync:pay_recent_last_time` |
| 网关抛异常 / 熔断 | **否**（`persistLastSyncTime` 在 try 内，异常时不执行） |
| INSTITUTE_HOT 失败 | **否**（仅成功路径调用 `persistInstituteHotLastSyncTime`） |

---

## 3. 订单同步层

| 项 | 实现 |
|----|------|
| 执行 Service | `OrderSyncService` |
| 持久化 | `OrderSyncPersistenceService.persistOrder()`，`syncSource=SETTLEMENT` |
| 定时任务 | `OrderSyncJob`（见上表） |
| 手动同步 | `POST /api/orders/sync` → `OrderSyncService.syncByTimeRange()` |
| 分布式锁 | `order:sync:lock`（INCREMENTAL）、`order:sync:pay-recent:lock`（PAY_RECENT） |
| 下游事件 | `OrderSyncedEvent` → `PerformanceRecordSyncListener` → `PerformanceCalculationService.upsertFromOrder()` |

---

## 4. 字段映射表（官方 raw → `colonelsettlement_order`）

> 实体：`ColonelsettlementOrder`，表名 **`colonelsettlement_order`**（审查 SQL 中的 `orders` 为逻辑别名，库内无 `orders` 表）。

| 官方字段（rawPayload 别名） | 本地字段 | 单位 | 处理规则 |
|----------------------------|----------|------|----------|
| `pay_goods_amount` / `order_amount` / `pay_amount` | `order_amount`（预估轨实付） | **分** | `OrderDualTrackAmountResolver.resolve()`，`firstPositive` |
| `settled_goods_amount` / `settle_amount` | `settle_amount` | **分** | 缺失时**回退 `payAmount`**（待结算污染风险） |
| `estimated_commission`（`colonel_order_info`） | `estimate_service_fee` | **分** | 机构嵌套层 `firstFromInstitutions` |
| `settle_colonel_commission` / `real_commission` | `effective_service_fee` | **分** | 结算后写入；待结算保留 0；可能扣减 `effective_tech_service_fee` |
| `estimated_tech_service_fee` | `estimate_tech_service_fee` | **分** | 缺失时回退 `effectiveTechServiceFee` |
| `tech_service_fee` / `settled_tech_service_fee` | `effective_tech_service_fee` | **分** | `firstNonNegative` |
| `settle_time` / `update_time`（网关 `DouyinOrderItem`） | `settle_time` | 时间 | 仅 `SyncSource.SETTLEMENT` 且 `item.settleTime()!=null` 时写入 |
| `order_create_time` / `pay_success_time` | `order_create_time` / `pay_time` | 时间 | 6468 主写；2704 可补充 |

**金额单位**：全链路以**分**存储，前端/API 展示层 ÷100 转元。

**null / 0 处理**：

- 待结算：`effective_*` 为 0 时保留，不用 `estimate_*` 覆盖（`mergeEstimateSnapshot` 保护预估轨）
- 6468 更新已存在订单：`mergeSettlementSnapshot` 防止清空 2704 已写结算字段
- `settleAmount` 缺失回退 `payAmount`：2704 路径存在结算轨污染风险（已有单测覆盖部分场景）

**6468 vs 2704 分轨**：

- `SyncSource.INSTITUTE` → `applyInstituteFactToOrder()`：只写 `order_amount`、`estimate_*`
- `SyncSource.SETTLEMENT` → `applyToOrder()`：写 `settle_amount`、`effective_*`、`settle_time`

---

## 5. 业绩域映射（`performance_records`）

`PerformanceCalculationService.upsertFromOrder()`：

| 订单字段 | 业绩字段 |
|----------|----------|
| `order_amount` | `pay_amount` |
| `settle_amount` | `settle_amount` |
| `estimate_service_fee` | `estimate_service_fee` |
| `effective_service_fee` | `effective_service_fee` |
| `estimate_tech_service_fee` | `estimate_tech_service_fee` |
| `effective_tech_service_fee` | `effective_tech_service_fee` |
| `settle_time` | `settle_time` |

链路完整；当前空数据因订单表无结算事实，非监听器断裂。

---

## 6. 只读 SQL 对账（real-pre）

> 实际表名 `colonelsettlement_order`；`pay_amount` 列不存在，创建轨 GMV 使用 `order_amount`（分）。

### 一、订单表结算字段覆盖率

```sql
SELECT COUNT(*) AS total_orders,
       COUNT(settle_time) AS has_settle_time,
       SUM(CASE WHEN settle_amount > 0 THEN 1 ELSE 0 END) AS has_settle_amount,
       SUM(CASE WHEN effective_service_fee > 0 THEN 1 ELSE 0 END) AS has_effective_service_fee,
       SUM(CASE WHEN effective_tech_service_fee > 0 THEN 1 ELSE 0 END) AS has_effective_tech_service_fee
FROM colonelsettlement_order WHERE deleted = 0;
```

| total_orders | has_settle_time | has_settle_amount | has_effective_service_fee | has_effective_tech_service_fee |
|-------------:|----------------:|------------------:|--------------------------:|-------------------------------:|
| 39,589 | **0** | **0** | **0** | **0** |

### 二、今日结算轨（订单表）

| settle_order_count | settle_gmv | effective_service_fee | effective_tech_service_fee | effective_service_profit |
|-------------------:|-----------:|--------------------:|---------------------------:|-------------------------:|
| 0 | NULL | NULL | NULL | NULL |

### 三、今日创建轨（订单表）

| create_order_count | pay_gmv（分） | estimate_service_fee | estimate_tech_service_fee | estimate_profit |
|-------------------:|--------------:|---------------------:|--------------------------:|----------------:|
| 4,339 | 10,037,562 | 194,094 | 13,797 | 180,297 |

### 四、今日结算轨（业绩表）

| perf_count | settle_amount | effective_service_profit | effective_recruiter_commission | effective_channel_commission | effective_gross_profit |
|-----------:|--------------:|-------------------------:|-------------------------------:|-------------------------------:|-----------------------:|
| 0 | NULL | NULL | NULL | NULL | NULL |

### 五、分析汇总表 `agg_daily_performance_settle`

**表不存在**（`to_regclass` 为 NULL）。

### 六、分析汇总表 `agg_daily_performance_create`

**表不存在**（`to_regclass` 为 NULL）。

---

## 7. 运行态同步证据（近 2 小时）

```
ORDER_SYNC_SETTLEMENT api=buyin.colonelMultiSettlementOrders mode=INCREMENTAL timeType=update
  range=[...] pagesFetched=0 uniqueOrders=0 fetched=0 inserted=0 updated=0 stopReason=EMPTY_PAGE
Douyin API call success, method=buyin.colonelMultiSettlementOrders
```

同期 6468 `ORDER_SYNC_INSTITUTE` 有 `fetched>0`、`inserted/updated>0`。

**历史直查**（见 `harness/reports/settle-dashboard-empty-diagnosis-20260609-134601.md`）：

- `time_type=update` 与 `settle`，2026-03-12 ~ 2026-06-09 逐日 90 天：`code=10000`，`ordersCount=0`
- 6468 raw：`settled_goods_amount` 全 0，`real_commission` 正数 0，`settle_time` JSON key 存在但值均为 null

---

## 8. 前端展示层

| 项 | 状态 |
|----|------|
| 页面 | `frontend/src/views/data/index.vue` |
| `time_basis` / `timeField` | 支持 `createTime`（创建轨）与 `settleTime`（结算轨） |
| 结算轨 UI | 双轨概览条、`dashboard-dual-track-settle`、经营指标矩阵「结算：」列（`OrderList.vue`） |
| API | `GET /api/data/dashboard/metrics` → `DualTrackMetricsVO`（`estimate` + `settle`） |
| 当前展示 | 创建轨有数（今日 ~4339 单）；结算轨全 0 — **非前端未接，是后端/上游无结算事实** |
| 口径混用 | **未发现**；结算轨使用 `effective_*` / `settle_time`，创建轨使用 `estimate_*` / `order_create_time` |

---

## 9. 判断标准对照

| # | 标准 | 本次判定 |
|---|------|----------|
| 1 | 未调用 2704 | **否** → 不构成 FAIL |
| 2 | 已调用但结算字段基本为空 | **是** → PARTIAL_PASS |
| 3 | orders 有结算、performance 无 | **否**（orders 也无） |
| 4 | performance 有结算、agg_settle 无 | **否**（performance 也无） |
| 5 | 后端有、前端不展示 | **否**（前端已展示双轨） |
| 6 | 创建轨预估当结算展示 | **否** |

---

## 10. 下一步最小修复任务拆分

按优先级、最小改动排列（**本轮未执行**）：

### P0 — 运行态解锁结算数据

1. **业务确认**：向抖音/业务方确认授权主体「星链达客」近 90 天是否应有已结算订单；若有，持 `log_id` 工单核对接口返回条件。
2. **等待上游样本**：一旦出现 `fetched>0`，只读验证 `colonelsettlement_order.settle_time` / `effective_*` → `performance_records` → 看板 `settle` 轨是否联动为非 0。

### P1 — 网关与设计对齐（小 diff）

3. **`time_type` 透传**：`RealDouyinOrderGateway.listSettlement()` 接受 `timeType` 参数；结算增量任务传 `settle`（或 INCREMENTAL 用 `update`、单独 SETTLE 任务用 `settle`，与 `docs/06-技术架构与数据模型.md` 双链路一致）。
4. **联调探针修复**：`DouyinController.dingdanJiesuan` 的 `timeType` 目前未传入 gateway（已有诊断报告记录）。
5. **`settleAmount` 回退治理**：2704 路径在 `settled_goods_amount=0` 时不回退 `payAmount`，避免待结算单污染结算轨（`OrderDualTrackAmountResolver` L86-89）。

### P2 — 可观测性与汇总表

6. **结算同步专用任务**：增加 `time_type=settle` 的日级/周级回补 job（90 天窗口内按天切片），与 `update` 增量解耦。
7. **`agg_daily_performance_settle`**：若产品需要离线汇总，补 migration + 写入任务；当前看板不依赖该表。

### P3 — 文档/SDK

8. 明确「未使用官方 SDK 类 `BuyinColonelMultiSettlementOrdersRequest`」为有意自研 HTTP 封装，或在 gateway 层引入 SDK 以降低字段漂移风险。

---

## 11. 审查方法说明

- 全仓库关键词检索：`colonelMultiSettlementOrders`、`BuyinColonelMultiSettlementOrdersRequest`、`MultiSettlement`、`settle_time`、`ORDER_SYNC`、`agg_daily_performance_*` 等
- 源代码只读：`OrderApi`、`RealDouyinOrderGateway`、`OrderSyncService`、`OrderDualTrackAmountResolver`、`OrderSyncJob`、`PerformanceCalculationService`、前端 `index.vue`
- 数据库：`docker exec saas-active-postgres-real-pre-1 psql -U saas -d saas_real_pre` 只读 SELECT
- 日志：`docker logs saas-active-backend-real-pre-1 --since 2h` 筛选 `ORDER_SYNC_SETTLEMENT`
- **禁止项均已遵守**：无写库、无清数据、无重算业绩、无改同步逻辑、无 `dryRun=false`

---

## 12. 相关报告

- `harness/reports/settle-dashboard-empty-diagnosis-20260609-134601.md` — 上游直查与 raw 层复核
- `harness/reports/order-settlement-dual-track-verify-20260603-183157.md` — 双轨字段映射早期验证
- `harness/reports/order-api-729-verify-20260603-174500.md` — 2704 接口连通性验证

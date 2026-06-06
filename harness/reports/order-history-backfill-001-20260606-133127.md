# ORDER-HISTORY-BACKFILL-001

## 任务边界

- 时间：2026-06-06 13:31 CST
- 环境：本地 `real-pre`
- 模式：只读诊断；未执行订单写库回补
- 用户目标：定位本地订单事实表为什么未达到正确基准 `3739`，避免盲目重拉
- 禁止项执行情况：未修改 Java / Vue / SQL / Docker / env，未重启容器，未部署远端，未 stage/commit

## 当前阶段结论

```text
阶段性结论：FAIL_LOCAL_ORDER_COVERAGE_ROOT_CAUSE_PAGINATION

本地订单事实表仍不足：
- 正确基准：3739
- 本地当前：1399
- 当前缺口：2340

已入库订单链路健康：
- colonelsettlement_order = 1399
- performance_records = 1399
- anti-join = 0
- duplicate performance_records = 0

高可信技术根因：
6468 buyin.instituteOrderColonel 上游返回 data.orders + data.cursor，
但当前 RealDouyinOrderGateway 只用 has_more / hasMore 判断是否继续翻页。
6468 响应没有 has_more，因此同步循环每个窗口只处理第一页 100 条。
```

注意：用户确认的 `3739 / ¥79400.07 / ¥1434.01 / ¥121.36` 是正确基准，本轮不质疑该基准。但本轮 RAW 探针证明无条件扫 `2026-06-03 00:00:00 ~ 当前` 会超过 3739 单，因此写库前必须锁定正确基准对应的筛选口径，否则会补过头。

## 环境证据

| 项 | 结果 |
| --- | --- |
| repo | `D:\Projects\SAAS` |
| branch | `feature/auth-system` |
| commit | `98175caa` |
| safety-check | `real-pre full DryRun` PASS |
| Docker | backend / frontend / postgres / redis 均 healthy |
| 后端 health | `/api/system/health` 返回 `{"status":"UP"}` |
| 前端 health | `/healthz` 可访问 |
| real-pre 开关 | `APP_TEST_ENABLED=false`、`DOUYIN_TEST_ENABLED=false`、`DOUYIN_REAL_UPSTREAM_MODE=live`、`ORDER_SYNC_ENABLED=true` |

## 本地 DB 对账

金额单位：分。

| 指标 | 当前值 |
| --- | ---: |
| `colonelsettlement_order` 非删除订单 | 1399 |
| `performance_records` | 1399 |
| 订单缺业绩 anti-join | 0 |
| 重复 `performance_records.order_id` | 0 |
| 订单额合计 | 2875818 |
| 预估服务费收入合计 | 48357 |
| 预估技术服务费合计 | 4889 |
| 非零结算金额订单 | 0 |
| 非空 `pick_source` 订单 | 0 |

日期分布：

| pay_date | cnt | amount_cent | service_fee_cent | tech_fee_cent |
| --- | ---: | ---: | ---: | ---: |
| 2026-06-03 | 325 | 620712 | 10685 | 1093 |
| 2026-06-04 | 608 | 1244872 | 20744 | 2094 |
| 2026-06-05 | 317 | 704244 | 12277 | 1234 |
| 2026-06-06 | 149 | 305990 | 4651 | 468 |

状态分布：

| order_status | cnt | amount_cent | service_fee_cent |
| --- | ---: | ---: | ---: |
| 1 | 1298 | 2642722 | 44106 |
| 4 | 101 | 233096 | 4251 |

`order_sync_dedup_claim`：

| claim_count | bound_count | min_first_seen | max_first_seen | max_last_seen |
| ---: | ---: | --- | --- | --- |
| 1399 | 1399 | 2026-06-03 12:20:07 | 2026-06-06 05:30:00 | 2026-06-06 05:30:00 |

说明：本地从上一轮 1390 增至 1399，说明自动同步仍在近窗入库，但缺口仍然是订单事实表覆盖不足。

## 本地 API 对账

| 接口 | 订单数 | 订单额 | 服务费收入 | 技术服务费 | 服务费支出 | 结论 |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| `/api/orders?page=1&size=1` | 1399 | - | - | - | - | 反映订单事实表 |
| `/api/orders/unattributed?page=1&size=1` | 1399 | - | - | - | - | 当前全部未归因 |
| `/api/data/orders/summary` | 1399 | 28758.18 | 483.57 | 48.89 | 130.32 | 订单覆盖不足；支出口径仍漂移 |
| `/api/performance/summary` | 1301 valid | 26516.92 | 442.16 | 44.73 | 119.56 | 只统计 valid 业绩 |
| `/api/dashboard/metrics` | 今日口径 | - | 44.16 | 4.44 | 缺字段 | 不是全量对账接口 |
| `/api/dashboard/summary` | 1301 | 0 | 0 | - | - | 旧归因看板口径 |

## Redis Checkpoint

| key | epoch | Asia/Shanghai |
| --- | ---: | --- |
| `order:sync:last_time` | 1780723139 | 2026-06-06 13:18:59 |
| `order:sync:pay_recent_last_time` | 1780721939 | 2026-06-06 12:58:59 |
| `order:sync:institute_recent_last_time` | 1780723139 | 2026-06-06 13:18:59 |

判断：checkpoint 没有停在错误历史时间点。问题不是 Redis 水位卡旧，而是任务只从当前附近滚动，且 6468 分页没有继续处理后续 cursor。

## 代码证据

| 问题点 | 证据 |
| --- | --- |
| 自动窗口只覆盖近窗 | `OrderSyncService`：`INSTITUTE_RECENT_WINDOW_SECONDS=24h`，`DEFAULT_COUNT=100`，`MAX_PAGES=200` |
| 6468 定时同步入口 | `OrderSyncService.syncInstituteOrdersRecentWindow()` 固定 `now-24h ~ now`，调用 `fetchInstitute(... cursor="0")` |
| 手动 `/orders/sync` 不是 6468 | `OrderController.syncOrders()` 调 `OrderSyncService.syncByTimeRange()`，而该方法走 `syncRangeWithMode()` -> `fetchSettlement()` -> 2704 |
| 6468 RAW 只读入口存在 | `DouyinController`：`POST /douyin/order-sync-probes/raw` 调 `douyinOrderGateway.listInstituteOrders()`；仅返回 raw，不写库 |
| 6468 上游分页结构 | RAW 探针返回 `dataKeys=cursor,orders` |
| 当前分页判断错误 | `RealDouyinOrderGateway.toOrderListResult()` 只用 `has_more/hasMore/has_next/hasNext` 生成 `hasMore`，虽读取 `cursor` 为 `nextCursor`，但 `hasMore=false` |
| 同步循环停止条件 | `OrderSyncService.syncItems()`：`hasMore = continuePaging && response.hasMore() && pages < MAX_PAGES` |
| afterCommit 业绩生成 | `OrderSyncPersistenceService.publishAfterCommit()` 事务提交后发布 `OrderSyncedEvent`；`PerformanceRecordSyncListener.onOrderSynced()` 异步调用 `upsertFromOrder()` |

## 运行日志证据

近 6 小时订单同步日志：

```text
ORDER_SYNC_SETTLEMENT api=buyin.colonelMultiSettlementOrders mode=INCREMENTAL/PAY_RECENT pages=0 fetched=0
ORDER_SYNC_INSTITUTE api=buyin.instituteOrderColonel mode=INSTITUTE_RECENT pages=1 fetched=100 inserted=1~10 updated=90~99
```

一次手动 7 天范围同步日志：

```text
ORDER_SYNC_SETTLEMENT api=buyin.colonelMultiSettlementOrders mode=INCREMENTAL range=[1780116241, 1780721041] pages=0 fetched=0
```

判断：2704 结算源不能补成交历史；当前能补订单事实的是 6468，但 6468 实际只落每个窗口第一页。

## 上游 RAW 探针

只读调用，不写库，不输出订单明细。

### 6468 结构探针

```text
dataKeys=cursor,orders
firstOrderKeys=colonel_type,product_img,author_openid,media_id,order_id,...,pay_success_time,total_pay_amount,colonel_order_info,...
rows=100
cursor=6953466161587689245
```

### 6468 cursor 翻页探针

```text
first page:  rows=100 cursor=6953466161587689245
second page: rows=100 cursor=6953463508662621995
```

说明：上游可以继续分页；本地当前 `pages=1` 不是上游没有第二页，而是本地 `hasMore` 判断没有识别 6468 的 cursor 结构。

### 上游全范围探针

范围：`2026-06-03 00:00:00 ~ 2026-06-06 13:27:31`

```text
pages=80（达到本次只读探针上限，仍未结束）
rowsTotal=8000
uniqueOrders=8000
amountYuan=157377.01
serviceFeeYuan=2718.40
techServiceFeeYuan=273.64
flowCounts: PAY_SUCC=7314, REFUND=678, CONFIRM=8
```

说明：上游存在大量历史订单，不是上游同范围无数据。

### 3739 口径尝试

只读尝试 1：按 6468 cursor 取前 3739 个唯一订单。

```text
uniqueOrders=3739
amountYuan=75020.15
serviceFeeYuan=1250.28
techServiceFeeYuan=125.76
flowCounts: PAY_SUCC=3443, REFUND=296
```

只读尝试 2：按 6468 cursor 取前 3739 个 `PAY_SUCC` 订单。

```text
paySuccOrders=3739
amountYuan=74672.62
serviceFeeYuan=1246.86
techServiceFeeYuan=125.48
```

两者均不匹配正确基准：

```text
正确基准：3739 / ¥79400.07 / ¥1434.01 / ¥121.36
```

判断：`3739` 不是简单“游标前 3739 条”或“前 3739 条 PAY_SUCC”。写库回补前必须确认正确基准对应的时间范围、状态、业务筛选或上游查询参数。

## 八个问题逐项回答

### 1. 本地同步窗口是不是只拉了最近几天？

是。代码层面 6468 自动入口固定 `now-24h ~ now`，2704 增量是 10 分钟窗口，PAY_RECENT 是 6 小时窗口。本地 DB 目前只覆盖 2026-06-03 至 2026-06-06 的一部分订单。

### 2. Redis checkpoint 是否从错误时间点开始？

不是主因。三个 checkpoint 都在 2026-06-06 13 点附近，说明任务水位在当前时间附近滚动；问题不是卡在历史旧点，而是没有全量历史回扫，且 6468 分页只处理第一页。

### 3. 6468 instituteOrderColonel 是否有历史回补入口？

有 RAW 只读探针，但没有当前可直接写库的 6468 历史范围回补入口。

现有写库入口 `/orders/sync` 走 2704 `colonelMultiSettlementOrders`，近窗和 7 天手动日志均 `fetched=0`，不能用于补成交历史。

### 4. 2704 settlement 是否只查结算，导致成交历史没补？

是高可信。2704 当前 `INCREMENTAL` / `PAY_RECENT` / 手动 7 天范围均 fetched=0。成交事实主要来自 6468。

### 5. time_type/pay_time/update_time 是否使用错？

2704 只支持 `settle/update`，代码使用 `update`。但当前成交历史缺口不是 2704 time_type 的第一主因，因为 2704 该范围直接返回 0。6468 由 `start_time/end_time + cursor/size` 查询，返回订单含 `pay_success_time`。

### 6. 上游同口径能否按日期返回总计接近 3739？

当前不能确认。上游无条件全范围返回远超 3739；前 3739 唯一订单和前 3739 PAY_SUCC 金额均不匹配正确基准。需要补充正确基准的筛选口径，或先实现 dry-run 对账工具再锁定。

### 7. 本地是否有分页只拉前 100 的风险？

是，并且是本轮最高可信技术根因。

证据链：

```text
6468 raw: data.orders + data.cursor
current parser: hasMore 只读 has_more/hasMore/has_next/hasNext
sync loop: response.hasMore() 为 false 就停
runtime log: ORDER_SYNC_INSTITUTE pages=1 fetched=100
manual cursor probe: second page rows=100
```

### 8. 补历史时是否会触发 afterCommit 生成 performance_records？

如果通过 `OrderSyncPersistenceService.persistOrder()` 写入，则会触发。代码在事务提交后发布 `OrderSyncedEvent`，`PerformanceRecordSyncListener` 异步 upsert 业绩。本地当前 anti-join=0 也证明已同步订单都能生成业绩记录。

## 为什么本轮不能直接写库回补

直接执行写库存在补过头风险：

1. 6468 无条件全范围上游订单远超 3739。
2. “前 3739 条唯一订单”和“前 3739 条 PAY_SUCC”均不匹配正确金额基准。
3. 当前缺少 6468 写库历史回补入口；现有 `/orders/sync` 写库入口走 2704，无法补成交事实。
4. 若直接修正全局分页并重启，定时 24 小时任务可能在未确认口径前自动大量入库，导致本地超过 3739。

因此本轮只能给出阶段性根因和下一步安全修复设计，不能把未回补写成 PASS。

## 下一步建议

### 临时止血方案

暂停盲目手动 `/orders/sync` 历史补拉，因为该入口走 2704，证据显示 fetched=0，不能补成交事实。

### 根因修复方案

新增受控 6468 历史回补能力，而不是直接改定时任务：

1. 新增 6468 range backfill service/endpoint，支持 `dryRun=true`。
2. dry-run 输出：页数、唯一订单数、金额、服务费、技术服务费、状态分布、日期分布、预计新增/更新数。
3. 写入前必须确认 dry-run 与 `3739 / ¥79400.07 / ¥1434.01 / ¥121.36` 接近。
4. apply 模式通过 `OrderSyncPersistenceService.persistOrder()` 写入，保持 afterCommit 生成 `performance_records`。
5. apply 增加 `maxCreate` / `targetOrderCount` / `stopWhenLocalCountReaches` 保护，避免超过 3739。
6. 先不要改变 `syncInstituteOrdersRecentWindow()` 的全局分页行为；避免重启后定时任务自动补入过量订单。

### 长期治理方案

1. 在 `RealDouyinOrderGateway` 中补齐 6468 `data.cursor` 分页契约测试。
2. 为 6468 和 2704 分别记录 `sync_source` 字段或同步批次表，避免后续无法按来源对账。
3. 为历史回补建立 dry-run -> approval -> apply -> evidence 的固定 runbook。
4. 为订单同步增加“上游响应结构变更”合约测试，覆盖 `data.orders/cursor`。

## 验收清单

`ORDER-HISTORY-BACKFILL-001` 进入 apply 前必须先满足：

- dry-run 能复现接近 3739 的候选集。
- dry-run 金额接近 ¥79400.07。
- dry-run 服务费收入接近 ¥1434.01。
- dry-run 技术服务费接近 ¥121.36。
- 预计新增数不会让本地超过 3739。
- apply 后 `colonelsettlement_order=3739`。
- apply 后 `performance_records=3739`。
- anti-join = 0。
- 重复 `performance_records` = 0。
- `/api/orders total=3739`。
- `/api/data/orders/summary orderCount=3739`。
- 服务费支出、服务费收益、毛利仍标记 `PENDING_BY_DASHBOARD_FORMULA_DRIFT`，直到公式任务修复。

## 本次状态

```text
PARTIAL_DIAGNOSIS_COMPLETE

已定位高可信技术根因：6468 cursor 分页未被本地 hasMore 识别。
未执行写库回补：正确基准 3739 的上游筛选口径尚未锁定，直接写库有补过头风险。
```

## 本次无需 Harness 升级

本次问题属于业务回补工具缺失和第三方分页契约缺失，不需要升级 Harness 入口规则。建议后续新增订单历史回补 runbook 和 dry-run 模板。

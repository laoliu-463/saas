# 结算数据看板无结算数据排查报告

- 时间：2026-06-09 13:46:01 Asia/Shanghai
- 环境：本地 real-pre
- 分支：feature/auth-system
- commit：1e4e9419
- 工作区状态：不干净。本次只读排查新增本报告，未 stage；任务前已有 `backend/src/test/java/com/colonel/saas/service/PerformanceMetricsQueryServiceTest.java` 修改及多份未跟踪报告。
- 是否修改业务代码：否
- 是否远端部署：否
- 结论：PASS（诊断结论已形成；本轮未做修复）

## 问题复述

用户问题：结算数据看板没有结算数据，需要判断是上游接口没有传结算数据，还是本地处理链路有问题。

## 已采集证据

### 1. 服务健康

执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\verify-local.ps1 -Env real-pre -Scope full
```

结果：

- Backend `http://127.0.0.1:8081/api/system/health` 返回 200，body 为 `{"status":"UP"}`。
- Frontend `http://127.0.0.1:3001/healthz` 返回 200。
- Local verification passed.

### 2. 看板接口返回

登录后请求：

- `/api/dashboard/metrics?timeField=createTime`
- `/api/dashboard/metrics?timeField=payTime`
- `/api/dashboard/metrics?timeField=settleTime`

结果摘要：

- `settle` 轨：`todayOrderCount=0`，`todayGmv=0`，近 7 天趋势全部 0，`amountTrack=effective`，`metricsSource=performance_records`，`track=settleTime`。
- `estimate` 轨：今天 `todayOrderCount=3365`，`todayGmv=77569.88`，`amountTrack=estimate`，`metricsSource=performance_records`，`track=createTime`。

说明：前端不是单纯未展示；后端看板接口本身返回结算轨为 0。

### 3. 数据库订单事实表

查询 `colonelsettlement_order`：

- `total_orders=38899`
- `settle_time_not_null=0`
- `settle_amount_nonzero=0`
- `effective_service_fee_nonzero=0`
- `effective_service_fee_expense_nonzero=0`
- `today_settle_count=0`
- `max_pay_time=2026-06-09 13:39:04`
- `max_order_create_time=2026-06-09 13:39:04`

最新订单样本存在创建/付款/预估服务费：

- 例如 `6926989782768516317`：`order_create_time=2026-06-09 13:41:18`，`pay_time=2026-06-09 13:41:18`，`order_amount=1399`，`estimate_service_fee=27`。

说明：本地有订单和预估轨数据，但订单事实表没有任何结算字段落库。

### 4. 数据库业绩表

查询 `performance_records`：

- `total_records=38906`
- `valid_records=35345`
- `settle_time_not_null=0`
- `settle_amount_nonzero=0`
- `effective_service_fee_nonzero=0`
- `effective_service_fee_expense_nonzero=0`
- `today_settle_count=0`

查询 `performance_records` 中有结算信号的最近记录，结果 0 行。

说明：业绩表没有结算轨数据；这与订单事实表为空一致。

### 5. 结算明细表

查询 `colonel_order_settlement`：

- `total=0`
- `settle_time_not_null=0`
- `settle_amount_nonzero=0`

说明：不是主订单表缺失但结算明细表已有数据。

### 6. 看板聚合代码

`PerformanceMetricsQueryService` 结算轨逻辑：

- `timeField=settleTime` 解析为 `settle_time`。
- SQL 从 `performance_records pr` join `colonelsettlement_order co`。
- 结算轨使用：
  - `pr.settle_amount`
  - `pr.effective_service_fee`
  - `pr.effective_tech_service_fee`
  - `pr.effective_service_profit`
  - `pr.effective_gross_profit`
- 时间过滤在结算轨追加 `co.settle_time IS NOT NULL`。

说明：看板结算轨查的是本地业绩结算字段；在本地结算字段全空时，返回 0 是符合当前代码逻辑的。

### 7. 订单到业绩处理链路

`PerformanceRecordSyncListener` 在 `OrderSyncedEvent` 后按 `orderId` 查询完整订单，再调用 `PerformanceCalculationService.upsertFromOrder(order)`。

`PerformanceCalculationService` 会把订单字段映射到业绩字段：

- `order.getSettleAmount()` -> `performance_records.settle_amount`
- `order.getEffectiveServiceFee()` -> `performance_records.effective_service_fee`
- `order.getEffectiveServiceFeeExpense()` -> `performance_records.effective_service_fee_expense`
- `order.getSettleTime()` -> `performance_records.settle_time`

说明：如果订单实体里已有结算字段，业绩表具备写入路径。当前空值更接近“订单事实层未收到/未落结算字段”，不是看板聚合层单独丢字段。

### 8. 上游同步日志

近 24 小时后端日志中：

- 多次 `Douyin API call success, method=buyin.colonelMultiSettlementOrders`
- 随后 `ORDER_SYNC_SETTLEMENT api=buyin.colonelMultiSettlementOrders ... timeType=update ... pagesFetched=0 uniqueOrders=0 fetched=0 inserted=0 updated=0 failed=0 stopReason=EMPTY_PAGE`
- 同期 `buyin.instituteOrderColonel` 成功并有数据，例如 `fetched=15 inserted=6 updated=9 failed=0`、`fetched=60 updated=60`。

说明：

- 抖音鉴权/连通不是全局失败。
- 主订单/预估事实源有数据。
- 当前本地结算同步链路调用 2704 成功，但在 `timeType=update` 下返回空页。

### 9. 联调探针与真实上游直查

调用 `/api/douyin/order-settlements`：

- 按 5 个最新订单号查询：HTTP 200，`data.orders` 长度 0。
- 按 2026-06-09 当日时间范围传 `timeType=settle`：HTTP 200，`data.orders` 长度 0。
- 按 2026-06-03 至 2026-06-09 时间范围传 `timeType=settle`：HTTP 200，`data.orders` 长度 0。

但代码证据显示：

- `DouyinController` 接收 `timeType` 参数，但调用 `douyinOrderGateway.listSettlement(...)` 时没有传入。
- `RealDouyinOrderGateway.listSettlement(...)` 调用 `OrderApi.listColonelMultiSettlementOrders(...)` 时硬编码 `"update"`。
- `RealDouyinOrderGateway.listSettlementByOrderIds(...)` 也硬编码 `"update"`。

说明：当前联调探针不能证明 `time_type=settle` 下上游也为空；它只能证明当前本地网关路径在 `update` 口径下拿不到结算订单。

随后不修改业务代码，使用本地 SDK `SignUtil`、后端 real-pre 配置和 Redis 中有效 access token，直接请求真实上游 `buyin.colonelMultiSettlementOrders`，只记录响应码和订单数量：

- `time_type=update`，窗口 2026-06-03 00:00:00 至 2026-06-09 23:59:59：`code=10000`，`ordersCount=0`。
- `time_type=settle`，窗口 2026-06-03 00:00:00 至 2026-06-09 23:59:59：首次整窗返回 `code=20001` / `dop.service-timeout-error`，不能当作无数据结论。
- `time_type=settle`，按天拆分 2026-06-03 至 2026-06-09：每天均 `code=10000`，`ordersCount=0`。
- `time_type=settle`，按 5 个最新订单号定向查询：`code=10000`，`ordersCount=0`。
- 用户补充接口文档：`end_time` 为 String，非必填，示例 `2022-10-31 15:04:05`，开始与结束时间最大间隔 90 天。为排除“结算发生在更早日期”的可能，追加按天逐日真实直查 90 天范围：2026-03-12 至 2026-06-09，共 90 天，全部 `code=10000`、无订单返回，`successZeroDays=90`、`nonZeroDays=0`、`errorDays=0`。

说明：在当前看板关注的最近 7 天以及可覆盖的近 90 天范围内，真实上游 `time_type=settle` 按天查询没有返回结算订单；本地结算事实为空与上游返回为空一致。

### 10. 用户复核后追加排查

用户反馈“不应该”，因此追加排除以下误判：

1. 真实响应结构是否被脚本漏解析：
   - `time_type=settle` 单日请求返回 `code=10000`、`log_id=20260609154258180034EF6A1FD0776A00`。
   - 响应顶层 key：`code,msg,sub_code,sub_msg,data,log_id`。
   - `dataKeys=orders,cursor`，`dataPreview={"orders":[],"cursor":""}`。
   - `time_type=update` 单日请求同样 `dataKeys=orders,cursor`，`orders=[]`。

2. 当前授权主体是否错位：
   - `/api/douyin/institution-info` 返回当前机构：`institution_id=7351155267604201000`，`colonel.buyin_id=7351155267604218000`，名称“星链达客”。
   - 本地订单 raw 分布中，“星链达客”占 35446 单，`colonel_buyin_id=7351155267604218149`，与当前授权主体对应。

3. `order_ids` 参数格式是否错误：
   - 使用星链达客最新 5 个订单号，`order_ids` 逗号字符串 + `time_type=settle`：`code=10000`，`ordersCount=0`。
   - `order_ids` 逗号字符串 + `time_type=update`：`code=10000`，`ordersCount=0`。
   - `order_ids` 数组：上游返回 `isv.parameter-invalid`，说明正确类型确实是 String。
   - `order_id` 单数字段或 `orderIds` 驼峰字段：上游返回“请指定时间范围或订单号”，说明这些字段名不被识别。

4. 6468 原始订单是否已经带实际结算字段但本地未写列：
   - `extra_data` 中 `settle_time` key 存在 39538 条，但均为 JSON null，有效非空 0 条。
   - `extra_data.settled_goods_amount` key 存在 39538 条，但全部为 `0`，正数 0 条。
   - `colonel_order_info.estimated_commission` 正数 33997 条。
   - `colonel_order_info.real_commission` 正数 0 条。
   - `colonel_order_info.tech_service_fee` 正数 33954 条。
   - `colonel_order_info.settled_tech_service_fee` 正数 0 条。

5. 订单状态是否已经进入可结算阶段：
   - `flow_point=PAY_SUCC`：35914 条，`settled_goods_amount=0`。
   - `flow_point=REFUND`：3642 条，`settled_goods_amount=0`，`refund_time` 非空。
   - `flow_point=CONFIRM`：10 条，样本均 `settled_goods_amount=0`、`real_commission=0`、`settle_time` 空。

追加结论：本地并不是“raw 里有有效结算值但主表/业绩表没写”。raw 层也没有实际结算值；当前只有预估佣金和预估技术服务费。

## 现象到证据链

现象：

- 结算数据看板没有结算数据。

证据：

- 看板接口 `settle` 轨返回 0。
- 订单事实表 `colonelsettlement_order` 的 `settle_time`、`settle_amount`、`effective_service_fee` 全空/0。
- 业绩表 `performance_records` 的 `settle_time`、`settle_amount`、`effective_service_fee` 全空/0。
- 结算明细表 `colonel_order_settlement` 为 0 行。
- 后端日志显示 2704 `buyin.colonelMultiSettlementOrders` 调用成功但 `fetched=0`，同时主订单接口有数据。
- 本地 2704 网关硬编码 `time_type=update`，而领域文档要求结算事实源默认 `time_type=settle`。

推论：

- 可以排除“前端单独没展示”的优先可能，因为后端接口结算轨就是 0。
- 可以排除“看板 SQL 有结算数据但没聚合出来”的优先可能，因为本地订单事实表和业绩表均无任何结算字段。
- 可以认定当前看板最近 7 天范围内，上游 `buyin.colonelMultiSettlementOrders` 的真实 `time_type=settle` 按天查询没有返回结算订单。
- 本地当前没有收到/没有落库结算事实；直接原因是 2704 同步结果为空。当前证据更支持“上游当前窗口无结算数据返回”，而不是“本地有数据但处理丢失”。

## 结论

当前结算看板为空，不是前端展示问题，也不是看板聚合层已有数据没查出来；本地 real-pre 的订单事实表、业绩表、结算明细表都没有结算事实。

真实上游直查已补齐：2026-06-03 至 2026-06-09 逐日 `time_type=settle` 查询均成功返回 0 单，按最新订单号定向 `settle` 查询也返回 0 单；根据用户补充的 90 天窗口规则，又追加 2026-03-12 至 2026-06-09 逐日直查，90 天全部成功返回 0 单。进一步复核 raw 层后确认，6468 原始订单也没有有效结算值：`settled_goods_amount` 全部为 0，`real_commission` 正数 0，`settle_time` 均为空。因此当前看板没有结算数据的直接原因是：上游在当前授权主体与近 90 天结算窗口内没有提供实际结算事实，本地没有可落库的结算事实。

仍需标注一个本地风险：当前 `/api/douyin/order-settlements` 联调探针接收 `timeType` 但不透传，`RealDouyinOrderGateway` 时间范围查询硬编码 `update`。这不是本次看板为空的主要证据链根因，因为已通过一次性直查验证 `settle` 也是 0；但它会影响后续联调效率和误导排查，应作为后续修复项。

## 可能原因分层

高可信：

- 本地没有结算事实数据。证据：三张本地相关表均无结算信号；看板接口返回 0 与 DB 一致。

高可信：

- 上游当前窗口没有返回结算订单。证据：真实 `time_type=settle` 按天查询 2026-06-03 至 2026-06-09 全部 `code=10000` 且 `ordersCount=0`；追加近 90 天逐日查询 2026-03-12 至 2026-06-09，`successZeroDays=90`、`nonZeroDays=0`、`errorDays=0`；按最新订单号定向查询也 `ordersCount=0`。

高可信：

- 6468 原始订单也未携带有效结算值。证据：`settled_goods_amount` 正数 0、`real_commission` 正数 0、`settle_time` 有 key 但均为 JSON null；确认不是本地把有效 raw 结算值丢掉。

风险项：

- 本地 2704 网关和 `/api/douyin/order-settlements` 联调探针存在 `timeType` 不透传、实际硬编码 `"update"` 的问题。真实 `settle` 口径已经通过一次性直查验证为 0，因此它不是本次看板为空的主因，但会误导后续排查，应修正。

低可信：

- 看板聚合 SQL 错误。现有证据不支持，因为源表没有结算数据，SQL 返回 0 合理。

## 建议后续验证

1. 向业务确认当前授权主体近 90 天是否预期存在已结算订单；若业务预期有，需拿直查请求对应的上游 log_id 向抖音侧核对授权主体、订单范围、订单是否满足分次结算接口返回条件、结算状态和授权范围。
2. 修正只读联调探针，使 `timeType` 参数真正透传到 `OrderApi.listColonelMultiSettlementOrders(...)`，避免后续误判。
3. 结算样本出现后，再验证 `RealDouyinOrderGateway.toOrderItem -> OrderSyncService.mapOrder -> OrderSyncPersistenceService.persistOrder -> PerformanceRecordSyncListener` 是否正确落库到 `colonelsettlement_order` 与 `performance_records`。
4. 结算样本出现后，重新跑看板 `/api/dashboard/metrics` 与 SQL 对账，确认 `settle` 轨从 0 变为对应金额。

## 本轮未执行项

- 未修改业务代码。
- 未构建、未重启容器：本轮为只读诊断，没有代码变更。
- 未远端部署。
- 未提交 Git：工作区存在任务前遗留修改和未跟踪报告，本轮不混入提交。
- 本次无需 Harness 升级。

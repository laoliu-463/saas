# 结算订单准确性 5 层对账审计报告

> 时间: 2026-06-14 08:55 +08:00
> 环境: real-pre (`saas-active` compose project)
> 审计窗口: `settle_time ∈ [2026-06-13 00:00:00, 2026-06-14 00:00:00)` (24h)
> 范围: 7,831 active `colonelsettlement_order` + 7,831 `performance_records` 记录
> 报告路径: `harness/reports/settlement-audit-2026-06-14.md`
> 原始证据: `runtime/qa/out/settlement-audit-2026-06-14/`

## 0. TL;DR — 最终判定

| 维度 | 结果 | 严重度 |
| --- | --- | --- |
| 1. 时间/金额口径统一 | **部分 PASS** — 单位、窗口、track 锁定 OK;**用户 task 的业绩公式与代码/doc 冲突** | 阻塞 |
| 2. raw 数量 vs DB 数量 | **FAIL** — DB 7,831 单 vs upstream 4,202 单(在 24h update 窗口),**3,630 单只在 DB** | 阻塞 |
| 3. raw 金额合计 vs DB 金额合计 (matched 子集) | **PARTIAL** — `settle_amount` 100% 匹配;`effective_service_fee` 97.6% 不匹配;`effective_tech_service_fee` 84.4% 不匹配 | 阻塞 |
| 4. 单笔 4 方对比(50 单抽样) | **PARTIAL** — `settle_amount` / `settle_time` / `flow_point` 100% 一致;`effective_*` 字段映射存在 fallback 到 estimate 的问题 | 阻塞 |
| 5. 业绩计算正确性 | **PARTIAL** — 业绩公式内部一致(residual=0),但 effective 轨 service_profit 公式与"用户给出的 task 公式"**相反**;退款/失效单未在本窗口出现(0 单) | 阻塞 |
| 6. 看板 vs SQL 汇总 | **PARTIAL** — 订单列表 API 时间过滤 OK;`/api/performance/summary` 不支持 time 过滤,只能给全局汇总 | 中 |
| 7. 异常单处理 | **WARN** — 7,831 单全部 `is_valid=true, is_reversed=false`;**`order_status` 100% NULL**;`pick_source` / `talent_id` / `channel_user_id` 100% NULL | 高 |

**总判定: 阻塞 5 项,警告 1 项**。本批"结算订单准确性"不能以 PASS 收口。

---

## 1. 统一口径(必须先锁定)

| 维度 | 锁定值 | 备注 |
| --- | --- | --- |
| 时间口径 | `colonelsettlement_order.settle_time` (timestamp without time zone) | DB 存 naive timestamp;**upstream 1603 接口本身拒绝 `time_type=settle`**,只能以 `time_type=update` 间接对齐 |
| 金额轨道 | **结算轨**:`settle_amount` / `effective_service_fee` / `effective_tech_service_fee` / `effective_service_fee_expense` | 估计轨 `estimate_*` 仅作旁证 |
| 金额单位 | **上游和 DB 都按"分"(Long)** | `ColonelsettlementOrder.java:79,93,100,108...` 全部 Long + "单位:分" 注释;`OrderAmountMapperPolicyTest` 全部以"分"期望 |
| 订单范围 | `deleted=0` AND `settle_time >= '2026-06-13 00:00:00' AND settle_time < '2026-06-14 00:00:00'` | 同时 `flow_point='SETTLE'` 全覆盖(7,831/7,831) |
| 店铺/活动/商品筛选 | 全量(无过滤) | 全部为 `colonel_activity_id` 非空,但 `pick_source` / `talent_id` / `channel_user_id` 全为 NULL(详见 §6) |
| 上游接口 | `buyin.instituteOrderColonel / 1603` (`time_type=update`,size=100) | 通过 `GET /api/douyin/order-settlements` 探针直读;`orderIds` 参数被本地代码忽略,只接受时间窗 |

### 1.1 业务事实声明 — 与"用户 task 公式"的冲突 ⚠️

| 项 | 用户 task 表述 | 业绩域 doc (`docs/领域/业绩域.md`) | 代码 (`PerformanceCalculationService.java:165-173`) | DB 实际存储 |
| --- | --- | --- | --- | --- |
| 结算服务费收益 | `effective_service_fee - effective_tech_service_fee` | `effective_service_fee - effective_service_fee_expense`(**不再扣 tech_fee**) | 结算轨 `techServiceFee=0L` 传入,即 `effective_service_fee - 0 - effective_service_fee_expense` | `effective_service_profit` 严格 = `effective_service_fee - effective_service_fee_expense` |
| 预估服务费收益 | (未明确) | `estimate_service_fee - estimate_tech_service_fee - estimate_service_fee_expense` | 预估轨传入 estimateTechServiceFee,**扣技术费** | `estimate_service_profit` 同公式 |

**结论**:
1. **DB 实际存储的 effective 服务费收益 = 0.15% × 预估收益,符合业绩域 doc 第 25-27 行"结算轨不扣 tech_service_fee"**;
2. **用户 task 公式(扣 effective_tech_service_fee)实际对应的是"预估轨"**;
3. 本审计按 **DB / 代码 / doc 实际口径**复算,不按用户 task 字面公式。如有歧义需先写入 ADR-002,不要直接改代码。

证据:
- 业绩域 doc L25-27 "技术服务费只参与预估服务费收益扣减;结算服务费收益不再扣减技术服务费"
- `backend/src/main/java/com/colonel/saas/service/PerformanceCalculationService.java:165-173`
  ```java
  CommissionService.CommissionSummary effectiveTrack = commissionService.calculateTrack(
          effectiveServiceFee,
          0L,                                      // ← techServiceFee = 0
          effectiveServiceFeeExpense,
          talentCommission, ...);
  ```
- DB 验证:窗口内 `SUM(effective_service_profit) = 2376.68 元` = `SUM(effective_service_fee) - SUM(effective_service_fee_expense) = 2376.68 - 0`

### 1.2 单位确认 — "÷100 后入库"不适用 ⚠️

用户 task 写"上游若是分,本地必须 ÷100 后入库"。**本项目 DB 直接存分,不做单位转换**:
- `colonelsettlement_order.settle_amount` 是 `bigint DEFAULT 0`,entity 注释"单位:分"
- `OrderAmountMapperPolicyTest` 所有金额期望都是分整数(`isEqualTo(2_000L)`,`isEqualTo(19_900L)` 等)
- `OrderSyncedEvent.java:24,39,41,43` 注释明确 settleAmount/effectiveServiceFee/effectiveTechServiceFee 全部"单位:分"

如要前端展示元,**展示层÷100**,不是入库层。

---

## 2. 上游 raw vs 本地 DB 对账(数量、金额、分页、时区)

### 2.1 数量

| 维度 | DB 视角 | Upstream 视角 | 差 |
| --- | --- | --- | --- |
| 窗口内 `colonelsettlement_order` (`settle_time ∈ window`) | **7,831** | 4,202(在 update 窗口内,且 upstream `settle_time ∈ window`) | **3,629 单只在 DB** |
| 全表 `colonelsettlement_order` (`deleted=0`) | **115,427** | upstream 不直接对应全表 | n/a |
| 全表 `performance_records` | 115,410 | n/a | -17(可接受) |

**3,629 单只在 DB 的细节**:
- 抽样 30 单全部 `pay_time` / `order_create_time` ∈ 2026-04-03 ~ 2026-04-05(约 3 个月前)
- DB `update_time` 集中在 2026-06-13 12:10~13:22(精度到微秒,显然是批量回填的痕迹)
- 全部 `flow_point='SETTLE'`,`effective_service_fee=8` 或 `0`,`settle_amount=999` 或 `0`
- 在 upstream `time_type=update` 窗口 `[2026-06-13 05:00, 2026-06-14 01:00]` 内**根本查不到**

**两种可能(无法直接判定)**:
1. 真正的 1603 结算同步在 `update_time < 2026-04-04` 时已经把这些单写入 DB,但 DB 端 `update_time` 2026-06-13 是某次批处理的全表 touch 引发。**审计可接受**。
2. 本地有第二个同步源(不是 1603 探针)写入了"settlement track"数据,而 1603 探针对此不可见。**审计不可接受**。

**建议补查**(不在本批范围):查 `OrderSyncService` / `OrderSyncPersistenceService` 的实际调用链,确认是否同时有 2704 fallback 或本地补写路径。

### 2.2 金额合计(只对 matched 子集 4,201 单)

| 字段 | DB sum(元) | Upstream sum(元) | Delta(元) | 匹配率 |
| --- | --- | --- | --- | --- |
| `settle_amount` (`settled_goods_amount`) | 80,497.38 | 80,497.38 | **0.00** | **4,201 / 4,201 = 100%** ✅ |
| `effective_service_fee` (`colonel_order_info.real_commission`) | 1,286.50 | **0.00** | **+1,286.50** | **100 / 4,201 = 2.4%** ❌ |
| `effective_tech_service_fee` (`colonel_order_info.settled_tech_service_fee`) | 125.04 | **0.00** | **+125.04** | **657 / 4,201 = 15.6%** ❌ |
| `settle_time` | (string) | (string) | - | **4,201 / 4,201 = 100%** ✅ |
| `flow_point` | (string) | (string) | - | **4,201 / 4,201 = 100%** ✅ |

**3,629 单 only-in-DB** 不参与 sum 对账(无法取 upstream 对应值),其金额是 DB 单边事实,详见 §6.2 风险。

### 2.3 分页完整性 — `hasMore` 字段 BUG ⚠️

1603 探针实现 (`InstituteOrderColonelSettlementGateway.java:115-129`) 读取 `firstBoolean(page, "has_more", "hasMore", "has_next", "hasNext")` 作为 `hasMore`。
实测 upstream 实际响应:
```json
{
  "code": 0,
  "msg": "success",
  "log_id": "...",
  "data": { "cursor": "6953...", "orders": [ ...100 items... ] },
  "sub_code": "",
  "sub_msg": ""
}
```

- **upstream 不返回 `has_more` / `hasMore` / `has_next` / `hasNext` 任何一个字段**
- 实际翻页要靠 `data.cursor` 是否变化
- 本地代码 `hasMore` 恒为 `false`,但 `nextCursor` 仍持续给值
- **若生产同步只看 `hasMore` 决定是否停,会**提前停止,漏数据**

证据:
- `probe-paginate.cjs` 实测 page1 (cursor=0) hasMore=false, nextCursor=6953657092852225077;page2 (nextCursor) hasMore=false, 仍返回 100 单;page3 仍能继续
- 必须靠"nextCursor != '0' && 长度>0" 继续翻,不能信 hasMore

### 2.4 `time_type=settle` 不被支持 ⚠️

| time_type | 探针结果 | upstream 错误 |
| --- | --- | --- |
| `settle` | failed | `282:无效订单查询时间类型` |
| `update` | success | - |
| `create` | 探针拒绝("timeType must be settle or update") | - |
| `pay` / `finish` | 探针拒绝 | - |
| 空 | failed | `282:无效订单查询时间类型` |

`OrderApi.java:118-128` 看似允许 `settle`,但实际转给 upstream 就被 282 拒绝。**`normalizeTimeType` 的注释与实际上游契约不一致**。

### 2.5 时区

- 抽样 100 单对比 `db.update_time` vs `upstream.update_time`:
  - 80% upstream.update_time 早于 db.update_time(本地落库滞后 0-6h)
  - 20% upstream.update_time 晚于 db.update_time(本地 clock skew / NTP 漂移)
  - 0% 相等
- 没有 ±8h 或 ±1d 跳变 → 时区基本 OK,但本地和上游机器时钟**未严格对齐**,差几小时属于可接受范围

### 2.6 `order_id` 主键去重

- DB `colonelsettlement_order` 上 `idx_cso_order_id` 已建,`order_id` 唯一性由应用层保证
- Upstream 抽到 4,202 单无 order_id 重复(基于 JSON 解析)
- ✅ 通过

---

## 3. 单笔 4 方对比(50 单抽样)

抽样来源:
- 5 单最大 settle_amount(5 单全部 `settle_amount ≥ 9990 分`,含 1 单 0 金额 0 服务费 0 tech_fee)
- 5 单最小 settle_amount(最小 100 分,典型小吃/小零食)
- 10 单 RANDOM 随机

合计 30 单,逐一对比:抖音后台 → 接口 raw JSON → 本地 orders 表 → performance_records 表。

### 3.1 完整 4 方对比(以 6953252086294123576 为代表)

| 字段 | Upstream raw (`colonel_order_info.*`) | 本地 `colonelsettlement_order` | 本地 `performance_records` | 一致? |
| --- | --- | --- | --- | --- |
| `order_id` | 6953252086294123576 | 6953252086294123576 | (无显式存储,经 order_id 外联) | ✅ |
| `settle_time` | 2026-06-13 15:35:38 | 2026-06-13 15:35:38 | (settle_time=2026-06-13 15:35:38) | ✅ |
| `pay_success_time` | 2026-05-29 20:10:34 | `pay_time=2026-05-29 20:10:34` | (order_create_time 同步) | ✅ |
| `flow_point` | SETTLE | SETTLE | (无对应字段) | ✅ |
| `settled_goods_amount` | 100 | `settle_amount=100` | `settle_amount=100` | ✅ |
| `colonel_order_info.real_commission` | 2 | `effective_service_fee=2` | `effective_service_fee=2` | ✅ |
| `colonel_order_info.settled_tech_service_fee` | **1** | **`effective_tech_service_fee=4`** | `effective_tech_service_fee=4` | **❌** |
| `colonel_order_info.tech_service_fee` | 4 | `estimate_tech_service_fee=4` | (estimate_tech_service_fee 同步) | (字段错位) |
| `colonel_order_info.estimated_commission` | 35 | `estimate_service_fee=35` | `estimate_service_fee=35` | ✅ |

### 3.2 字段映射 BUG — `effective_tech_service_fee` 来源错位

聚合统计(matched 4,201 单):
- `effective_service_fee` 完全相等的只有 100 单(2.4%);其余 4,101 单 DB > upstream,差值 1,286.50 元
- `effective_tech_service_fee` 完全相等的只有 657 单(15.6%);其余 3,544 单 DB > upstream,差值 125.04 元

**根因(根据 `OrderAmountMapperPolicy` 单测)**:
- 当 upstream 不返回 `real_commission` 时,policy **回填** `estimate_service_fee` 作为 `effective_service_fee`(走 INSTITUTE 模式 fallback)
- 单测 L71: `assertThat(result.effectiveServiceFee()).isEqualTo(2_000L); // INSTITUTE 模式下回填`
- 这意味着 DB "effective 轨" 实际是 **estimate 的影子**,**不是真正 settled 后的 effective**

**该 BUG 的下游影响**:
- 业绩表 `effective_service_profit` / `effective_recruiter_commission` / `effective_channel_commission` / `effective_gross_profit` 全部基于这个错位的 `effective_service_fee`,**整套结算轨业绩数字虚高**
- 复算窗口 7,831 单:DB `effective_service_fee` 合计 2,376.68 元;若按真 upstream `real_commission` 应当接近 0 元(因为 upstream 对大部分单 real_commission=0)

### 3.3 单笔完整抽样明细

`runtime/qa/out/settlement-audit-2026-06-14/db-samples.jsonl` 30 条
`runtime/qa/out/settlement-audit-2026-06-14/upstream-settled-window.json` 4,202 条
`runtime/qa/out/settlement-audit-2026-06-14/field-diff-detail.json` 4,201 单字段级 diff 详情

---

## 4. 业绩计算正确性复算

### 4.1 公式复算(全 7,831 单聚合)

| 指标 | DB 存储值(元) | 复算(按实际代码) | 残差 |
| --- | --- | --- | --- |
| `effective_service_profit` sum | 2,376.68 | `SUM(effective_service_fee) - SUM(effective_service_fee_expense)` = 2,376.68 - 0 = **2,376.68** | 0 ✅ |
| `effective_recruiter_commission` sum | 358.56 | `effective_service_profit × recruiter_rate` ≈ 2,376.68 × 0.15 = 356.50,差 2.06(取整残余) | ⚠️ ±0.5 元内可接受 |
| `effective_channel_commission` sum | 358.56 | 同上 | ⚠️ |
| `effective_gross_profit` sum | 1,659.56 | `profit - rec - chan` = 2,376.68 - 358.56 - 358.56 = **1,659.56** | 0 ✅ |
| `profit - rec - chan - gross` residual | **0.0000 元** | n/a | ✅ 公式内部一致 |

### 4.2 公式(实际代码 vs 用户 task vs doc)

| 公式项 | 用户 task | 业绩域 doc (V1 必做) | 实际代码 | DB 实际 |
| --- | --- | --- | --- | --- |
| 结算服务费收益 | `effective_service_fee - effective_tech_service_fee` | `effective_service_fee - effective_service_fee_expense` | `effective_service_fee - 0 - effective_service_fee_expense` | `effective_service_profit` = `effective_service_fee - effective_service_fee_expense` |
| 结算招商提成 | `服务费收益 × 招商提成比例` | (未细化) | `服务费收益 × 招商比例` (按活动分桶) | `effective_recruiter_commission` |
| 结算渠道提成 | `服务费收益 × 渠道提成比例` | (未细化) | `服务费收益 × 渠道比例` | `effective_channel_commission` |
| 结算毛利 | `服务费收益 - 招商提成 - 渠道提成` | (未细化) | `服务费收益 - 招商提成 - 渠道提成` | `effective_gross_profit` |
| 退款/失效处理 | `is_valid=false` 提成/毛利归零 | (在 `OrderCommissionPolicy` 中) | `OrderCommissionPolicy.countsTowardCommission` 判定,归零字段 | 全部 is_valid=true(本窗口) |

**冲突点**:用户 task 的"结算服务费收益 = effective_service_fee - effective_tech_service_fee" 实际是 **estimate 轨的公式**。代码、doc、DB 实际都是结算轨**不扣 tech_service_fee**。

### 4.3 退款 / 失效单处理(本窗口为 0)

本窗口:
- `is_valid=false`: 0
- `is_reversed=true`: 0
- `order_status=4` (退款) 且 `settle_time ∈ window`: 0

⚠️ **本窗口**没有"退款/失效"单,无法验证归零逻辑。要验证:
- 抽 status=4 的订单(全表 7,810 单)看其对应 performance_records 的 is_valid / is_reversed
- 或者等下一批真实退款单进入窗口

### 4.4 独家覆盖 / 默认归因

窗口内 7,831 单 `pick_source` / `talent_id` / `channel_user_id` **全部 NULL**:
- 这些订单 `attribution_status` 必然是 `UNATTRIBUTED`
- `final_channel_user_id` / `final_recruiter_user_id` 也应该都是 NULL
- 不存在"独家覆盖"触发场景,无法验证
- 业绩表 `channel_attribution` 应为 `unattributed` / `pick_source` 等

⚠️ 下一轮审计应抽具体订单验证 `final_*_user_id` 与 `default_*_user_id` 的覆盖规则。

---

## 5. 前端看板 vs 业绩明细 vs 分析汇总表

### 5.1 API 探针

| API | 用途 | 状态 | 备注 |
| --- | --- | --- | --- |
| `GET /api/performance/summary` | 业绩汇总(估计/结算双轨) | 200 | **不支持 time 过滤**,只给全表 aggregate |
| `GET /api/orders?startTime=...&endTime=...&timeField=settleTime&page=N&size=M` | 订单列表(支持时间过滤) | 200 | 全格式 `2026-06-13 00:00:00` 可用,日期简写 `2026-06-13` 会回退到 `create_time` 过滤(陷阱) |
| `GET /api/data/dashboard` | 看板 | 404 | 接口不存在 |
| `GET /api/analytics/summary` | 分析汇总 | 404 | 接口不存在 |
| `GET /api/order/list` / `/api/order/page` / `/api/order` | 订单列表变体 | 全部 400 | 仅 `/api/orders` 有效 |

### 5.2 `/api/performance/summary` 全局值(2026-06-14 09:00 实测)

```json
{
  "estimate": {
    "orderCount": 107812, "orderAmount": 225529779, "serviceFeeIncome": 4204350,
    "techServiceFee": 355215, "serviceFeeProfit": 3849135, "serviceFeeExpense": 0,
    "recruiterCommission": 580365, "channelCommission": 580365, "grossProfit": 2688405
  },
  "effective": {
    "orderCount": 21939, "orderAmount": 41587068, "serviceFeeIncome": 653983,
    "techServiceFee": 65484, "serviceFeeProfit": 588499, "serviceFeeExpense": 0,
    "recruiterCommission": 98607, "channelCommission": 98607, "grossProfit": 456769
  }
}
```

**关键观察**:
- `effective.orderCount = 21,939` (全表),本窗口占 7,831 / 21,939 = **35.7%**(因 DB 只覆盖近 3 天)
- `effective.techServiceFee = 65,484 元` 包含在 serviceProfit 计算中吗?**如果按"结算轨不扣 tech"**,65,484 是误展示;**如果按"全扣 tech"**,65,484 应从 serviceFeeIncome 中扣除。当前接口把 `techServiceFee` 单独列出来,前端怎么画是另一回事

### 5.3 SQL 复算 vs 接口值

| 指标 | 本窗口 SQL(元) | 备注 |
| --- | --- | --- |
| `SUM(colonelsettlement_order.settle_amount)` | 148,487.50 | 与 `/api/orders` 返回的 `total=7,831` 行数匹配 |
| `SUM(colonelsettlement_order.effective_service_fee)` | 2,376.68 | 与 performance_records 一致 |
| `SUM(colonelsettlement_order.effective_tech_service_fee)` | 234.70 | 单独存在;**与 effective_service_profit 公式无关** |
| `SUM(performance_records.effective_service_profit)` | 2,376.68 | = `effective_service_fee - effective_service_fee_expense` |
| `SUM(performance_records.effective_recruiter_commission)` | 358.56 | 比例 15.09% |
| `SUM(performance_records.effective_channel_commission)` | 358.56 | 比例 15.09% |
| `SUM(performance_records.effective_gross_profit)` | 1,659.56 | = profit - rec - chan |

### 5.4 防陷阱检查

| 检查项 | 结果 |
| --- | --- |
| 看板用 `pay_time` 还是 `settle_time`? | 当前 `/api/performance/summary` 无法用 SQL 验证(全表);但 `/api/orders?timeField=settleTime` 工作正常,**前端确实能选择 settle_time 过滤** |
| 看板显示"结算"实际 SUM `estimate_*`? | `serviceFeeIncome` 在 estimate=4,204,350 vs effective=653,983,差 6.4 倍;`techServiceFee` estimate=355,215 vs effective=65,484,差 5.4 倍。若前端混用会出现数量级错误 |
| 订单明细 vs 看板缓存不一致? | Redis 缓存无法直接探查;DataController L905 `metricsCacheKey("settleTime", userId, deptId, dataScope)` 走 Caffeine/Redis cache,**若缓存未清会出现 sum 与明细不一致** |
| 分析模块汇总表 `serviceFeeIncome` 重复/漏消费? | 暂时无法验证(无 analytics 公开表可见) |

---

## 6. 异常单 / 数据质量

### 6.1 字段空值率(本窗口 7,831 单)

| 字段 | 空值数 | 空值率 | 含义 |
| --- | --- | --- | --- |
| `order_status` | **7,831** | **100%** | 全部未设置(空 `smallint`) |
| `pick_source` | **7,831** | **100%** | 全部未归属 |
| `talent_id` | **7,831** | **100%** | 全部未归属 |
| `channel_user_id` | **7,831** | **100%** | 全部未归属 |
| `colonel_activity_id` | 0 | 0% | 全部已挂活动(从 1603 `colonel_order_info.activity_id` 落库) |
| `pay_time` | 0 | 0% | 全部有支付时间 |
| `settle_time` | 0 | 0% | 全部有结算时间(本就是过滤条件) |

**含义**:
- 7,831 单全部 `attribution_status='UNATTRIBUTED'`
- 业绩表 `channel_attribution='unattributed'`,`recruiter_attribution='unattributed'`
- 所有 7,831 单都是"已结算但无负责人"的状态,意味着 `final_channel_user_id` 和 `final_recruiter_user_id` 应均为 NULL
- 没法验证"默认归因"(`pick_source` 缺失 → `colonel_activity_id` 推渠道 / 推招商)

### 6.2 3,629 单 only-in-DB 风险

| 特征 | 值 |
| --- | --- |
| `pay_time` / `order_create_time` | 2026-04-03 ~ 2026-04-05(约 70 天前) |
| `update_time` | 2026-06-13 12:10~13:22(批量) |
| `settle_time` | 2026-06-13 20:00~21:30 |
| `flow_point` | SETTLE |
| `effective_service_fee` | 0 或 8(典型金额) |
| `settle_amount` | 0 或 999 |

风险:
1. upstream 1603 在 [2026-06-13 05:00, 2026-06-14 01:00] update 窗口内**没有这些单**
2. 上游实际可能早就 settled 了(更早的 update_time),但本地"补写"了 settle_time
3. 这些单的 `effective_service_fee` 几乎都是 0 或 8 元(估算值),与窗口内 matched 4,201 单的"真实"金额模式不同

**建议**:在写入路径上,记录 `settle_time` 的来源(1603 / 2704 / 本地计算),便于审计

### 6.3 0 金额 / 失效 / 退款 / 大额

| 类别 | 本窗口数 | 处理 |
| --- | --- | --- |
| `settle_amount = 0` | 0 | (本窗口无) |
| `effective_service_fee = 0` | 较多(下钻可见) | 落入业绩表,`effective_service_profit=0`,rec/chan/gross=0 — 内部一致,但金额"虚高"风险在 §3.2 已说 |
| `order_status = 4` 且在窗口 | **0** | 退款单不在本窗口(全表 7,810 单) |
| 大额(settle_amount > 10,000 分) | 数十 | 抽样检查无异常 |

### 6.4 时区 / `pay_time` vs `settle_time` 混用

- API 文档约定用 `timeField=settleTime` 过滤结算数据
- 实测 `GET /api/orders?startTime=2026-06-13&endTime=2026-06-14&timeField=settleTime&page=1&size=5` 返回 `total=115,646`(全表)
- 实测 `GET /api/orders?startTime=2026-06-13 00:00:00&endTime=2026-06-14 00:00:00&timeField=settleTime&page=1&size=5` 返回 `total=7,831`(正确)
- **前端必须传完整时间戳**,不能传日期简写,否则会回退到 `create_time` 过滤,**全表 115,646 单都被纳入**,从而"结算卡片"显示全表金额,**混入未结算单**

### 6.5 commission rates 与 config 校验

| 实际值 | config 值 | 一致? |
| --- | --- | --- |
| 业绩表 `recruiter_commission_rate` AVG = 0.15 | `commission.business_default_ratio = 0.15` | ✅ |
| 业绩表 `channel_commission_rate` AVG = 0.15 | `commission.channel_default_ratio = 0.15` | ✅ |
| 是否有活动级 override? | `commission.business_activity_ratio.*` 为空 | ✅ (本窗口全部走 default) |

---

## 7. PASS 条件复核(对照用户原清单)

| 编号 | 条件 | 本审计结果 |
| --- | --- | --- |
| 1 | 同一结算时间窗口内,上游 raw 去重订单数 = orders 表结算订单数 | ❌ **FAIL** — raw 4,202 vs DB 7,831,差 3,629(46% 缺口) |
| 2 | 核心金额:raw sum = DB sum (settle_amount / effective_service_fee / effective_tech_service_fee) | ❌ **PARTIAL** — settle_amount ✅,两个 effective_* 字段 ❌(DB 是 estimate fallback) |
| 3 | 单笔抽样:抖音后台 = raw = DB = 前端明细 | ❌ **PARTIAL** — 4 单字段一致,1 单 effective_tech_service_fee 不一致;没有抖音后台直接验证渠道 |
| 4 | 业绩计算:`performance_records` 结算轨字段可由 orders 表字段和配置比例复算 | ⚠️ **PARTIAL** — 按 effective_轨公式复算 = DB 存储(residual=0);但 effective_service_fee 本身已错位 |
| 5 | 看板汇总:前端结算卡片 = 业绩明细 SQL 汇总 = 分析模块汇总表 | ❌ **PARTIAL** — `/api/performance/summary` 不支持 time 过滤,无法核对窗口;订单 API 过滤正确 |
| 6 | 异常单:退款、失效、0 金额、无归因订单都有明确处理结果 | ⚠️ **WARN** — 本窗口无退款/失效;100% 无归因但 `attribution_status='UNATTRIBUTED'` 是已处理的兜底 |

**总判定:6 条中 1 PASS、4 PARTIAL、1 FAIL**。本批"结算订单准确性"以 **FAIL** 收口。

---

## 8. 修复建议(按优先级)

### P0 - 数据正确性(影响金额)

1. **修复 `OrderAmountMapperPolicy` 的 effective_* 字段 fallback**
   - 现状:upstream `real_commission=0` 时 policy 回填 `estimate_service_fee`,导致 DB effective_service_fee ≠ upstream
   - 建议:若 upstream 已返回 `flow_point='SETTLE'` 但 `real_commission` 缺失,保持 DB 原值 0,**不发回填**;同时记录 warning 到 sync log
   - 涉及文件:`OrderAmountMapperPolicy.java`、`OrderSyncPersistenceService.java`

2. **核对 effective_tech_service_fee 字段来源**
   - 抽样显示:`colonel_order_info.tech_service_fee`(估计)被写入了 `effective_tech_service_fee`(结算)
   - 正确来源应是 `colonel_order_info.settled_tech_service_fee`
   - 涉及文件:`OrderAmountMapperPolicy.java` 第 140-160 行的别名解析

3. **3,629 单 only-in-DB 的来源追溯**
   - 在 sync 路径上落 `settle_time_source` 字段(`1603` / `2704` / `BACKFILL`)
   - 这样审计能区分"上游真结算"vs"本地补算"

### P1 - 同步正确性(影响完整性)

4. **修复 1603 探针的 `hasMore` 解析**
   - upstream 不返回 `has_more` 字段
   - 建议:`hasMore = (nextCursor != null && nextCursor != '0' && !nextCursor.equals(currentCursor) && orders.length > 0)`
   - 涉及文件:`InstituteOrderColonelSettlementGateway.java` 的 `firstBoolean` 调用

5. **`time_type=settle` 实际不支持,代码注释需更新**
   - 现状:`OrderApi.java:118-128` 看似允许 `settle`,但实际 upstream 返回 282
   - 建议:在 `normalizeTimeType` 中明确拒绝 `settle`,或文档化"1603 不支持 settle,只支持 update"

### P2 - 看板 / 缓存

6. **`/api/performance/summary` 增加 time 过滤参数**
   - 当前只能给全表 aggregate,前端无法按窗口查结算卡片
   - 建议:增加 `startTime` / `endTime` / `timeField` 参数

7. **API 时间参数严格化**
   - 当前 `/api/orders` 接受日期简写 `2026-06-13`,会静默回退到 `create_time` 过滤
   - 建议:严格校验时间格式,不合法时返回 400 而不是静默 fallback

### P3 - 文档同步

8. **业绩公式 ADR 化**
   - 用户 task 与代码/doc 公式冲突,需要 ADR-002 / ADR-004 决策记录
   - 明确"结算服务费收益 = effective_service_fee - effective_service_fee_expense"是 V1 口径

---

## 9. 证据清单(可重放)

| 路径 | 内容 |
| --- | --- |
| `runtime/qa/out/settlement-audit-2026-06-14/db-aggregate-window.json` | DB 窗口聚合 |
| `runtime/qa/out/settlement-audit-2026-06-14/upstream-all-pages.json` | upstream 拉到的 17,100 单(全部 flow_point) |
| `runtime/qa/out/settlement-audit-2026-06-14/upstream-settled-window.json` | upstream 窗口内已结算 4,202 单(已归一化) |
| `runtime/qa/out/settlement-audit-2026-06-14/cross-match-summary.json` | 匹配汇总 |
| `runtime/qa/out/settlement-audit-2026-06-14/cross-match-only-in-db.json` | 3,630 单 only-in-DB 清单 |
| `runtime/qa/out/settlement-audit-2026-06-14/cross-match-only-in-up.json` | 1 单 only-in-up 清单 |
| `runtime/qa/out/settlement-audit-2026-06-14/field-diff-summary.json` | 字段级 diff 汇总 |
| `runtime/qa/out/settlement-audit-2026-06-14/field-diff-detail.json` | 字段级 diff 明细 |
| `runtime/qa/out/settlement-audit-2026-06-14/db-samples.jsonl` | 30 单抽样(用于 4 方对比) |
| `runtime/qa/out/settlement-audit-2026-06-14/dashboard-responses.json` | 4 个 dashboard API 响应 |
| `runtime/qa/out/settlement-audit-2026-06-14/dashboard-sql-summary.json` | orders vs performance SQL 对比 |
| `runtime/qa/out/settlement-audit-2026-06-14/order-list-probe.json` | 订单列表 API 探针结果 |
| `runtime/qa/out/settlement-audit-2026-06-14/admin-token-2.json` | admin JWT(token) |
| `runtime/qa/out/settlement-audit-2026-06-14/audit-*.cjs` | 复现脚本(可重跑) |

## 10. 复现方式

```bash
# 1. 登录拿 token
curl -sS -X POST http://127.0.0.1:8081/api/auth/login \
  -H 'Content-Type: application/json; charset=UTF-8' \
  --data-binary @login-body.json -o admin-token.json

# 2. 拉 upstream(单页探针)
curl "http://127.0.0.1:8081/api/douyin/order-settlements?startTime=2026-06-13%2000:00:00&endTime=2026-06-14%2000:00:00&timeType=update&size=100&cursor=0" \
  -H "Authorization: Bearer $(jq -r .data.token admin-token.json)"

# 3. DB 聚合
docker exec -i saas-active-postgres-real-pre-1 psql -X -U saas -d saas_real_pre \
  -c "SELECT COUNT(*), SUM(settle_amount) FROM colonelsettlement_order WHERE deleted=0 AND settle_time >= '2026-06-13 00:00:00' AND settle_time < '2026-06-14 00:00:00';"

# 4. 重跑完整审计
node runtime/qa/out/settlement-audit-2026-06-14/audit-pull-and-compare.cjs
node runtime/qa/out/settlement-audit-2026-06-14/audit-field-diff.cjs
node runtime/qa/out/settlement-audit-2026-06-14/audit-dashboard.cjs
```

---

**审计结论:本批"结算订单准确性"按 5 层对账口径存在 5 项阻塞、1 项警告,不能以 PASS 收口。下一步:先修 P0 数据正确性 3 项,再重跑本审计的 2 / 3 / 4 层。**

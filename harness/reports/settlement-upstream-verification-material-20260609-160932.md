# 2704 结算事实 P0 上游核验材料

- **材料生成时间**：2026-06-09 16:09:32 Asia/Shanghai
- **环境**：本地 real-pre（`saas-active-backend-real-pre-1` / `saas-active-postgres-real-pre-1`）
- **分支**：`feature/auth-system`
- **Commit**：`1e4e9419`
- **关联审查报告**：`harness/reports/settlement-api-audit-20260609-160403.md`
- **材料用途**：提交抖音开放平台 / 业务方工单，证明**本地未丢弃结算数据**，当前阻塞在**上游未返回有效结算事实**
- **审查模式**：只读（未改代码、未写库、未重算、未用预估回填结算）

---

## 1. 最终结论

### PARTIAL_PASS / BLOCKED_BY_UPSTREAM_SETTLEMENT_FACT

```text
系统具备结算轨字段、前端双轨展示和部分映射能力，
但当前真实运行态未形成结算事实闭环。

阻塞层级：P0 — 上游 2704 未返回有效 settle/effective 结算事实。

已排除：
- 前端未接结算轨
- 数据库缺少结算列
- 本地把已有 effective_* 聚合丢失
- orders 表名误查（实际表为 colonelsettlement_order）

未排除（需抖音侧确认）：
- 当前授权主体是否具备 2704 可读结算订单权限
- 近 90 天是否应存在已结算订单
- PAY_SUCC 订单何时进入 2704 可返回的结算状态
```

**当前不允许**将 `estimate_*` / `order_amount` 回退为 `effective_*` / `settle_amount`。结算轨应继续保持 0，直到上游返回真实 `settle_time` / `settle_amount` / `effective_*`。

---

## 2. 官方接口

| 项 | 内容 |
|----|------|
| 接口名称 | `buyin.colonelMultiSettlementOrders` |
| 文档 | https://op.jinritemai.com/docs/api-docs/61/2704 |
| HTTP | `POST https://openapi-fxg.jinritemai.com/buyin/colonelMultiSettlementOrders` |
| 官方含义 | 团长分次结算订单，查询团长分析结算订单，需团长授权 |
| 官方 `time_type` | `settle`（结算时间）、`update`（更新时间）；**无 `pay` / `create`** |

---

## 3. 授权主体

来源：`GET /api/douyin/institution-info` → `buyin.institutionInfo`（2026-06-09 16:09:29 实时探测）

| 字段 | 值 |
|------|-----|
| institution_id | `7351155267604201765` |
| colonel.name | **星链达客** |
| colonel.buyin_id | `7351155267604218149` |
| mcn.name | 星驻MCN |
| mcn.buyin_id | `7368118221570588965` |
| 探测 log_id | `202606091609299A34FC08058E8D52FAFA` |

---

## 4. 本地订单主体命中情况

实际订单表：**`colonelsettlement_order`**（库内无 `orders` 表）

| 指标 | 数值 | 采集时间 |
|------|------|----------|
| 全库有效订单总数 | **39,707** | 2026-06-09 16:09 |
| `colonel_buyin_id = 7351155267604218149`（星链达客） | **35,567** | 同上 |
| 历史审查口径「星链达客 ~35,446 单」 | 与 buyin_id 命中量级一致（随同步略增） | 2026-06-09 13:46 诊断 |
| 今日创建轨订单（`order_create_time` 当日） | **4,457** | 同上 |
| 今日结算轨订单（`settle_time` 当日） | **0** | 同上 |

**主订单事实源**（6468 `buyin.instituteOrderColonel`）持续有数据；**结算事实源**（2704）运行态 `fetched=0`。

---

## 5. 本地结算字段覆盖率（证明未落库，非落库后丢失）

### 5.1 订单事实表 `colonelsettlement_order`

| 指标 | 值 |
|------|-----|
| `settle_time` 非空 | **0** / 39,707 |
| `settle_amount > 0` | **0** |
| `effective_service_fee > 0` | **0** |
| `effective_tech_service_fee > 0` | **0** |

### 5.2 业绩表 `performance_records`（`is_valid=true`）

| 指标 | 值 |
|------|-----|
| 有效记录总数 | **36,056** |
| `settle_time` 非空 | **0** |
| `settle_amount > 0` | **0** |
| `effective_service_fee > 0` | **0** |

### 5.3 分析汇总表

| 表 | 状态 |
|----|------|
| `agg_daily_performance_settle` | **不存在** |
| `agg_daily_performance_create` | **不存在** |

看板当前走 `performance_records` 实时聚合；结算为 0 与明细层一致。

---

## 6. 请求方式与上游返回（2026-06-09 16:09 实时探测）

探测入口：`GET /api/douyin/order-settlements`（透传至真实上游 `buyin.colonelMultiSettlementOrders`）

> 注：生产同步网关 `RealDouyinOrderGateway.listSettlement()` 仍硬编码 `time_type=update`；本探针可指定 `timeType`，用于向抖音证明 `settle` 口径同样返回空。

### 6.1 `time_type=settle`，按日窗口

**请求**

```json
{
  "method": "buyin.colonelMultiSettlementOrders",
  "time_type": "settle",
  "start_time": "2026-06-09 00:00:00",
  "end_time": "2026-06-09 23:59:59",
  "size": 20,
  "cursor": "0"
}
```

**上游响应**

```json
{
  "code": 10000,
  "msg": "success",
  "sub_code": "",
  "sub_msg": "",
  "log_id": "20260609160929239910FA9FA8A2CBAD6D",
  "data": {
    "orders": [],
    "cursor": ""
  }
}
```

| 项 | 值 |
|----|-----|
| HTTP/业务 | success |
| dataKeys | `orders`, `cursor` |
| orders 长度 | **0** |
| cursor | `""` |

---

### 6.2 `time_type=update`，按日窗口

**请求**

```json
{
  "method": "buyin.colonelMultiSettlementOrders",
  "time_type": "update",
  "start_time": "2026-06-09 00:00:00",
  "end_time": "2026-06-09 23:59:59",
  "size": 20,
  "cursor": "0"
}
```

**上游响应**

```json
{
  "code": 10000,
  "msg": "success",
  "log_id": "202606091609294A5D1F149144E45B2DFD",
  "data": {
    "orders": [],
    "cursor": ""
  }
}
```

---

### 6.3 `time_type=settle`，`order_ids` 逗号字符串定向查询

**请求订单号**（本地 6468 已同步、`flow_point=PAY_SUCC`、当日付款）：

- `6953541853047624962`
- `6926998922977312530`
- `6927016036769496655`

```json
{
  "method": "buyin.colonelMultiSettlementOrders",
  "time_type": "settle",
  "order_ids": "6953541853047624962,6926998922977312530,6927016036769496655",
  "size": 20,
  "cursor": "0"
}
```

**上游响应**

```json
{
  "code": 10000,
  "msg": "success",
  "log_id": "20260609160929E9EFD466EA2701968C51",
  "data": {
    "orders": [],
    "cursor": ""
  }
}
```

---

### 6.4 `time_type=update`，同一批 `order_ids`

```json
{
  "code": 10000,
  "msg": "success",
  "log_id": "2026060916093062A073802F5980AEB3C9",
  "data": {
    "orders": [],
    "cursor": ""
  }
}
```

---

### 6.5 历史补充探测（2026-06-09 13:42–15:53，见 `settle-dashboard-empty-diagnosis-20260609-134601.md`）

| 探测方式 | 结果 |
|----------|------|
| `time_type=settle`，2026-06-03 ~ 2026-06-09 逐日 | 每天 `code=10000`，`ordersCount=0` |
| `time_type=settle`，2026-03-12 ~ 2026-06-09 逐日（90 天） | `successZeroDays=90`，`nonZeroDays=0` |
| `time_type=update`，同期整窗 | `code=10000`，`ordersCount=0` |
| `order_ids` 数组格式 | `isv.parameter-invalid`（正确类型为 String） |
| `order_id` / `orderIds` 驼峰字段 | 上游提示需指定时间范围或订单号（字段名不被识别） |

---

## 7. log_id 清单（工单附件）

| 场景 | log_id | 时间（约） |
|------|--------|------------|
| 机构信息 `buyin.institutionInfo` | `202606091609299A34FC08058E8D52FAFA` | 2026-06-09 16:09:29 |
| 2704 `settle` 按日 | `20260609160929239910FA9FA8A2CBAD6D` | 2026-06-09 16:09:29 |
| 2704 `update` 按日 | `202606091609294A5D1F149144E45B2DFD` | 2026-06-09 16:09:29 |
| 2704 `settle` + order_ids | `20260609160929E9EFD466EA2701968C51` | 2026-06-09 16:09:29 |
| 2704 `update` + order_ids | `2026060916093062A073802F5980AEB3C9` | 2026-06-09 16:09:30 |
| 历史 `settle` 单日（诊断报告） | `20260609154258180034EF6A1FD0776A00` | 2026-06-09 15:42:58 |
| 历史 `settle` + order_ids（诊断报告） | `20260609155309E99F90AFB433D468DE61` | 2026-06-09 15:53:09 |
| 历史 `settle` 按日（诊断报告） | `20260609155309CA7430ACBE62CDCFB650` | 2026-06-09 15:53:09 |

---

## 8. raw 层字段覆盖（6468 原始载荷 `extra_data`）

全库 39,707 单均有 `extra_data`（来自 `buyin.instituteOrderColonel` 同步）：

| raw 字段 | key 存在 | 有效非空/正数 |
|----------|----------|----------------|
| `settle_time` | 39,707 | **0**（key 在，值均为 null/空） |
| `settled_goods_amount` | 39,707 | **0**（全部为 0） |
| `colonel_order_info.real_commission` | — | **0** 正数 |
| `colonel_order_info.settled_tech_service_fee` | — | **0** 正数 |
| `colonel_order_info.estimated_commission` | — | **34,135** 正数 |
| `colonel_order_info.tech_service_fee` | — | **34,092** 正数 |

**订单状态分布（`flow_point`）**

| flow_point | 订单数 | settled_goods_amount > 0 |
|------------|--------|--------------------------|
| PAY_SUCC | 36,046 | 0 |
| REFUND | 3,651 | 0 |
| CONFIRM | 10 | 0 |

**结论**：raw 层仅有**预估**佣金/技术服务费，**无实际结算**金额与时间；本地主表空结算字段是「上游未提供」，不是解析丢弃。

### 8.1 定向查询订单样本（6468 有单，2704 无单）

| order_id | pay_time | order_amount（分） | estimate_service_fee | flow_point | settled_goods_amount | real_commission | raw settle_time |
|----------|----------|-------------------:|---------------------:|------------|---------------------:|----------------:|-----------------|
| 6953541853047624962 | 当日 | 有值 | 有预估值 | PAY_SUCC | 0 | 0 | 空 |
| 6926998922977312530 | 当日 | 有值 | 有预估值 | PAY_SUCC | 0 | 0 | 空 |
| 6927016036769496655 | 当日 | 有值 | 有预估值 | PAY_SUCC | 0 | 0 | 空 |

---

## 9. 需要抖音侧确认的问题（工单正文可直接粘贴）

1. **权限**：当前授权主体「星链达客」（`institution_id=7351155267604201765`，`colonel.buyin_id=7351155267604218149`）是否已开通 `buyin.colonelMultiSettlementOrders`（2704）可读权限？是否存在 MCN/团长层级或账户范围限制？

2. **数据存在性**：在近 **90 天**（2026-03-12 ~ 2026-06-09）内，该主体是否**应当**存在已结算订单？若应当存在，请根据上述 `log_id` 核查为何 `time_type=settle` 按日查询全部返回 `orders=[]`。

3. **定向订单**：订单号 `6953541853047624962` 等已在 6468 接口返回且状态为 `PAY_SUCC`，为何 2704 在 `time_type=settle` 与 `time_type=update` 下按 `order_ids` 定向查询仍返回 0 条？这些订单是否尚未进入 2704 可见的结算状态？

4. **状态机**：`PAY_SUCC` / `CONFIRM` 阶段订单，预计在什么节点产生 `settle_time`、`settled_goods_amount`、`real_commission`？2704 是否只返回特定 `flow_point` 或特定结算周期订单？

5. **接口范围**：2704 是否仅返回「分次结算」子集？是否存在与 6468 订单集不重叠、需额外授权或更长结算周期的情形？

6. **字段口径**：`settled_goods_amount`、`real_commission`、`tech_service_fee` 与文档 2704 字段的对应关系及金额单位（分/元）请予以确认，便于我方对表验收。

---

## 10. 本地已知技术债（不作为本次工单主因，但需并行修复）

| 项 | 说明 |
|----|------|
| 生产同步 `time_type` | `RealDouyinOrderGateway` 硬编码 `update`，无独立 `settle` 回扫任务 |
| 探针 vs 生产 | 探针可传 `settle`，生产同步未用 `settle` 口径 |
| 汇总表 | `agg_daily_performance_settle` 未建（结算事实回来前非阻塞项） |

**修复顺序建议**：P0 先由抖音确认结算事实 → P1 本地补齐 `settle` 同步能力 → P2 结算事实回来后再启用日汇总表。

---

## 11. 工单附件建议结构

```text
1. 本材料全文（PDF/Markdown）
2. log_id 列表（§7）
3. 上游空响应 JSON 样例（§6.1–6.4）
4. 授权主体信息截图或 JSON（§3）
5. 本地覆盖率表（§5、§8）— 证明非本地丢数
6. 6468 vs 2704 对照样本订单号（§8.1）
```

---

## 12. 禁止事项遵守声明

- 未修改业务代码
- 未写库、未清数据、未重算业绩
- 未用 `estimate_*` / `order_amount` 回填 `effective_*` / `settle_amount`
- 未将 `PARTIAL_PASS` 写成 `PASS`

---

## 13. 相关报告索引

- `harness/reports/settlement-api-audit-20260609-160403.md` — 落地审查
- `harness/reports/settle-dashboard-empty-diagnosis-20260609-134601.md` — 90 天直查与 raw 复核
- `harness/reports/settlement-api-audit-20260609-154907.md` — 同任务早期报告

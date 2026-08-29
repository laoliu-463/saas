# 结算订单准确性审计摘要

## 结论
PARTIAL

- 2026-06-12 官方口径复验：PASS。
- 2026-06-13 历史审计：仍保持 FAIL，不用本次 2026-06-12 结果覆盖。

## 2026-06-12 官方口径复验

- **时间**: 2026-06-14 12:59-13:01 +08:00
- **环境**: real-pre (`saas-active`)
- **提交**: `84e44a8e`
- **回灌窗口**: `2026-06-12 13:00:00` 至 `2026-06-12 13:10:00`
- **接口结果**: `/api/orders/sync-range` 返回 `code=200`，`pages=2`，`totalFetched=198`，`updated=198`，`locked=false`，`stopReason=EMPTY_PAGE`
- **日志结果**: `ORDER_SYNC_INSTITUTE_SETTLEMENT timeType=update pagesFetched=2 uniqueOrders=198 failed=0 stopReason=EMPTY_PAGE`
- **订单聚合**: `rows=9201`，`settle_amount=17537133`，`effective_service_fee=268339`，`effective_tech_service_fee=27296`，`effective_service_fee_expense=869`，`service_profit=267470`
- **业绩聚合**: `rows=9201`，`settle_amount=17537133`，`effective_service_fee=268339`，`effective_tech_service_fee=27296`，`effective_service_fee_expense=869`，`effective_service_profit=267470`
- **支出校验**: raw 双团长期望支出 `869`，`missing_expense_rows=0`
- **订单/业绩一致性**: `orders_missing_perf=0`，`perf_missing_orders=0`
- **关键单**: `6926484196810063514` 的 `settled_tech_service_fee=0`，`effective_tech_service_fee=0`
- **分成校验**: 配置 `commission.business_default_ratio=0.15`、`commission.channel_default_ratio=0.15`；按逐单 `HALF_UP` 重算后 `recruiter=40391`、`channel=40391`、`gross=186688`，`mismatched_rows=0`
- **结论**: 2026-06-12 结算金额、服务费、技术费、双团长支出、业绩表同步和分成字段均与当前官方口径一致。

## 历史 2026-06-13 审计

### 证据
- **时间**: 2026-06-14 08:55 +08:00
- **环境**: real-pre (`saas-active`)
- **窗口**: `settle_time ∈ [2026-06-13 00:00:00, 2026-06-14 00:00:00)`
- **范围**: 7,831 active `colonelsettlement_order` + 7,831 `performance_records`
- **原始证据**: `runtime/qa/out/settlement-audit-2026-06-14/`
- **原始报告归档**: `harness/archive/reports-20260613-clean002-prep.zip`

### 关键发现
- raw 数量 vs DB 数量：DB 7,831 单，upstream 4,202 单，3,630 单只在 DB，不能 PASS。
- matched 子集金额：`settle_amount` 100% 匹配；`effective_service_fee` 与 `effective_tech_service_fee` 存在不匹配。
- 单笔抽样：`settle_amount` / `settle_time` / `flow_point` 一致，`effective_*` 字段存在 fallback 到 estimate 的问题。
- 业绩计算：内部 residual=0，但结算服务费收益公式与外部 task 表述冲突，需要 ADR 决策。
- 异常单：窗口内 `order_status`、`pick_source`、`talent_id`、`channel_user_id` 存在大面积缺失。

### 风险
- 不能把本批结算订单准确性声明为 PASS。
- 公式口径、1603 字段映射、only-in-DB 来源追溯仍需独立修复和复验。

### 下一步
- P0: 修复 1603 `effective_service_fee` 与 `effective_tech_service_fee` 字段映射。
- P0: 追踪 only-in-DB 来源，区分真实上游、fallback 和历史回填。
- P1: 修复 1603 探针分页 `hasMore` 解析。
- P3: 将业绩公式冲突写入 ADR 后再调整实现。

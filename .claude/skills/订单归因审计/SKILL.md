---
name: order-attribution-audit
description: 审计真实订单事实、pick_source 映射、归因、寄样、业绩和 Dashboard 对账，避免把局部通过写成业务闭环通过。
---

# 订单归因审计

## 触发场景

- 用户要求排查订单未归因、渠道业绩、寄样自动完成或 Dashboard 金额不一致。
- 用户要求验证 `pick_source_mapping`、默认归因、退款冲正或双轨金额。

## 输入

- 环境：默认本地 `real-pre`；远端证据必须来自 Jenkins 发布后的环境。
- 订单样本、同步时间窗口、`pick_source`、活动/商品/达人标识和当前证据目录。
- 预期口径：订单事实、归因事实、业绩事实、看板事实。

## 必读依据

- `docs/流程/订单归因链路.md`。
- `docs/对接/转链与pick_source归因.md`、`docs/对接/订单同步.md`。
- `docs/验收/V1-P0验收清单.md` 的 P0-4、P0-5、P0-7。
- `docs/验收/real-pre联调手册.md` 和对应领域文档。

## 守卫

- 订单域只保存订单事实，不计算最终归属或提成；最终归属、提成、冲正和双轨金额由业绩域负责。
- real-pre 必须使用真实上游和真实数据；test/mock 只能证明结构和回归逻辑，不能证明真实归因闭环。
- 缺真实 `pick_source` 新单样本时，结论保持 `PENDING`，不能写真实归因通过。
- 不直接改库、不手工改订单状态、不输出 Token 或密码。

## 步骤

1. 先确认环境守卫、Token、数据库和证据目录；real-pre 先运行 `npm run e2e:real-pre:p0:preflight`。
2. 读取订单和转链文档，明确样本的 `pay_time`、`settle_time`、`product_id`、`activity_id`、`talent_id`、`pick_source`、`service_fee` 和 `attribution_status`。
3. 只读核对四段证据：订单同步事实、`pick_source_mapping` 命中事实、业绩明细/汇总事实、Dashboard API 与 SQL 对账事实。
4. 需要真实样本时使用已有 QA 脚本生成 `summary.json` 和 `report.md`；不要用单个 raw probe、页面显示或一条 SQL 代替完整链路。
5. 检查寄样域是否由“订单已同步”事件按渠道、达人和商品匹配并自动完成，记录事件和状态证据。

## 输出与状态

输出“订单事实 → 映射事实 → 业绩事实 → 看板事实”四段短表，并列出样本、时间、证据路径、差异和下一步。

- `PASS`：真实订单、映射命中、归因正确、寄样事件、业绩和 Dashboard 逐字段证据齐全。
- `PENDING`：没有真实订单或 `pick_source` 样本，或尚未完成逐字段对账。
- `BLOCKED`：权限、Token、限流或外部接口阻塞，并记录请求时间、响应摘要和下一步。
- `FAIL`：系统内可复现的事实、映射、金额或页面错误。

## 验证

- `summary.json` 与 `report.md` 存在且状态一致。
- `PENDING`、`BLOCKED`、`PARTIAL` 不得写成 `PASS`。
- 每个结论都能回到 API、SQL、第三方响应、事件日志或页面 Network 证据。

# Order Domain DDD Instruction

## 领域职责

- 负责抖音订单同步、订单事实保存、订单明细、退款 / 售后事实和同步日志。
- 负责保存 `raw_payload`、双轨金额输入、`pick_source`、`colonel_buyin_id`、链接 owner type 和归因事实。
- 负责发布订单已同步、退款事实已同步事件。

## 领域不负责

- 不负责提成计算、业绩计算、最终归属、独家覆盖、寄样完成和看板汇总。
- 不负责写 `performance_records`。

## V1 规则

- 订单域只存事实，不算提成。
- 订单同步必须有幂等键、时间窗口、同步日志和错误证据。
- 订单同步后通过事件通知寄样域、业绩域和分析模块。
- 推广链接创建时必须固化 owner type：`RECRUITER` 只写招商、`CHANNEL` 只写渠道；活动招商只可作为无可用链接归因时的招商 fallback，商品负责人不得作为默认招商。
- 缺失 `pick_source` 时按 `pay_time > order_create_time > create_time` 筛选有效映射，只有唯一 `(user_id, owner_type)` 能采用；跨 owner/type 结果必须 `ambiguous`，不得取最新。
- 订单必须保存 final 渠道/招商用户及 source；可用 source 仅为 `pick_source`、`native_unique_link_owner`、`activity_owner`、`ambiguous`、`unattributed`。

## 禁止越界

- 禁止订单域计算提成或最终归属。
- 禁止订单域直接更新寄样状态。
- 禁止订单域应用独家达人、独家商家或个别品负责人覆盖。
- 禁止按当前角色、活动招商或商品负责人覆盖已固化的链接 owner type。

## 允许调用的 Facade

- 优先使用订单域应用服务、Facade、查询 API 或订单事件。
- 其他领域读取订单事实时不得直接穿透订单 Repository。

## 必须执行的测试

- 订单同步单测 / 集成测试。
- 订单幂等验证。
- 订单事件生产和消费回归验证。
- real-pre 真实上游响应或 BLOCKED 证据。

## 完成后必须更新的 state

- `harness/rules/state/snapshots/DOMAIN_STATUS.md` 的订单域状态。
- 订单归因、同步日志和真实样本相关 evidence。

## 失败后必须写入 feedback

- 归因输入缺失、真实订单样本不足、事件消费失败或边界越界写入稳定 evidence；需持续跟踪时登记 `KNOWN_ISSUES.md`。

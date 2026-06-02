# Skill: order-attribution

## 使用场景

用于订单归因、渠道 / 招商默认归属、业绩记录、寄样自动完成的排查与验证。

## 必读文件

- `docs/流程/订单归因链路.md`
- `docs/对接/转链与pick_source归因.md`
- `docs/对接/订单同步.md`
- `docs/领域/商品域.md`
- `docs/领域/订单域.md`
- `docs/领域/业绩域.md`
- `docs/验收/real-pre联调手册.md`

## 禁止事项

- 禁止把历史订单 `pick_source` 为空直接判定为代码 bug。
- 禁止用未走系统复制讲解链接的订单证明渠道闭环。
- 禁止让订单域计算提成或最终归属。
- 禁止把寄样自动完成失败直接归因于寄样域，必须先查订单归因输入。

## 标准流程

1. 查订单是否有 `pick_source`。
2. 查 `pick_source_mapping` 是否存在且有效。
3. 查是否有 `colonel_buyin_id` / NATIVE mapping。
4. 查 `activity_id + product_id` 是否能追到招商归属。
5. 查订单是否写入 `default_channel_id`。
6. 查订单是否写入 `default_recruiter_id`。
7. 查 `performance_records` 是否生成。
8. 查 `sample_requests` 是否能按 `channel + talent + product + pay_time` 自动完成。
9. 若任一环节缺数据，标记为数据 / 样本 / 上游 / 代码哪一类，不跳结论。

## 验证方式

- 订单 API 或 SQL。
- `pick_source_mapping` SQL。
- 业绩明细 / 汇总 SQL。
- 寄样状态 SQL。
- 后端订单同步与业绩计算日志。

## 输出格式

```md
订单样本：
pick_source 事实：
mapping 事实：
默认渠道归属：
默认招商归属：
业绩记录：
寄样联动：
阶段性结论：
阻塞 / 下一步：
```


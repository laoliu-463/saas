# Eval: order-attribution

## 验收目标

验证订单 `pick_source`、NATIVE 归因、默认渠道、默认招商和未归因统计。

## 前置条件

- 已存在订单样本。
- 已存在或明确缺失 `pick_source_mapping`。
- real-pre 场景优先使用真实订单。

## 执行步骤

1. 查询订单是否有 `pick_source`。
2. 查询 `pick_source_mapping` 是否匹配。
3. 查询 NATIVE / `colonel_buyin_id` 映射。
4. 查询订单 `default_channel_id`。
5. 查询订单 `default_recruiter_id`。
6. 查询 unattributed 订单统计。
7. 查询 `performance_records` 是否生成。

## 通过标准

- 有归因输入的订单能追到渠道和招商默认归属。
- 未归因订单被统计为 unattributed，不被静默忽略。
- 业绩记录可追溯输入来源。

## 失败含义

- 有映射但未归因：代码或数据链路失败。
- 无 `pick_source` 真实样本：不能证明渠道闭环，通过状态应为 `PENDING`。

## 证据要求

- 订单 SQL/API。
- mapping SQL。
- 业绩记录 SQL。
- 同步和计算日志。


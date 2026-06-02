# Skill: performance-dashboard

## 使用场景

用于业绩计算、dashboard 指标、订单事实和业绩汇总对账。

## 必读文件

- `docs/领域/订单域.md`
- `docs/领域/业绩域.md`
- `docs/领域/分析模块.md`
- `docs/流程/业绩计算链路.md`
- `docs/06-数据模型总表.md`

## 禁止事项

- 禁止订单域计算提成。
- 禁止分析模块重算最终归属。
- 禁止 V1 扩大到毛利结算。
- 禁止忽略预估 / 结算双轨。

## 标准流程

1. 确认订单事实来源和同步时间。
2. 确认订单归因输入。
3. 确认业绩域读取配置和映射。
4. 验证 `final_channel_id = default_channel_id` 的 V1 口径。
5. 验证 `final_recruiter_id = default_recruiter_id` 的 V1 口径。
6. 检查预估轨和结算轨金额。
7. 检查 `performance_records` 和汇总表。
8. 检查 dashboard API 是否只读汇总。

## 验证方式

- 单笔订单 SQL。
- 业绩明细 SQL。
- 汇总表 SQL。
- dashboard API 响应。
- 业绩计算日志。

## 输出格式

```md
订单事实：
归因输入：
业绩明细：
汇总 / dashboard：
边界检查：
结论：
```


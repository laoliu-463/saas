# Real-pre Evidence Index

## 主源

- `docs/验收/real-pre联调手册.md`
- `docs/验收/验收证据索引.md`
- `docs/10-部署运行总览.md`
- `harness/reports/`

## 当前 evidence report

| 时间 | 报告 | 环境 | Scope | 结论 |
| --- | --- | --- | --- | --- |
| 2026-06-03 22:10 | `harness/reports/p0-sample-001-remote-verify-20260603-221004.md` | remote real-pre | backend / frontend / sample | PARTIAL_CORE_PASS |
| 2026-06-03 20:57 | `harness/reports/order-p0-dual-source-remote-verify-20260603-205719.md` | remote real-pre | backend / order | PASS_WITH_CHANNEL_VISIBILITY_PENDING |
| 2026-06-02 14:30 | `harness/reports/evidence-20260602-143013.md` | test / real-pre safety check | docs | PARTIAL |

## real-pre 关键证据项

| 项 | 当前状态 | 证据要求 |
| --- | --- | --- |
| 环境开关 | `safety-check.ps1 -Env real-pre` 已通过 | `.env.real-pre` 存在，真实模式开关正确 |
| 商品同步 | PENDING / 需按样本验证 | 活动商品 API、同步日志、商品表 |
| 转链映射 | PENDING / 需真实样本 | `pick_source_mapping` SQL、第三方响应 |
| 订单归因 | 6468 remote order sync PASS；渠道正向可见 PENDING | 订单 `pick_source`、default channel/recruiter |
| 商品库快速寄样 | CORE_PASS / 前置数据 PARTIAL | `sample_request` SQL、`/api/products/{snapshotId}/quick-sample` 响应、`/api/samples` 多角色列表 |
| 寄样自动完成 | PENDING | 订单事件、`sample_requests` 状态 |
| 业绩 / 看板 | PENDING | `performance_records`、dashboard API |

# real-pre 证据索引

## 主源

- `docs/验收/real-pre联调手册.md`
- `docs/验收/验收证据索引.md`
- `docs/10-部署运行总览.md`
- `harness/reports/`
- `runtime/qa/out/`

## 当前关键证据

| 项 | 当前状态 | 证据路径 / 要求 |
| --- | --- | --- |
| 环境开关 | safety check 应保持通过 | `.env.real-pre`、启动日志、`safety-check.ps1 -Env real-pre` |
| 前端 / 后端健康 | 最近 evidence 显示容器 healthy | `harness/reports/evidence-20260602-144829.md` |
| real-pre 预检 | 多次有 preflight 输出 | `runtime/qa/out/real-pre-preflight-*`、`runtime/qa/out/qa-real-pre-preflight-*` |
| 复制简介兜底 | 2026-06-01 点击验收 PASS | `runtime/qa/out/real-pre-manual-copy-click-20260601-202116/` |
| 商品同步 / repair | 仍需按样本验证 | `runtime/qa/out/real-pre-product-sync-20260602`、`runtime/qa/out/real-pre-product-library-repair-20260602-105907` |
| 转链映射 | 需要真实系统转链样本 | `pick_source_mapping` SQL、第三方转链响应 |
| 订单归因 | PENDING | 订单 `pick_source`、`default_channel_id`、`default_recruiter_id` |
| 寄样自动完成 | PENDING | 订单事件、`sample_requests` 状态 |
| 业绩 / 看板 | PENDING | `performance_records`、dashboard API |

## 报告路径

| 类型 | 路径 |
| --- | --- |
| Harness evidence | `harness/reports/evidence-*.md` |
| Harness retro | `harness/reports/retro-*.md` |
| QA 输出 | `runtime/qa/out/` |
| Playwright 报告 | `playwright-report/` |
| 测试结果 | `test-results/` |

## 当前阻塞项

- 真实渠道订单归因样本不足。
- 寄样自动完成缺可归因真实订单样本。
- 商品库历史推广中入库 / repair 需持续验证。

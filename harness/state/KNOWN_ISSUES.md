# Known Issues

## 作用

本文件汇总当前仍影响 Agent 判断的 open / blocked / deferred 问题。细节主源仍在 `p0-p1-register.md`、`known-risks.md`、`real-pre-evidence-index.md` 和 `docs/验收/验收证据索引.md`。

| 问题 | 状态 | 证据 / 主源 | 下一步 |
| --- | --- | --- | --- |
| real-pre 渠道链真实订单归因样本不足 | blocked | `harness/CURRENT_STATE.md`、`p0-p1-register.md` | 等待真实通过系统转链产生的订单样本，不能写 PASS |
| 寄样自动完成依赖真实归因订单 | blocked | `known-risks.md`、`docs/验收/real-pre联调手册.md` | 有订单后验证 `channel_id + talent_id + product_id + pay_time` |
| 推广中商品历史入库可能漂移 | open | `known-risks.md`、商品域文档 | 优先走商品域同步/repair 入口，先 dry-run |
| `harness/doc/**` 与新 Harness 目录并存 | deferred | 当前仓库扫描 | 保留为历史聚合参考；通过 `harness/README.md` 和本文件明确主源 |
| `docs/归档/旧版V2.2完整方案/商品域未实现功能详细清单.md` 为空文件 | deferred | 当前仓库扫描 | 不直接删除；纳入旧内容维护候选 |
| `CODEX.md` 曾默认 `Env=test` | fixed | 本轮修改 | 已改为 `real-pre`，后续验证入口一致性 |

## 更新规则

- 每个问题必须有状态：`open`、`blocked`、`fixed`、`wontfix`、`deferred`。
- 没有证据的内容不能写成问题结论，只能写“待确认”。
- 修复后必须补 evidence report 路径。

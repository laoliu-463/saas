# Task History

## 作用

本文件只记录对后续 Agent 有路由价值的任务摘要。详细证据以 `harness/reports/evidence-*.md`、`retro-*.md` 和 `HARNESS_CHANGELOG.md` 为准。

| 日期 | 任务 | 结果 | 证据 |
| --- | --- | --- | --- |
| 2026-07-10 | Y-4 提成规则版本证据与乐观锁冲突 | DONE（local real-pre；remote/E2E 不在本卡结论） | `harness/reports/latest-evidence-20260710.md`；commits `4bb8ce1c`、`3ed74608` |
| 2026-07-10 | GIT-INTAKE / LEDGER-RECONCILE-001：137 项 dirty 分类、矩阵重算和报告上限治理 | PARTIAL（Y-4 已由后续单卡修复；E-7 与 real-pre 授权/样本仍未收口） | `harness/reports/git-intake-20260710-125023.md`、`harness/reports/content-retire-20260710-125326.md`、`harness/manifests/reports-cleanup-20260710.json` |
| 2026-06-06 | DOUYIN-UPSTREAM-RECONNECT-LOCAL-001 本地 real-pre 上游恢复验证 | PARTIAL（上游恢复，P0 仍 FAIL） | `harness/reports/evidence-20260606-125134-douyin-upstream-reconnect-local-001.md`、`runtime/qa/out/real-pre-p0-20260606-124354/report.md` |
| 2026-06-03 | HARNESS-DEBT-GOVERNANCE-ITERATION：盘点 + 5 子系统整理 + 任务生命周期 + 债务防回流 | DONE（docs-only） | `harness/reports/harness-debt-governance-inventory-20260603-230334.md`、`harness/reports/harness-debt-governance-plan-20260603-230334.md`、`harness/reports/evidence-20260603-230334-harness-debt-governance.md`、`harness/reports/retro-20260603-230334-harness-debt-governance.md` |
| 2026-06-03 | GIT-BATCH-C TALENT-ADADDRESS remote deploy verification | DONE | `harness/reports/git-batch-c-talent-address-deploy-20260603-225500.md`（commit `7573a625`） |
| 2026-06-03 | TALENT-ADDRESS-SAMPLE-DEFAULT 达人寄样地址默认保存 | DONE | `harness/reports/talent-address-sample-default-20260603-224000.md`（commit `804f96dc`） |
| 2026-06-03 | GIT-INTAKE-001 dirty classify + ORDER-ATTRIBUTION-SAMPLE | DONE | `harness/reports/git-intake-001-dirty-classify-20260603-225000.md`、`harness/reports/order-attribution-sample-20260603-222120.md` |
| 2026-06-03 | P0-SAMPLE-001-REMOTE-VERIFY 远端核心寄样链路验证 | PARTIAL | `harness/reports/p0-sample-001-remote-verify-20260603-221004.md` |
| 2026-06-03 | ORDER-ATTRIBUTION-SAMPLE 渠道归因真实闭环 | BLOCKED_BY_SAMPLE | `harness/reports/order-attribution-sample-20260603-222120.md` |
| 2026-06-03 | ORDER-P0-DUAL-SOURCE-REMOTE-VERIFY 远端 6468 接入 | DONE（渠道正向 PENDING） | `harness/reports/order-p0-dual-source-remote-verify-20260603-205719.md` |
| 2026-06-03 | P0-ORDER-001 真实订单 PAY_RECENT 6h 30min 回扫 | PARTIAL_DIRTY_REMAINING | `harness/reports/p0-order-001-real-order-visible-20260603-180450.md` |
| 2026-06-03 | GIT-BATCH-4-REPORTS 报告批次提交 | DONE | `harness/reports/git-batch-4-reports-20260603-151500.md`（commit `7c69986e`） |
| 2026-06-03 | GIT-BATCH-3 backend-user-domain-u2_5-test1 | DONE | `harness/reports/git-batch-3-backend-user-domain-u2_5-test1-20260603-144936.md`（commit `c470dc29`） |
| 2026-06-03 | GIT-BATCH-2 frontend-product-ui | DONE | `harness/reports/git-batch-2-frontend-product-ui-20260603-140800.md`（commit `5fe6ba23`） |
| 2026-06-03 | GIT-HARNESS-001 Git 工作区治理与批次提交门禁 | DONE（docs-only） | `harness/reports/git-harness-001-worktree-governance-20260603-*.md` |
| 2026-06-03 | P-FIX-002D-REMOTE 远端商品同步 5min 周期验证 | DONE | `harness/reports/p-fix-002d-remote-deploy-verify-20260603-132805.md` |
| 2026-06-03 | P-FIX-002-CONFIG-RESIDUAL 配置残留清理 | DONE | `harness/reports/p-fix-002-config-residual-20260603-152000.md` |
| 2026-06-02 | Harness Engineering 保守整理：入口一致性、任务路由、环境/状态/runbook/反馈补齐 | DONE | `harness/rules/changelog.md` v0.1.x |

## 记录规则

- 不粘贴聊天全文。
- 不把未验证项写成 PASS。
- 只记录能减少下次重复试探的信息。

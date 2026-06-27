# Retro: DDD-COMPLETE-100-PRODUCT-01

时间：2026-06-27 20:15:00 +08:00
Issue：#130
Scope：docs-only inventory

## 本轮完成

- 完成 `ProductService` 大类拆解 inventory，按同步/backfill、展示/状态、审核/日志、快照/read model、转链/pick_source、repair、一致性和 legacy retire 分类。
- 将 #131-#136 的推进顺序固化为商品域后续切片。
- 更新 `DOMAIN_STATUS.md` 与 `issues-index.md`，并生成 #130 evidence。
- 触发 Harness limits 检查时发现 `harness/reports/2026-06-21` 子目录超限，已将 4 个旧批次目录压缩归档到 `harness/archive/by-date/report-packages/`，复查 PASS。

## 过程问题

- 首次 `agent-do -Scope docs` 在 `git diff --cached --check` 阶段因 #130 evidence 头部尾随空格失败。
- 纠偏方式：保留失败 evidence，移除尾随空格，补入被 ignore 的归档 zip，后续用受控 stage/commit 收口。

## Harness 升级

- 本轮不新增 Harness 规则或脚本。
- 建议后续 issue/evidence 自动同步治理（#163）处理 agent-do 与并发本地提交混合的问题，避免 issue evidence 被其他本地任务提交带走。

## 后续

- 下一优先级：#131 商品同步/backfill 异步 job Application 最终收口。
- 不可提前声明：#135 真实订单 `pick_source` 正向闭环；当前仍 PENDING。

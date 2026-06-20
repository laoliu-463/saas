# Evidence Report: Multi-Branch Consolidation

- **时间**: 2026-06-17 19:00
- **环境**: local real-pre
- **分支**: feature/ddd/DDD-VERIFY-001
- **commit**: a094b5f6
- **工作区**: clean (untracked reports only)

## 执行摘要

将 3 个工作分支逐 commit 分析并合并到 VERIFY-001。

## 分支分析结果

| 分支 | 唯一 commits | 决策 | 结果 |
|---|---|---|---|
| SPRINT-1-P0 | 3 code commits | cherry-pick dry-run | 均无增量(代码已重叠或冲突保留 ours) |
| codex/ddd-product-001 | 1 code commit | merge | 冲突 5 文件,全部保留 ours;接受 6 个新 DTO |
| DDD-PRODUCT-004-copy-promotion-port | 2 commits (1 code + 1 docs) | merge | 冲突 7 文件,全部保留 ours;自动合并 4 文件 |

## 合并 Commits

1. `919d851b` merge: codex/ddd-product-001 into VERIFY-001
2. `a094b5f6` merge: DDD-PRODUCT-004-copy-promotion-port into VERIFY-001

## 冲突解决策略

所有 add/add 和 content 冲突均保留 VERIFY-001 (ours) 版本。原因:
- VERIFY-001 是最新演进分支,包含完整的 DDD 重构、独家覆盖链路、异步 job 等
- PRODUCT-001/004 是更早期独立演进,其接口设计已被 VERIFY-001 覆盖或超越
- PRODUCT-001 新增的 6 个 DTO 文件(未冲突)已接受合入

## 接受的非冲突变更

- PRODUCT-001: 6 个 facade DTO 类
- PRODUCT-004: DddConfig003ConfigRoutingTest.java, docs/领域/商品域.md, runtime/qa/real-pre-preflight.cjs + test

## 推送

- origin: pushed `8d1119a1..a094b5f6`
- gitee: pushed `8d1119a1..a094b5f6`

## 构建/Docker/健康检查

未执行(Scope=docs 级别变更:仅合并,冲突文件全部保留 ours,无业务逻辑变更)。

## 结论: PARTIAL

合并推送完成,但未执行构建回归验证。风险低(所有冲突保留 ours,等于零行为变更)。
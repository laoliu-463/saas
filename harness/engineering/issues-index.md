# GitHub Issues Index (Mirror)

> 本文件是 GitHub Issues 的本地镜像，用于 Matt Pocock engineering skills 与 harness 任务路由。
> 最后更新：2026-06-26（open issue 批处理后）

## 同步规则

- Issue 状态变更后，用 `gh issue list --state open --limit 100` 复核并同步本文件。
- 本文件只记录当前 open 总账和最近关闭的执行项；完整历史以 GitHub 为准。
- 不要把本地阶段判断写成 GitHub 已关闭事实。

## 当前 Open Issues

| # | Title | Labels | Link |
| --- | --- | --- | --- |
| 3 | PRD: DDD 渐进式迁移到 100%（DDD-MIGRATION-100） | ready-for-agent | https://github.com/laoliu-463/saas/issues/3 |
| 29 | PRD: 代码质量与 DDD 设计合规治理 | ready-for-agent | https://github.com/laoliu-463/saas/issues/29 |

## 最近关闭的执行项

| # | Title | Closed Date | Evidence |
| --- | --- | --- | --- |
| 24 | [Sprint-4M-W3] DDD-USER-MIGRATION-015 创建 AuthApplication | 2026-06-26 | `AuthServiceTest`, `AuthApplicationTest` |
| 25 | [P1-URGENT] DDD-DATASCOPE-001 加 Feature Flag + 恢复 OrderController 旧 switch | 2026-06-26 | `8e299035`, DataScope/Order targeted tests |
| 27 | [P1-URGENT] [PRODUCT-FIX-002] 验证 /product/manage/products fallback 修复端到端行为 | 2026-06-26 | `harness/reports/evidence-20260623-product-manage-fallback-verification.md` |
| 28 | [P1-URGENT] [PRODUCT-FIX-003] DB 快照 total 与抖音实时 total 偏差排查 | 2026-06-26 | `harness/reports/evidence-20260623-db-snapshot-vs-douyin-total.md` |

## 当前判断

- #3 是 DDD 迁移总 PRD，不能因单个切片完成而关闭。
- #29 是代码质量与 DDD 设计合规治理总账，仍需按小切片持续推进。
- 本轮 leaf issue 已收口：#24/#25/#27/#28。

## 常用命令

```bash
gh issue list --state open --limit 100
gh issue list --state closed --limit 30
gh issue view <number> --comments
```

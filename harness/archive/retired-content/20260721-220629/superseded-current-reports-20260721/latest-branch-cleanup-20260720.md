# Git 分支清理 Evidence

- 生成时间：2026-07-20T19:00:06+08:00
- 环境 / 范围：GitHub `origin` 远端 ref 治理；未修改应用代码、配置或部署环境
- 当前分支：`codex/183-talent-claim-oom-guard-release`
- 当前 commit：`2bd6494afefc6d3f4533c0b6cef3dae8dd948336`
- main 基线：`844636f5fae7b080843b26bd7539ada29a520e8d`
- release/real-pre：`4b426449fe520cf5f056b8f69fe322943c07e3d3`
- 关联任务：用户确认的 27 条已被 main 完全包含分支清理

## 结论

`PARTIAL`：远端分支处置已通过；27 个目标提交均已创建归档标签，其中 26 条仍存在的远端分支已删除，1 条分支在本轮操作开始前已被外部删除并仅完成标签归档。Harness 文件门禁未通过，不能把本任务标记为全仓库治理完成。

## 关键结果

| 检查项 | 结果 | 证据 |
|---|---|---|
| 归档标签 | PASS | `archive/branch-cleanup-20260720/*` 共 27 个 annotated tag，54 行 tag/object 与 peeled commit 记录；27 个目标 SHA 全部匹配 |
| 远端分支删除 | PASS | 26 条远端分支删除命令成功；最终 27 个目标 ref 均不存在 |
| 外部已删除分支 | PASS | `codex/182-talent-claim-oom-guard` 在本轮写操作前已不存在；原 SHA `cd9cc600c50d40196ebd08cf81a3e183c07d2269` 已归档 |
| main | PASS | SHA 保持 `844636f5fae7b080843b26bd7539ada29a520e8d` |
| release/real-pre | PASS | SHA 保持 `4b426449fe520cf5f056b8f69fe322943c07e3d3` |
| 开放 PR | PASS | 15 个开放 PR 头分支均仍存在；未删除开放 PR 分支 |
| Dependabot | PASS | 10 条 Dependabot 分支未纳入本次删除范围 |
| 构建 / 测试 | 不适用 | 本次仅操作 Git 远端 ref，未修改应用代码 |
| Docker 重启 / 健康检查 | 不适用 | 本次未修改运行环境、镜像或服务 |
| 业务验证 | 不适用 | 本次不涉及业务行为变更 |
| Harness 文件门禁 | FAIL / PARTIAL | `TASK_GATE=FAIL`、`REPOSITORY_HEALTH=PARTIAL`；`harness/reports/current` 从基线 78 增至 83 个文件，`latest-sop-refactor-design.md` 为 470 行 |

## 处置范围

归档标签统一使用：`archive/branch-cleanup-20260720/<原分支名>`。

删除范围最终按当前远端事实确定为 18 条 DDD 分支和 9 条其他已被 main 完全包含的分支：`codex/180-real-pre-release-queue`、`codex/182-talent-claim-oom-guard`、`codex/harness-file-governance`、`codex/product-activity-status-counts`、`codex/product-sample-setting-drawer`、`codex/rbac-implementation-plan`、`codex/rbac-permission-enforcement`、`codex/repository-governance-mainline-20260719`、`feature/product-manage-fallback-fix-20260623`。最终共 27 个归档标签、26 条远端分支删除；其中 `codex/182...` 在本轮删除前已被外部删除。

未触碰：`main`、`release/real-pre`、开放 PR 分支、10 条 Dependabot 分支、所有与 main 分叉的人工分支、任何本地 worktree。

## 风险与下一步

- 当前工作区已有未提交的前端与 Harness 改动，本任务未覆盖、未重置、未删除。
- Harness 门禁失败原因涉及当前并行工作区的报告数量和既有超长报告；未在本任务中删除或压缩，避免破坏其他任务证据。
- 远端删除可通过对应归档标签恢复同一提交；如需恢复，应新建短分支，不直接重建长期 DDD 总分支。
- 下一步按统一 DDD 任务清单从当前 `main` 创建单业务切片短分支和独立 PR。

## Retro

- 发现：旧只读审计报告的 ahead/behind 语义与当前 `merge-base` 结果不一致。
- 改进动作：后续分支清理必须以实时 `git ls-remote`、`git merge-base --is-ancestor`、开放 PR 列表和归档标签 SHA 校验四项为删除前置条件。
- 验证方式：删除前逐条校验远端 SHA；删除后逐条确认目标 ref 不存在，并确认归档 tag peeled SHA 与原提交一致。

## Git

- 工作区：执行前已有未提交改动；本任务未修改这些文件。
- 远端部署：未执行，非本任务范围。
- Git Exit Gate：`PARTIAL_DIRTY_REMAINING`。
- 本任务文件：`harness/reports/current/latest-branch-cleanup-20260720.md`，未提交。
- 未执行 commit/push：当前工作区存在未登记的并行 dirty，按 Unknown Dirty Policy 不得混入本任务提交。
- 其他 dirty 文件：
  - `frontend/src/router/menuTree.test.ts`
  - `frontend/src/router/menuTree.ts`
  - `frontend/src/views/layout/Sider.vue`
  - `frontend/src/views/profile/UserProfile.test.ts`
  - `frontend/src/views/profile/UserProfile.vue`
  - `harness/reports/current/latest-harness-limits-check.md`
  - `harness/reports/current/latest-archive-tag-plan.md`
  - `harness/reports/current/latest-branch-inclusion-audit.md`
  - `harness/reports/current/latest-frontend-profile-navigation.md`
  - `harness/reports/current/latest-sop-refactor-design.md`
  - `harness/rules/governance/risk-routing.md`
  - `harness/rules/test-impact-map.json`
  - `harness/scripts/tests/test-impact-map.Tests.ps1`

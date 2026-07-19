# Git 分支与 Worktree 治理清单（2026-07-19）

## 基线

- GitHub 默认分支：`main`
- 当前 `main`：`f9af2ca92936cf6b0f89693e329ebb6680152453`
- 当前 `release/real-pre`：`db930364f577f965f93601297e5e9854b4ff1813`
- 服务器审计运行 SHA：`db930364f577f965f93601297e5e9854b4ff1813`
- 规则：允许并行开发，禁止并行合并、迁移、远端 E2E 和部署。

## 治理结论

| 分类 | 数量 | 当前动作 |
| --- | ---: | --- |
| 已进入 `main` 历史 | 23 | 标记为删除候选；确认无 owner / dirty worktree 后再单独清理 |
| 与 `main` 分叉的人工分支 | 22 | 禁止整支合并；按能力切片重新建 PR |
| Dependabot 分支 | 10 | 逐 PR 审查兼容性与 CI，不批量合并 |
| 脏 Worktree（不含本任务） | 15 | 保留，不移动、不删除、不重置 |

本轮不执行远端分支删除、Worktree 删除或历史重写。

## 已合并分支：清理候选

- `origin/codex/ddd-product-001`
- `origin/codex/harness-file-governance`
- `origin/codex/product-activity-status-counts`
- `origin/codex/product-sample-setting-drawer`
- `origin/codex/rbac-implementation-plan`
- `origin/codex/repository-governance-mainline-20260719`
- `origin/feature/ddd/DDD-CLEAN-002`
- `origin/feature/ddd/DDD-CLEAN-003`
- `origin/feature/ddd/DDD-CLEAN-004`
- `origin/feature/ddd/DDD-EVENT-003-dispatcher-dryrun`
- `origin/feature/ddd/DDD-FRONT-001`
- `origin/feature/ddd/DDD-ORDER-005`
- `origin/feature/ddd/DDD-ORDER-006`
- `origin/feature/ddd/DDD-PERF-005`
- `origin/feature/ddd/DDD-PRODUCT-004-copy-promotion-port`
- `origin/feature/ddd/DDD-SAMPLE-001`
- `origin/feature/ddd/DDD-SAMPLE-002-eligibility-policy`
- `origin/feature/ddd/DDD-SAMPLE-005-FIX-sample-agent`
- `origin/feature/ddd/DDD-SLIM-ORDER-002-attribution`
- `origin/feature/ddd/DDD-SLIM-PERF-001`
- `origin/feature/ddd/DDD-SLIM-PRODUCT-001`
- `origin/feature/ddd/DDD-SLIM-SAMPLE-001`
- `origin/feature/product-manage-fallback-fix-20260623`

## 分叉人工分支：能力切片候选

- `origin/codex/166-git-governance-phase1`
- `origin/codex/cooperation-workbench-actions`
- `origin/codex/ddd-legacy-entrypoint-migration`
- `origin/codex/ddd-performance-event-single-consumer`
- `origin/codex/ddd-sample-query-completion`
- `origin/codex/ddd-user-role-application`
- `origin/codex/deploy-product-library-density`
- `origin/codex/deploy-product-library-density-v2`
- `origin/codex/fix-product-copy-link-admin`
- `origin/codex/fix-remote-runtime-issues`
- `origin/codex/harness-node-verify-phase1`
- `origin/codex/integrate-ddd-completed-20260717`
- `origin/codex/integration-safe-merge-20260618`
- `origin/codex/product-library-card-ui-latest`
- `origin/codex/rbac-shadow-runtime-plan`
- `origin/codex/realtime-product-order-list-refresh`
- `origin/codex/role-aware-link-attribution`
- `origin/feature/auth-system`
- `origin/feature/ddd/DDD-VERIFY-001`
- `origin/feature/ddd/SPRINT-1-P0`
- `origin/fix/admin-password-reset`
- `origin/fix/compose-env-unify`

其中 `feature/auth-system` 与 `main` 的差异为主线独有 136、分支独有 178 个提交；禁止整支 merge。迁移时必须按功能建立 Issue，重新确认当前代码、迁移和验收契约后，以最小 PR 移植。

## Dependabot 分支

- `origin/dependabot/github_actions/actions/checkout-7.0.0`
- `origin/dependabot/github_actions/actions/setup-java-5.6.0`
- `origin/dependabot/github_actions/actions/setup-node-7.0.0`
- `origin/dependabot/github_actions/pnpm/action-setup-6.0.9`
- `origin/dependabot/maven/backend/org.springframework.boot-spring-boot-starter-parent-4.1.0`
- `origin/dependabot/maven/backend/routine-maven-updates-07c848a636`
- `origin/dependabot/npm_and_yarn/frontend/routine-frontend-updates-82e37e7844`
- `origin/dependabot/npm_and_yarn/frontend/types/node-26.1.1`
- `origin/dependabot/npm_and_yarn/frontend/typescript-7.0.2`
- `origin/dependabot/npm_and_yarn/routine-root-updates-155ad51d09`

## 脏 Worktree：强制保留

| Worktree / 分支 | Dirty 数 |
| --- | ---: |
| `stability-closeout-20260718` / `codex/stability-closeout-20260718` | 47 |
| `cooperation-operation-bar-local-verify` / `codex/local-cooperation-operation-bar-verify` | 1 |
| `ddd-performance-event-single-consumer` / 同名分支 | 10 |
| `ddd-verify-product-service` / `feature/ddd/DDD-VERIFY-001` | 1 |
| `deploy-product-library-density` / 同名分支 | 1 |
| `fix-activity-product-sync-20260712` / `codex/fix-activity-product-sync-reliability` | 1 |
| `full-business-permission-deploy` / 同名分支 | 29 |
| `integrate-ddd-completed-20260717` / `codex/ddd-main-final-20260717` | 27 |
| `logistics-shipper-code-fix-integration` / detached | 5 |
| `product-sync-deploy-20260713` / `feature/auth-system` | 3 |
| `sprint-1-p0` / `feature/ddd/SPRINT-1-P0` | 1 |
| `y4-optimistic-lock` / 同名分支 | 13 |
| `SAAS-dual-commission-deploy` / `codex/deploy-dual-commission-real-pre` | 2 |
| `SAAS-product-132-verify` / detached | 4 |
| `git-batch-19-20260621-192615` / detached | 6 |

## 后续执行门禁

1. 清理候选先确认没有 dirty worktree、未关闭 PR、未交接 owner 和独有提交。
2. 分叉分支先产出 `git diff` 能力清单，不按提交数量判断可合并性。
3. 每个能力切片使用新 `codex/<issue>-<slug>` 分支、独立 worktree 和独立 PR。
4. 合并控制器一次只处理一个 PR；主线变化后，其余 PR 必须更新并重新通过 CI。
5. 发布只能从 `release/real-pre` 进入 Jenkins 全局队列。

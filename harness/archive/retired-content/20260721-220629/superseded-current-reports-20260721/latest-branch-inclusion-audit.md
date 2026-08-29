# Branch Inclusion Audit (READ-ONLY — NO GIT MUTATION)

> Generated 2026-07-20T18:49:32 by Hermes.
> Pure ahead/behind comparison against origin/main.
> Excludes `main`, `release/real-pre`, and all `gitee` mirror refs.

## Totals

| Metric | Count |
|---|---|
| Origin branches audited | **64** |
| **Fully included in main** (ahead=0, no unique commits) | **3** |
| Diverged from main (ahead > 0) | **61** |
| With active worktree | **28** |

## ⚠️ Filters NOT applied (your manual review still required)

This is a mechanical ahead/behind check. It does NOT consider:

1. PR state — branch may have a merged PR with a different SHA than origin/main
2. Whether the branch's commits were "intentionally cherry-picked into main"
3. Whether the branch's commits were "force-pushed away" (ghosts)
4. Whether someone is actively using the branch via direct commit (not via PR)
5. Branch protection / CODEOWNERS / required reviewers

Use this as a **starting list** for your 26-branch audit; cross-check with `gh pr view` per branch.

## Fully included in main (ahead=0): 3 branches

Sorted by branch name. Each row shows: branch / SHA / behind count / PR state / active worktree.

| # | Branch | SHA | Behind main | PR state | Active worktree |
|---|---|---|---|---|---|
| 1 | `codex/sop-refactor-pr5-stage-timeout` | `9ec849da23f9` | 1 | #196 OPEN | `D:/Projects/SAAS/.worktrees/sop-refactor-pr5-stage-timeout` |
| 2 | `codex/sop-refactor-pr6-gha-sha-gate` | `6e1ad1cd6d5c` | 1 | #198 OPEN | `D:/Projects/SAAS/.worktrees/sop-refactor-pr6-gha-sha-gate` |
| 3 | `codex/sop-refactor-pr7-remove-frontend-build` | `fb31b77103e1` | 1 | #197 OPEN | `D:/Projects/SAAS/.worktrees/sop-refactor-pr7-remove-frontend-build` |


## Diverged from main (ahead > 0): 61 branches

These branches have unique commits not in main. **Do NOT auto-delete.**

| Branch | SHA | Ahead | Behind | Active worktree |
|---|---|---|---|---|
| `fix/admin-password-reset` | `6e42608f41a4` | 1074 | 1 |  |
| `fix/compose-env-unify` | `dc02fa17e5e9` | 1073 | 1 |  |
| `codex/ddd-product-001` | `9512cda63a54` | 928 | 0 | `D:/Projects/SAAS/.worktrees/ddd-product-001` |
| `feature/ddd/DDD-SAMPLE-005-FIX-sample-agent` | `ce79cba040cd` | 878 | 0 |  |
| `feature/ddd/SPRINT-1-P0` | `27d57fd1ead0` | 878 | 13 | `D:/Projects/SAAS/.worktrees/sprint-1-p0` |
| `feature/ddd/DDD-SLIM-ORDER-002-attribution` | `6c577ae87d4e` | 877 | 0 |  |
| `feature/ddd/DDD-EVENT-003-dispatcher-dryrun` | `1d49a885fc18` | 874 | 0 |  |
| `feature/ddd/DDD-SAMPLE-002-eligibility-policy` | `ea7763bb203b` | 872 | 0 |  |
| `feature/ddd/DDD-PRODUCT-004-copy-promotion-port` | `1a9296f388cd` | 870 | 0 | `D:/Projects/SAAS/.worktrees/ddd-product-004-copy-promotion-port` |
| `feature/ddd/DDD-PERF-005` | `b55259e6e4fc` | 854 | 0 |  |
| `feature/ddd/DDD-ORDER-005` | `8b49dd12da2b` | 846 | 0 |  |
| `feature/ddd/DDD-ORDER-006` | `db23248aa120` | 843 | 0 |  |
| `feature/ddd/DDD-SLIM-PERF-001` | `7bb33923ae0b` | 839 | 0 |  |
| `feature/ddd/DDD-SAMPLE-001` | `aa415bd84c28` | 836 | 0 |  |
| `feature/ddd/DDD-SLIM-PRODUCT-001` | `d1f956252df5` | 833 | 0 |  |
| `feature/ddd/DDD-SLIM-SAMPLE-001` | `63ff05bf59b3` | 829 | 0 |  |
| `feature/ddd/DDD-FRONT-001` | `9d3bc6b78aab` | 825 | 0 |  |
| `feature/ddd/DDD-CLEAN-002` | `cab0ac6d5286` | 820 | 0 |  |
| `feature/ddd/DDD-CLEAN-003` | `34e2f1052d10` | 819 | 0 |  |
| `feature/ddd/DDD-CLEAN-004` | `167215473bed` | 816 | 0 |  |
| `codex/integration-safe-merge-20260618` | `57b41c283137` | 751 | 12 | `D:/Projects/SAAS/.worktrees/integration-safe-merge-20260618` |
| `feature/ddd/DDD-VERIFY-001` | `26cf87649089` | 567 | 192 | `D:/Projects/SAAS/.worktrees/ddd-verify-product-service` |
| `codex/realtime-product-order-list-refresh` | `59961f88c2c4` | 560 | 14 | `D:/Projects/SAAS/.worktrees/product-activity-status-counts` |
| `feature/product-manage-fallback-fix-20260623` | `b84f42327a31` | 499 | 0 |  |
| `codex/product-activity-status-counts` | `8df2b62c01c2` | 488 | 0 |  |
| `codex/ddd-legacy-entrypoint-migration` | `a7c0afe23946` | 327 | 10 | `D:/Projects/SAAS/.worktrees/ddd-legacy-entrypoint-migration` |
| `codex/ddd-sample-query-completion` | `32a5f1624d77` | 327 | 5 | `D:/Projects/SAAS/.worktrees/ddd-sample-query-completion` |
| `codex/harness-file-governance` | `c648e233e47f` | 259 | 0 |  |
| `codex/rbac-implementation-plan` | `c33466b81a4a` | 253 | 0 |  |
| `codex/rbac-shadow-runtime-plan` | `88659fb00793` | 228 | 26 | `D:/Projects/SAAS/.worktrees/rbac-shadow-runtime-plan` |
| `codex/product-sample-setting-drawer` | `67f94935e58d` | 213 | 0 |  |
| `codex/deploy-product-library-density` | `7856f3194944` | 178 | 56 | `D:/Projects/SAAS/.worktrees/deploy-product-library-density` |
| `codex/deploy-product-library-density-v2` | `50d3c4631023` | 178 | 86 | `D:/Projects/SAAS/.worktrees/deploy-product-library-density-v2` |
| `codex/fix-remote-runtime-issues` | `b449ec52927c` | 178 | 111 | `D:/Projects/SAAS/.worktrees/remote-runtime-fix` |
| `codex/integrate-ddd-completed-20260717` | `0e3dee33b114` | 178 | 66 |  |
| `codex/166-git-governance-phase1` | `2bbc186bd29c` | 172 | 183 | `D:/Projects/SAAS/.worktrees/git-governance-phase1` |
| `codex/cooperation-workbench-actions` | `9dddfc34a1f6` | 172 | 30 |  |
| `codex/fix-product-copy-link-admin` | `efc7a161437f` | 172 | 177 | `D:/Projects/SAAS/.worktrees/fix-product-copy-link-admin` |
| `codex/product-library-card-ui-latest` | `4e2c56a4d0dd` | 172 | 178 | `D:/Projects/SAAS/.worktrees/deploy-product-library-card-ui-latest` |
| `feature/auth-system` | `4e2c56a4d0dd` | 172 | 178 | `D:/Projects/SAAS/.worktrees/product-sync-deploy-20260713` |
| `codex/role-aware-link-attribution` | `018c788ed98d` | 168 | 17 | `D:/Projects/SAAS/.worktrees/role-aware-link-attribution` |
| `codex/ddd-performance-event-single-consumer` | `4752956cb6e6` | 159 | 3 | `D:/Projects/SAAS/.worktrees/ddd-performance-event-single-consumer` |
| `codex/harness-node-verify-phase1` | `643e04cb3906` | 123 | 16 | `D:/Projects/SAAS/.worktrees/harness-node-verify-phase1` |
| `codex/ddd-user-role-application` | `c14d36582643` | 44 | 0 |  |
| `codex/repository-governance-mainline-20260719` | `05d639f851c1` | 37 | 0 | `D:/Projects/SAAS/.worktrees/repository-governance-mainline-20260719` |
| `dependabot/maven/backend/org.springframework.boot-spring-boot-starter-parent-4.1.0` | `e63d3d45ff24` | 36 | 1 |  |
| `dependabot/npm_and_yarn/frontend/types/node-26.1.1` | `1208b72831cf` | 36 | 1 |  |
| `dependabot/npm_and_yarn/frontend/typescript-7.0.2` | `5c233124545a` | 36 | 1 |  |
| `codex/180-real-pre-release-queue` | `2f59cae84a69` | 33 | 0 | `D:/Projects/SAAS/.worktrees/real-pre-release-queue-20260719` |
| `dependabot/github_actions/actions/checkout-7.0.0` | `c08a1e66bcb2` | 32 | 1 |  |
| `dependabot/github_actions/actions/setup-java-5.6.0` | `0c30f892230b` | 32 | 1 |  |
| `dependabot/github_actions/actions/setup-node-7.0.0` | `e06768f98884` | 32 | 1 |  |
| `dependabot/github_actions/pnpm/action-setup-6.0.9` | `e377a7efe335` | 32 | 1 |  |
| `dependabot/maven/backend/routine-maven-updates-07c848a636` | `afd447c70817` | 32 | 1 |  |
| `dependabot/npm_and_yarn/frontend/routine-frontend-updates-82e37e7844` | `bc1b677ed921` | 32 | 1 |  |
| `dependabot/npm_and_yarn/routine-root-updates-155ad51d09` | `70255ef20f42` | 32 | 1 |  |
| `codex/183-talent-claim-oom-guard-release` | `2bd6494afefc` | 25 | 2 | `D:/Projects/SAAS` |
| `codex/rbac-permission-enforcement` | `3374e9f7c6ed` | 13 | 0 | `D:/Projects/SAAS-rbac-codex` |
| `codex/sop-refactor-pr1-config-layer` | `437a81bfc6b4` | 10 | 1 | `D:/Projects/SAAS/.worktrees/sop-refactor-pr1-config-layer` |
| `codex/182-talent-claim-oom-guard` | `cd9cc600c50d` | 6 | 0 |  |
| `codex/183-jenkins-fetch-recovery` | `6791e3e75d38` | 2 | 1 | `D:/Projects/SAAS-jenkins-fetch-recovery` |


## Notes for your 26-branch audit

Your stated target: 26 branches total = 18 DDD + 8 other.

This report found **3** branches ahead=0 from origin/main (the mechanical "fully contained" set).

If the numbers don't match, the gap is one of:

1. **You counted locally-deleted branches** that origin still has (or vice versa)
2. **You included gitee-mirror-only refs** (excluded here)
3. **You included `sop-refactor-pr*`** (excluded here per your instruction)
4. **Your 8 "other" branches** are a specific list I don't have — please share

## Recommended next action (NOT executed)

For each row in "Fully included in main":

1. Verify the PR state in the PR state column is `MERGED` (or `no PR` for direct merges).
2. Decide: archive tag + delete, or leave alone.
3. If archiving:
   ```bash
   git tag archive/<branch-name> origin/<branch-name>
   git push origin archive/<branch-name>
   git push origin --delete <branch-name>
   ```

This report is **read-only**. No tag, no delete, no push has been performed.

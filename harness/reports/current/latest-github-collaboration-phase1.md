# Evidence Report

## Metadata

- Time: 2026-07-19 15:11:46 +08:00
- Environment: real-pre
- Scope: docs
- Branch: codex/166-git-governance-phase1
- Implementation commits: 70ac1c65, 27239a79, 52976a74, ddd9d4fc
- Evidence refresh base: ddd9d4fc
- Pull request: https://github.com/laoliu-463/saas/pull/167 (Draft)
- Owned worktree: clean after implementation commit; this report and issue mirror are the final state-sync change
- Deploy remote: false

## Owned Files

~~~text
.github/workflows/ci.yml
harness/engineering/issues-index.md
harness/rules/changelog.md
harness/rules/skills/git/git-change-control.md
harness/scripts/commands/_lib.ps1
harness/scripts/commands/agent-do.ps1
harness/scripts/commands/git-push-safe.ps1
harness/scripts/commands/safety-check.ps1
harness/scripts/modules/HarnessFileGovernance.psm1
harness/scripts/tests/agent-do-conclusion.Tests.ps1
harness/scripts/tests/check-harness-limits.Tests.ps1
harness/scripts/tests/github-collaboration.Tests.ps1
harness/scripts/tests/git-push-safe.Tests.ps1
harness/scripts/tests/safety-check-docs.Tests.ps1
.github/CODEOWNERS
.github/dependabot.yml
.github/ISSUE_TEMPLATE/bug.yml
.github/ISSUE_TEMPLATE/config.yml
.github/ISSUE_TEMPLATE/feature.yml
.github/pull_request_template.md
CONTRIBUTING.md
SECURITY.md
harness/reports/current/latest-github-collaboration-phase1.md
~~~

## Owned Git Status

~~~text
(clean after implementation commit ddd9d4fc)
~~~

## Build Result

~~~text
Gate requirement: Scope=docs, application build/restart not required.
Supplementary local frontend: pnpm test PASS; pnpm typecheck PASS; pnpm build PASS.
Supplementary local backend: Java compile PASS; mvn -B test executed 3598 tests and failed on the pre-existing baseline (24 failures, 42 errors, 3 skipped).
GitHub run 29677526171: Frontend PASS; Repository governance PASS; Backend executed 3598 tests and failed with 25 failures, 42 errors, 3 skipped.
~~~

## Docker Status

~~~text
not collected
not collected
~~~

## Health Check Result

~~~text
Scope=docs: compose restart and HTTP health checks skipped by scoped local harness path.
~~~

## Business Validation Result

~~~text
Scope=docs: business validation not applicable; safety check executed.
~~~

## Governance Verification

| Check | Result | Evidence |
| --- | --- | --- |
| GitHub/Harness Pester contracts | PASS | 29 passed, 0 failed |
| GitHub YAML parse | PASS | 5 YAML files parsed |
| GitHub Actions actionlint | PASS | actionlint 1.7.12 |
| Harness incremental limits | PASS | `TASK_GATE=PASS`; existing report debt keeps `REPOSITORY_HEALTH=PARTIAL` |
| GitHub Repository governance | PASS | run 29677526171, 42s |
| GitHub Frontend tests and build | PASS | run 29677526171, 1m32s |
| GitHub Backend tests | FAIL | run 29677526171, 3598 tests / 25 failures / 42 errors / 3 skipped |
| Git whitespace check | PASS | `git diff --check` returned no findings |
| Remote deployment | SKIP | not requested and not executed |
| Database migration | SKIP | no repository migration or remote database operation executed |

## Content Maintenance Result

~~~text
Content maintenance skipped by -ContentMaintenance off.
~~~

## Remote Deploy Result

~~~text
remote not deployed
~~~

## Retro Summary

首轮 PR CI 暴露 Linux 路径分隔符和测试 Compose 迁移耦合；已改为跨平台路径处理与无仓库迁移的原生 service containers。第二轮治理与前端已 PASS；后端基线问题转入 GitHub #168，平台规则仍待该基线修复后启用。

## Conclusion

PARTIAL

## Residual Risk

- 后端默认分支基线仍红；主要证据为测试数据库缺表/缺列和 DDD architecture contract failures，已由 GitHub #168 跟踪。
- GitHub ruleset、required checks、Merge Queue、默认分支迁移、可见性变化、分支/worktree 清理、合并和部署仍在 Phase 1 非范围内。
- `harness/reports` 与 `harness/reports/current` 保留既存文件数债务，本任务未新增门禁违规。

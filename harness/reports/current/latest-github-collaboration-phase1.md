# Evidence Report

## Metadata

- Time: 2026-07-19 14:57:22 +08:00
- Environment: real-pre
- Scope: docs
- Branch: codex/166-git-governance-phase1
- Implementation commits: 70ac1c65, 27239a79
- Evidence refresh base: 27239a79
- Owned worktree: clean after implementation commit; this report is the final evidence-only refresh
- Deploy remote: false

## Owned Files

~~~text
.github/CODEOWNERS
.github/dependabot.yml
.github/ISSUE_TEMPLATE/bug.yml
.github/ISSUE_TEMPLATE/config.yml
.github/ISSUE_TEMPLATE/feature.yml
.github/pull_request_template.md
.github/workflows/ci.yml
CONTRIBUTING.md
harness/engineering/issues-index.md
harness/rules/changelog.md
harness/rules/skills/git/git-change-control.md
harness/scripts/commands/_lib.ps1
harness/scripts/commands/agent-do.ps1
harness/scripts/commands/git-push-safe.ps1
harness/scripts/commands/safety-check.ps1
harness/scripts/tests/agent-do-conclusion.Tests.ps1
harness/scripts/tests/github-collaboration.Tests.ps1
harness/scripts/tests/git-push-safe.Tests.ps1
harness/scripts/tests/safety-check-docs.Tests.ps1
SECURITY.md
~~~

## Owned Git Status

~~~text
(clean after implementation commit 27239a79)
~~~

## Build Result

~~~text
Gate requirement: Scope=docs, application build/restart not required.
Supplementary frontend verification: pnpm test PASS; pnpm typecheck PASS; pnpm build PASS.
Supplementary backend verification: Java compile PASS; mvn -B test FAIL on the pre-existing default-branch baseline (3598 tests, 24 failures, 42 errors, 3 skipped).
The backend failures are outside this PR's .github/Harness governance change set and match the already failing default-branch Backend tests job.
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
| GitHub/Harness Pester contracts | PASS | 16 passed, 0 failed |
| GitHub YAML parse | PASS | 5 YAML files parsed |
| GitHub Actions actionlint | PASS | actionlint 1.7.12 |
| Harness incremental limits | PASS | `TASK_GATE=PASS`; existing report debt keeps `REPOSITORY_HEALTH=PARTIAL` |
| Scoped push dry-run | PASS | all 11 then-pending Owned files enumerated, including `.github/ISSUE_TEMPLATE/*` |
| Git whitespace check | PASS | `git diff --check` returned no findings |
| Remote deployment | SKIP | not requested and not executed |
| Database migration | SKIP | no database files changed; not required for docs/governance scope |

## Content Maintenance Result

~~~text
Content maintenance skipped by -ContentMaintenance off.
~~~

## Remote Deploy Result

~~~text
remote not deployed
~~~

## Retro Summary

平台保护尚未启用且默认分支后端测试基线仍失败；Phase 2 应在基线修复后启用 required checks、ruleset 与 merge queue。本轮修复了 docs 无 env 收口和 dot-prefixed Owned files 漏提交问题，无需独立 retro。

## Conclusion

PARTIAL

## Residual Risk

- The default branch backend baseline remains red; required checks must not be enabled as merge blockers until that debt is fixed or explicitly split into an approved baseline policy.
- GitHub ruleset, Merge Queue, default-branch migration, visibility changes, branch cleanup, deployment, and database changes are intentionally outside Phase 1.
- `harness/reports` and `harness/reports/current` retain pre-existing file-count debt; this task did not add a new violation.

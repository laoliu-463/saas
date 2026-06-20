# Evidence Report - Git Intake

## Metadata

- Time: 2026-06-21 00:20:00 +08:00
- Environment: real-pre
- Scope: git-intake / docs
- Branch: feature/ddd/DDD-VERIFY-001
- Head: bfb17058 docs: index agent config memory sources
- Staged: empty
- Deploy remote: false

## Intake Commands

~~~text
git branch --show-current
git log -1 --oneline
git diff --cached --name-only
git status --porcelain=v1 -uall
~~~

## Dirty Summary

| Dimension | Count |
| --- | ---: |
| total dirty entries | 231 |
| modified | 60 |
| deleted | 33 |
| untracked | 138 |
| staged | 0 |

## Path Classification

| Category | Count | Assessment |
| --- | ---: | --- |
| backend | 110 | DDD implementation work, not safe to mix with docs/harness commits. |
| frontend | 3 | Auth/request/login changes, separate frontend batch. |
| docs | 12 | Docs and ADR/migration planning changes. |
| docs_state | 25 | Harness rules/state/engineering docs. |
| harness_script | 2 | Harness script fixes, including `git-push-safe.ps1`. |
| report_only | 77 | Evidence/retro/report moves and new reports. |
| cleanup_retire | 2 | Manifest/archive cleanup records. |
| path-unknown | 0 | No path fell outside known top-level categories. |

## Candidate Batches

| Batch | Scope | Candidate Files | Required Verification |
| --- | --- | --- | --- |
| GIT-BATCH-0 | harness-script + report | `harness/scripts/commands/git-push-safe.ps1`, `harness/rules/changelog.md`, `harness/reports/2026-06-21/evidence-20260621-001242.md`, `harness/reports/2026-06-21/retro-20260621-001242.md` | `git-push-safe -DryRun`, `agent-do -Scope docs -DryRun`, `check-harness-limits`, `git diff --check`. |
| GIT-BATCH-1 | docs/harness path alignment | `AGENTS.md`, `docs/README.md`, `docs/方案/PLAN-004-DDD边界与Harness工程收口.md`, `harness/README.md`, `harness/rules/governance/*`, `harness/rules/runbooks/ddd/*`, `harness/rules/state/snapshots/01-当前项目状态.md` | `safety-check -Scope docs -DryRun`, `check-harness-limits`, old-path scan. |
| GIT-BATCH-2 | report archive cleanup | root report deletions plus `harness/reports/2026-06-*` additions and archive manifests | `check-harness-limits`, manifest review, no business files. |
| GIT-BATCH-3 | frontend auth/request | `frontend/src/utils/request.ts`, `frontend/src/utils/request.test.ts`, `frontend/src/views/Login.vue` | frontend tests/build and browser/API smoke before commit. |
| GIT-BATCH-4 | backend user-domain DDD | user domain application/policy/port/infrastructure files and tests | backend targeted tests first, then Maven package and real-pre health after commit path is clean. |
| GIT-BATCH-5 | backend order/colonel DDD | order facade/query, colonel domain/router/repository adapter and tests | targeted backend tests, dependency/boundary review, then broader backend verification. |

## Completed Batch

| Batch | Commit | Push |
| --- | --- | --- |
| GIT-BATCH-0 | `1ba8dfcc docs(harness): GIT-BATCH-0 unblock secret scan` | pushed to `gitee` and `origin` on branch `feature/ddd/DDD-VERIFY-001` |

## Additional Harness Cleanup In This Turn

- `harness/rules/skills/git/git-batch-submit.md`: report closeout paths now point to current state snapshots and changelog.
- `harness/rules/runbooks/governance/closeout-and-gc.md`: closeout prerequisites and `retire-content.ps1` command now point to current `harness/rules/...` and `harness/scripts/commands/...` paths.

## Decision

DELAY new business DDD edits.

Reason: the worktree is path-classified but not ownership-verified. Starting new business changes before batching would mix current-task, previous-partial, report cleanup, frontend, and backend DDD changes.

## Next Action

Continue with GIT-BATCH-1 or GIT-BATCH-2 after explicit staged-file review. Do not start backend/frontend DDD edits until docs/report cleanup batches are isolated.

## Residual Risk

- No commit or push was performed in this intake.
- Backend and frontend batches still need code-level verification before they can be treated as complete.
- Report archive cleanup must be handled separately from business code.

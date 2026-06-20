# Evidence Report - GIT-BATCH-1

## Metadata

- Time: 2026-06-21 00:25:00 +08:00
- Environment: real-pre
- Scope: docs / harness
- Branch: feature/ddd/DDD-VERIFY-001
- Base commit: 1ba8dfcc
- Deploy remote: false

## Current Task

GIT-BATCH-1: commit docs/harness path alignment so later DDD backend/frontend batches can run from one current Harness entrypoint.

## Candidate Files

~~~text
AGENTS.md
docs/README.md
docs/方案/PLAN-004-DDD边界与Harness工程收口.md
harness/README.md
harness/rules/governance/COMPLETION_GATES.md
harness/rules/governance/completion-gates-detail.md
harness/rules/governance/completion-gates-git.md
harness/rules/governance/domains-map.md
harness/rules/governance/forbidden-scope.md
harness/rules/governance/quality-ledger.md
harness/rules/governance/session-exit-gate.md
harness/rules/governance/task-routing.md
harness/rules/instructions/governance/multi-agent-ddd-prompts.md
harness/rules/policies/structure-policy.md
harness/rules/runbooks/ddd/DDD_DOMAIN_TASK_MATRIX.md
harness/rules/runbooks/ddd/DDD_OPTIMIZATION_ROADMAP.md
harness/rules/runbooks/ddd/HARNESS_ITERATION_ROADMAP.md
harness/rules/state/snapshots/01-当前项目状态.md
harness/rules/skills/git/git-batch-submit.md
harness/rules/runbooks/governance/closeout-and-gc.md
harness/reports/2026-06-21/evidence-20260621-002000-git-intake.md
harness/reports/2026-06-21/retro-20260621-002000-git-intake.md
harness/reports/2026-06-21/evidence-20260621-002500-git-batch-1.md
harness/reports/2026-06-21/retro-20260621-002500-git-batch-1.md
~~~

## Verification Before Staging

| Check | Result | Evidence |
| --- | --- | --- |
| code-review-graph stats | PASS | Graph reachable, latest timestamp 2026-06-21T00:24:09. |
| old path scan | PASS_WITH_NOTE | No live old execution path hit; one remaining `structure-policy.md` hit is an explicit forbidden old-name example. |
| candidate diff check | PASS | `git diff --check` on path-alignment candidates returned 0. |
| scope isolation | PASS | Candidate files are docs/harness/report files only; backend/frontend files excluded. |

## Build / Runtime

~~~text
Scope=docs/harness: build, container restart, HTTP health and business E2E are intentionally skipped.
~~~

## Conclusion

PARTIAL

## Residual Risk

- This batch does not validate backend/frontend DDD behavior.
- The wider worktree still has unrelated backend, frontend, report archive and docs changes.
- Commit/push status will be recorded by the Git Gate after staging.

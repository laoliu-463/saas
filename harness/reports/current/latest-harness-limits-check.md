# Harness File Governance Check

## Status

- TASK_GATE=PASS
- REPOSITORY_HEALTH=PARTIAL

## Active Budgets

- Active directories: warning 40, hard limit 50.
- Reports root: target hard limit 20; pre-existing debt uses no-regression.
- Non-script text: warning 160 lines, hard limit 200.

## Task Violations
- None

## Warnings
- None

## Historical Debt
- [DIRECT_FILE_COUNT_EXCEEDED] harness/reports: Repository file count 39 exceeds 20.
- [DIRECT_FILE_COUNT_EXCEEDED] harness/reports/current: Repository file count 65 exceeds 50.
- [TEXT_LINE_COUNT_EXCEEDED] harness/reports/evidence-20260713-221229.md: Repository text line count 258 exceeds 200.

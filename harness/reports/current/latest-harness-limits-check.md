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
- [DIRECT_FILE_COUNT_WARNING] harness/reports/current: File count reached warning level 50.

## Historical Debt
- [DIRECT_FILE_COUNT_EXCEEDED] harness/reports: Repository file count 23 exceeds 20.

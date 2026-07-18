# Harness File Governance Check

## Status

- TASK_GATE=FAIL
- REPOSITORY_HEALTH=PARTIAL

## Active Budgets

- Active directories: warning 40, hard limit 50.
- Reports root: target hard limit 20; pre-existing debt uses no-regression.
- Non-script text: warning 160 lines, hard limit 200.

## Task Violations
- [DIRECT_FILE_COUNT_WORSENED] harness/reports: File count increased from 23 to 24.
- [TIMESTAMP_REPORT_IN_ROOT] harness/reports/evidence-20260713-131800.md: New timestamp reports are forbidden in reports root.

## Warnings
- None

## Historical Debt
- [DIRECT_FILE_COUNT_EXCEEDED] harness/reports: Repository file count 24 exceeds 20.
- [DIRECT_FILE_COUNT_EXCEEDED] harness/reports/current: Repository file count 68 exceeds 50.

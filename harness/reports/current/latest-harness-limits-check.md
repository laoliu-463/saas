# Harness File Governance Check

## Status

- TASK_GATE=FAIL
- REPOSITORY_HEALTH=PARTIAL

## Active Budgets

- Active directories: warning 40, hard limit 50.
- Reports root: target hard limit 20; pre-existing debt uses no-regression.
- Non-script text: warning 160 lines, hard limit 200.

## Task Violations
- [TEXT_LINE_COUNT_EXCEEDED] frontend/package-lock.json: Text line count 4897 exceeds 200.

## Warnings
- [TEXT_LINE_COUNT_WARNING] docker-compose.real-pre.yml: Text line count reached warning level 198.

## Historical Debt
- [DIRECT_FILE_COUNT_EXCEEDED] harness/reports: Repository file count 23 exceeds 20.

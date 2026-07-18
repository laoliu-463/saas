# Harness File Governance Check

## Status

- TASK_GATE=FAIL
- REPOSITORY_HEALTH=PARTIAL

## Active Budgets

- Active directories: warning 40, hard limit 50.
- Reports root: target hard limit 20; pre-existing debt uses no-regression.
- Non-script text: warning 160 lines, hard limit 200.

## Task Violations
- [DIRECT_FILE_COUNT_WORSENED] harness/reports: File count increased from 23 to 39.
- [DIRECT_FILE_COUNT_WORSENED] harness/reports/current: File count increased from 54 to 63.
- [TEXT_LINE_COUNT_EXCEEDED] harness/reports/evidence-20260713-221229.md: Text line count 258 exceeds 200.
- [TIMESTAMP_REPORT_IN_ROOT] harness/reports/evidence-20260713-220358.md: New timestamp reports are forbidden in reports root.
- [TIMESTAMP_REPORT_IN_ROOT] harness/reports/evidence-20260713-220639.md: New timestamp reports are forbidden in reports root.
- [TIMESTAMP_REPORT_IN_ROOT] harness/reports/evidence-20260713-221229.md: New timestamp reports are forbidden in reports root.
- [TIMESTAMP_REPORT_IN_ROOT] harness/reports/evidence-20260713-223114.md: New timestamp reports are forbidden in reports root.
- [TIMESTAMP_REPORT_IN_ROOT] harness/reports/evidence-20260713-223527.md: New timestamp reports are forbidden in reports root.
- [TIMESTAMP_REPORT_IN_ROOT] harness/reports/evidence-20260713-224145.md: New timestamp reports are forbidden in reports root.
- [TIMESTAMP_REPORT_IN_ROOT] harness/reports/evidence-20260713-230742.md: New timestamp reports are forbidden in reports root.
- [TIMESTAMP_REPORT_IN_ROOT] harness/reports/evidence-20260713-231039.md: New timestamp reports are forbidden in reports root.
- [TIMESTAMP_REPORT_IN_ROOT] harness/reports/evidence-20260713-231826.md: New timestamp reports are forbidden in reports root.
- [TIMESTAMP_REPORT_IN_ROOT] harness/reports/evidence-20260713-232012.md: New timestamp reports are forbidden in reports root.
- [TIMESTAMP_REPORT_IN_ROOT] harness/reports/evidence-20260713-232312.md: New timestamp reports are forbidden in reports root.
- [TIMESTAMP_REPORT_IN_ROOT] harness/reports/retro-20260713-231045.md: New timestamp reports are forbidden in reports root.
- [TIMESTAMP_REPORT_IN_ROOT] harness/reports/retro-20260713-231829.md: New timestamp reports are forbidden in reports root.
- [TIMESTAMP_REPORT_IN_ROOT] harness/reports/retro-20260713-232013.md: New timestamp reports are forbidden in reports root.
- [TIMESTAMP_REPORT_IN_ROOT] harness/reports/retro-20260713-232314.md: New timestamp reports are forbidden in reports root.

## Warnings
- [TEXT_LINE_COUNT_WARNING] harness/reports/evidence-20260713-220639.md: Text line count reached warning level 174.

## Historical Debt
- [DIRECT_FILE_COUNT_EXCEEDED] harness/reports: Repository file count 39 exceeds 20.
- [DIRECT_FILE_COUNT_EXCEEDED] harness/reports/current: Repository file count 63 exceeds 50.
- [TEXT_LINE_COUNT_EXCEEDED] harness/reports/evidence-20260713-221229.md: Repository text line count 258 exceeds 200.

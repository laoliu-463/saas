# Retro: U-7 DataApplicationService User Facade Boundary

## What changed

- Added a dedicated user-domain scalar outlet for **用户展示名称**.
- Moved data order-detail channel/recruiter name enrichment away from full `UserOptionResponse` DTOs.
- Kept the previous display behavior: real name first, username fallback.

## Evidence

- RED boundary test observed before production edit.
- Focused tests: 54 PASS.
- Expanded user-facade regression: 136 PASS.
- Package: PASS.
- Local real-pre backend restart and health: PASS.
- code-review-graph incremental update: PASS.

## Lesson

- **用户显示标签** and **用户展示名称** must stay separate: option labels can include account hints, while business record names should preserve existing concise display.
- A scalar facade outlet prevented the analysis module from learning user DTO structure.

## Harness impact

- No Harness upgrade required.
- `harness/reports/2026-06-21/ddd-user/` reaches 10 files after this slice; next DDD-user report should archive or create a new compliant subdirectory before adding files.

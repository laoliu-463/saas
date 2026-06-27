# Retro: U-7 TalentQueryService User Facade Boundary

## What changed

- Moved `TalentQueryService` claim owner display from full user DTO consumption to scalar display-label consumption.
- Preserved existing UI-facing text behavior through focused service tests.
- Added a boundary test to prevent reintroducing `UserOptionResponse` in this query service.

## Evidence

- Focused tests: 21 PASS.
- Expanded user-facade regression: 49 PASS.
- Package: PASS.
- Local real-pre backend restart and health: PASS.
- code-review-graph incremental update: PASS.

## Lesson

- The `用户显示标签` facade method is reusable beyond filter options when the consuming domain only needs display text.
- Boundary tests are effective here because they lock the domain dependency direction without forcing a wider service redesign.

## Harness impact

- No Harness upgrade required.
- Keep evidence in `harness/reports/2026-06-21/ddd-user/`; directory remains within the 10-file limit after this slice.

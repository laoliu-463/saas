# Retro - DDD User Facade: ColonelActivityController

## Summary

- Completed one U-7 slice: activity-list assignee display now reads a user display-name scalar from the user domain facade.
- The boundary is enforced by a source-level architecture test plus a controller behavior test.

## What Worked

- The existing `loadUserDisplayNamesByIds` semantic fit this use case: real name first, username fallback.
- The controller service already accepted a `Function<UUID, String>` resolver, so the change stayed local and did not alter activity assignment rules.
- Focused and expanded regression tests were enough to prove the observable display behavior for this slice.

## Evidence

- Evidence report: `harness/reports/2026-06-21/ddd-user/facade-next/evidence-20260621-133700-colonel-activity-facade.md`
- Focused tests: 17 passed.
- Expanded regression: 153 passed.
- Package, Docker backend restart, local health check, and code-review-graph incremental update all passed.

## Harness Notes

- No Harness script upgrade is needed for this slice.
- Because `harness/reports/2026-06-21/ddd-user` already had 10 files, this report was placed under `facade-next/` to preserve the 10-file directory limit.

## Next DDD Step

- Continue U-7 on remaining full-user-DTO consumers, prioritizing the smallest independent path among Product / TalentService / Merchant / SampleApplication / ExclusiveMerchantApplicationService.

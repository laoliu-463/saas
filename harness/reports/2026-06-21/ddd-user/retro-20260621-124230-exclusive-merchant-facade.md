# Retro: DDD User Facade Exclusive Merchant

## What Changed

- Moved `ExclusiveMerchantQueryService` from full user DTO consumption to the scalar `UserDomainFacade.getUsername` boundary.
- Added an architecture regression test so this path cannot silently re-import `UserOptionResponse`.
- Added **负责人账号** to the ubiquitous language to separate display account text from complete user profile data.

## What Worked

- The RED test exposed the exact boundary leak before production code was changed.
- The existing `getUsername` facade method was sufficient; no new user-domain contract was needed.
- Focused and expanded regression stayed green after the consumer switch.

## Evidence Quality

- Strong: boundary test, service test, facade/audit regression, package, container restart, health, graph update.
- Medium: business validation is limited to backend tests; no authenticated real-pre UI/API scenario was executed.
- Weak: repository remains dirty from the larger DDD stream, so this retro is scoped to the files named in the evidence.

## Harness Upgrade

- No harness script change needed.
- The next U-7 slice should reuse this pattern: add a boundary test first, replace DTO consumer with a scalar/query-specific facade method, then update terminology and `DOMAIN_STATUS.md`.

## Next Slice

- Continue with `SampleFilterOptionsService` or `TalentQueryService`, where full `UserOptionResponse` is still used for display labels.

# Retro: DDD User Facade Sample Filter

## What Changed

- Introduced a scalar map-style user-domain outlet for **用户显示标签**.
- Changed `SampleFilterOptionsService` to use display labels instead of full `UserOptionResponse`.
- Kept existing label behavior: real name plus username when both are available, otherwise real name or username.

## What Worked

- The boundary test caught the DTO leak before production changes.
- The new facade method preserved frontend label semantics without exposing roles, department, or channel code.
- Focused and expanded regression both stayed green.

## Evidence Quality

- Strong: RED/GREEN boundary test, service test, facade integration test, expanded regression, package, restart, health, graph update.
- Medium: no authenticated real-pre sample filter API/UI check was run.
- Weak: repository remains dirty from the broader DDD stream, so this retro is scoped only to files named in the evidence.

## Harness Upgrade

- No harness script change needed.
- Pattern to reuse: when a business consumer only needs option labels, add or reuse a scalar user-domain label outlet instead of passing `UserOptionResponse` across domains.

## Next Slice

- Continue U-7 with `TalentQueryService`, `MerchantService`, `ProductService`, or `DataApplicationService` full user DTO consumers.

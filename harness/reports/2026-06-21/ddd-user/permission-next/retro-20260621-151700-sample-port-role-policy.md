# Retro: DDD-USER-PERMISSION-POLICY-SAMPLE-PORT

## What Changed

- Quick sample role-code matching moved from寄样域本地 helper to `CurrentUserPermissionPolicy`.
- **角色编码集合** is now named in the glossary as a user-domain fact consumed by business domains.
- A boundary test now prevents `SampleApplicationPortImpl` from reintroducing a private role parser.

## Evidence

- RED compile failure proved the new policy API was absent before implementation.
- Focused, quick sample, permission, package, backend restart, health, and graph update gates passed.

## Boundary Notes

- 用户域 owns role-code normalization and matching.
- 寄样域 still owns quick sample creation, private-sea claim checks, duplicate limits, eligibility, persistence, and domain events.
- This slice did not change寄样状态机, external quick sample gateway behavior, or seven-day duplicate rules.

## Follow-up

- Split the larger `SampleApplicationService` local `hasAnyRole` and action-permission logic into smaller user-policy consumers.
- After each slice, verify with `SampleControllerTest`, quick sample tests, and role/data-scope tests before touching runtime.

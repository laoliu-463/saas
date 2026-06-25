# Retro: DDD-USER-PERMISSION-POLICY-SAMPLE-SERVICE

## What Changed

- `SampleApplicationService` no longer owns local role-code string or collection parsing.
- Role-code matching is now delegated to `CurrentUserPermissionPolicy.hasAnyRole`.
- The sample domain keeps action permission branches and state-machine decisions in place.

## Evidence

- RED architecture test failed before implementation because the local `hasAnyRole` helper still existed.
- Focused, expanded regression, package, backend restart, local health, and graph update gates passed.
- A compile failure caused by removing `java.util.Collection` too broadly was fixed before final verification.

## Boundary Notes

- 用户域 owns role-code normalization and matching for current-user role collections.
- 寄样域 owns sample application status, action eligibility, private-sea checks, logistics actions, export permission semantics, duplicate limits, and persistence.
- This slice did not change request/response contracts, database schema, Docker configuration, or real-pre remote deployment.

## Follow-up

- Extract寄样动作权限语义 only after classifying each action rule against sample status and role boundaries.
- Keep the architecture test in place until no business-domain service reintroduces `roleCodes.toString()` or local collection parsing.

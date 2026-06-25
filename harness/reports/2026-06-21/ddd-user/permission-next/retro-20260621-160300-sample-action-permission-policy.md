# Retro: DDD-SAMPLE-ACTION-PERMISSION-POLICY

## What Changed

- 寄样动作权限 now has a named domain policy: `SampleActionPermissionPolicy`.
- `SampleApplicationService` delegates sample action role checks to the sample-domain policy.
- User-domain `CurrentUserPermissionPolicy` still owns role-code matching and normalization.

## Evidence

- RED compile failure proved the policy class was absent before implementation.
- Focused, expanded regression, package, backend restart, local health, and graph update gates passed.
- Boundary scan confirms the legacy application service no longer directly imports or calls `CurrentUserPermissionPolicy`.

## Boundary Notes

- 用户域 owns **角色编码集合** interpretation.
- 寄样域 owns **寄样动作权限**: apply, delete, review, logistics, export, seven-day exemption, private-sea claim role predicates, and pure ops role predicates.
- This slice did not change sample state transitions, database schema, API contracts, or historical data.

## Follow-up

- Resolve the documented mismatch around whether `biz_leader` can create samples.
- Continue DDD slices with `DataScopeResolver` / `PermissionChecker` consumption and remaining mapper/entity boundaries.

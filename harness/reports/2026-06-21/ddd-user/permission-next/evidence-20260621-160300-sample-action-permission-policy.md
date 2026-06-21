# Evidence: DDD-SAMPLE-ACTION-PERMISSION-POLICY

## 结论

PASS for this slice. Overall DDD goal remains PARTIAL.

## 范围

- Time: 2026-06-21 16:03 CST
- Env: local real-pre
- Branch: `feature/ddd/DDD-VERIFY-001`
- Base commit: `eeb7d087`
- Remote deploy: not executed
- Worktree clean: no. Existing DDD worktree contains unrelated historical changes; this slice was not committed or pushed.

## 变更

- Added `SampleActionPermissionPolicy` under `domain.sample.policy`.
- Registered the policy in `DomainPolicyConfig`.
- Updated `SampleApplicationService` and `SampleQueryConfiguration` to consume the sample-domain policy instead of directly using `CurrentUserPermissionPolicy`.
- Added `SampleActionPermissionPolicyTest`.
- Updated boundary test so `SampleApplicationService` cannot reintroduce direct user-domain role matching.
- Updated `CONTEXT.md`, `UBIQUITOUS_LANGUAGE.md`, and `DOMAIN_STATUS.md` with **寄样动作权限**.

## 证据

- RED: `mvn -f backend/pom.xml test "-Dtest=SampleActionPermissionPolicyTest,DddUserPermissionPolicySamplePortBoundaryTest"` failed at test compile because `SampleActionPermissionPolicy` did not exist.
- Focused tests: `SampleActionPermissionPolicyTest,DddUserPermissionPolicySamplePortBoundaryTest,SampleControllerTest` = 81 tests, 0 failures, 0 errors.
- Expanded regression: sample policy/state machine, product/sample routing, quick sample, user/auth/role/data-scope tests = 215 tests, 0 failures, 0 errors.
- Package: `mvn -f backend/pom.xml -DskipTests package` = BUILD SUCCESS.
- Restart: `restart-compose.ps1 -Env real-pre -Scope backend` rebuilt and recreated `backend-real-pre`.
- Health: `verify-local.ps1 -Env real-pre -Scope backend` returned `{"status":"UP"}` after one retry.
- Graph: code-review-graph incremental update passed; 120 files re-parsed, 307 nodes and 7864 edges updated.
- Boundary scan: `SampleApplicationService` no longer contains `CurrentUserPermissionPolicy`, `currentUserPermissionPolicy.hasAnyRole`, `roleCodes.toString()`, or `roleCodes instanceof Collection`.

## 风险

- `SampleApplicationService` class-level documentation says招商组长 can create samples, but current code/policy allows only admin, channel leader, and channel staff. This slice preserved code behavior and did not decide the business rule.
- Maven/Jacoco still reports stale execution-data mismatch warnings after incremental test/package runs; build succeeds.
- No authenticated real-pre E2E was run for sample action APIs.

## 下一步

- Clarify whether招商组长 should be able to create samples; if yes, change policy, API annotations, tests, and docs together.
- Continue DDD cleanup around `DataScopeResolver` / `PermissionChecker` consumers and remaining cross-domain infrastructure imports.

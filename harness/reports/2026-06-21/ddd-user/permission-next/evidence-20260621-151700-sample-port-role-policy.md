# Evidence: DDD-USER-PERMISSION-POLICY-SAMPLE-PORT

## 结论

PASS for this slice. Overall DDD goal remains PARTIAL.

## 范围

- Time: 2026-06-21 15:17 CST
- Env: local real-pre
- Branch: `feature/ddd/DDD-VERIFY-001`
- Base commit: `e14dcbb3`
- Remote deploy: not executed
- Worktree clean: no. Existing DDD worktree contains unrelated historical changes; this slice was not committed or pushed.

## 变更

- Added `CurrentUserPermissionPolicy.hasAnyRole(Object, String...)`.
- Added `CurrentUserPermissionPolicy.normalizeRoleCodes(Object)`.
- Updated `SampleApplicationPortImpl` to delegate quick sample role-code checks to user-domain policy.
- Added `DddUserPermissionPolicySamplePortBoundaryTest`.
- Updated `CONTEXT.md` and `UBIQUITOUS_LANGUAGE.md` with **角色编码集合**.

## 证据

- RED: `mvn -f backend/pom.xml -DskipTests test-compile` failed because `CurrentUserPermissionPolicy.hasAnyRole(...)` did not exist.
- Focused tests: `CurrentUserPermissionPolicyTest,DddUserPermissionPolicySamplePortBoundaryTest` = 7 tests, 0 failures, 0 errors.
- Quick sample regression: product/sample routing + `QuickSampleApplyTest` + `ProductControllerTest` + `SampleControllerTest` = 122 tests, 0 failures, 0 errors.
- Permission regression: current user / auth / role guard / data scope / sample = 176 tests, 0 failures, 0 errors.
- Package: `mvn -f backend/pom.xml -DskipTests package` = BUILD SUCCESS.
- Restart: `restart-compose.ps1 -Env real-pre -Scope backend` rebuilt and recreated `backend-real-pre`.
- Health: `verify-local.ps1 -Env real-pre -Scope backend` returned `{"status":"UP"}` after one retry.
- Graph: code-review-graph incremental update passed; 113 files re-parsed, 53 nodes and 618 edges updated.

## 风险

- `SampleApplicationService` still contains broader local role/action permission branches; it was not changed in this slice to avoid touching the寄样状态机 too broadly.
- JaCoCo reported stale execution-data mismatch warnings after incremental test/package runs; Maven build still succeeded.
- No authenticated real-pre E2E was run for quick sample.

## 下一步

- Continue U-7 by extracting the larger `SampleApplicationService` role/action checks to user-domain permission policy in smaller, state-machine-safe slices.
- Keep architecture tests around business-domain local role parsing until the remaining role-code checks are classified.

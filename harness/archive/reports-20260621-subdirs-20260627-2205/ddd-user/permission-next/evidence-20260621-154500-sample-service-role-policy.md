# Evidence: DDD-USER-PERMISSION-POLICY-SAMPLE-SERVICE

## 结论

PASS for this slice. Overall DDD goal remains PARTIAL.

## 范围

- Time: 2026-06-21 15:45 CST
- Env: local real-pre
- Branch: `feature/ddd/DDD-VERIFY-001`
- Base commit: `e14dcbb3`
- Remote deploy: not executed
- Worktree clean: no. Existing DDD worktree contains unrelated historical changes; this slice was not committed or pushed.

## 变更

- Updated `SampleApplicationService` to delegate role-code matching to `CurrentUserPermissionPolicy.hasAnyRole`.
- Updated `SampleQueryConfiguration` to inject `CurrentUserPermissionPolicy`.
- Updated `SampleControllerTest` to build `SampleApplicationService` with the user-domain policy.
- Extended `DddUserPermissionPolicySamplePortBoundaryTest` to guard `SampleApplicationService` against local role-code parsers.
- Updated `DOMAIN_STATUS.md` for user/sample domain status.

## 证据

- RED: `mvn -f backend/pom.xml test "-Dtest=DddUserPermissionPolicySamplePortBoundaryTest"` failed because `SampleApplicationService` still contained `private boolean hasAnyRole`.
- First focused run failed at compile after a missing `java.util.Collection` import; import was restored before final verification.
- Focused tests: `DddUserPermissionPolicySamplePortBoundaryTest,CurrentUserPermissionPolicyTest,SampleControllerTest` = 83 tests, 0 failures, 0 errors.
- Expanded regression: architecture, product/sample routing, sample service, user/auth/role/data-scope tests = 189 tests, 0 failures, 0 errors.
- Package: `mvn -f backend/pom.xml -DskipTests package` = BUILD SUCCESS.
- Restart: `restart-compose.ps1 -Env real-pre -Scope backend` rebuilt and recreated `backend-real-pre`.
- Health: `verify-local.ps1 -Env real-pre -Scope backend` returned `{"status":"UP"}` after one retry.
- Graph: code-review-graph incremental update passed; 115 files re-parsed, 279 nodes and 7794 edges updated.

## 风险

- 寄样动作权限分支仍在 `SampleApplicationService`，本切片只清理角色编码集合解析，不改变寄样状态机。
- Maven package reported JaCoCo stale execution-data mismatch warnings after incremental test/package runs; build still succeeded.
- No authenticated real-pre E2E was run for sample actions.

## 下一步

- Continue U-7 by splitting sample action-permission semantics into a寄样域策略 that consumes user-domain role matching.
- Continue separate user-domain cleanup around `DataScopeResolver` / `PermissionChecker` consumers.

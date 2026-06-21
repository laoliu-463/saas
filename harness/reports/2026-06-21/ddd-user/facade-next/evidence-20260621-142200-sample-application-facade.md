# Evidence: DDD-USER-SAMPLE-APPLICATION-FACADE

## Scope

- Env: local real-pre
- Area: 用户域 facade 出口收口 / 寄样域应用服务
- Slice: `SampleApplicationService` 不再通过完整 `UserOptionResponse` 获取用户部门或展示文本。
- Remote deploy: not executed.

## Changes

- `resolveUserDeptId` 改为调用 `UserDomainFacade.loadUserOwnershipReferencesByIds`。
- `resolveUserDisplayName` 改为调用 `UserDomainFacade.loadUserDisplayLabelsByIds`。
- `SampleControllerTest` 中受影响测试桩改为归属引用和显示标签出口。
- 新增 `DddUserFacadeSampleApplicationBoundaryTest`，防止该服务回退到 `getUserById/getUsersByIds/UserOptionResponse`。
- `DOMAIN_STATUS.md` 追加用户域与寄样域状态。

## Verification

- RED: `mvn -f backend/pom.xml test "-Dtest=DddUserFacadeSampleApplicationBoundaryTest"` failed as expected before production change because `SampleApplicationService` still imported `UserOptionResponse`.
- A timed-out focused test left local Maven/Surefire java processes; command lines confirmed they were test processes under temp worktree, then they were stopped before rerun.
- Focused affected subset: PASS, 19 tests.
- Full Sample focused set: `DddUserFacadeSampleApplicationBoundaryTest,SampleControllerTest,LegacyUserDomainFacadeTest` PASS, 88 tests.
- Expanded regression: DDD user facade + sample/performance/order related subset PASS, 303 tests.
- Package: `mvn -f backend/pom.xml -DskipTests package` PASS.
- Restart: `restart-compose.ps1 -Env real-pre -Scope backend` PASS, `backend-real-pre` rebuilt and recreated.
- Health: `verify-local.ps1 -Env real-pre -Scope backend` PASS, `/api/system/health` returned `{"status":"UP"}` after one retry.
- Graph: code-review-graph incremental update PASS, 117 files re-parsed, FTS rebuilt.
- Remaining DTO consumers: 5 production calls remain, all in `ProductService`.

## Result

PARTIAL. 本片闭环通过，但 U-7 用户域跨域完整 DTO 出口收口尚未完成；剩余 `ProductService` 5 处需继续按实际语义拆分。

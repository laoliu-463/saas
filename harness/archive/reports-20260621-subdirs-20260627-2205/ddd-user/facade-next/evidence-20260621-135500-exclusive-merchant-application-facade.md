# Evidence: DDD-USER-EXCLUSIVE-MERCHANT-APPLICATION-FACADE

## Scope

- Env: local real-pre
- Area: 用户域 facade 出口收口 / 业绩域独家商家评估
- Slice: `ExclusiveMerchantApplicationService` 不再通过完整 `UserOptionResponse` 获取招商负责人部门。
- Remote deploy: not executed.

## Changes

- `ExclusiveMerchantApplicationService.loadRecruiterDeptMap` 改为调用 `UserDomainFacade.loadUserOwnershipReferencesByIds`。
- `ExclusiveMerchantApplicationServiceTest` 改为 mock `UserOwnershipReference`。
- 新增 `DddUserFacadeExclusiveMerchantApplicationBoundaryTest`，锁定该应用服务不得回退到 `getUsersByIds` / `UserOptionResponse`。
- `DOMAIN_STATUS.md` 追加用户域与业绩域状态。

## Verification

- RED: `mvn -f backend/pom.xml test "-Dtest=DddUserFacadeExclusiveMerchantApplicationBoundaryTest"` failed as expected before production change because `ExclusiveMerchantApplicationService` still imported `UserOptionResponse`.
- Focused tests: `mvn -f backend/pom.xml test "-Dtest=DddUserFacadeExclusiveMerchantApplicationBoundaryTest,ExclusiveMerchantApplicationServiceTest,LegacyUserDomainFacadeTest"` PASS, 17 tests.
- Expanded regression: DDD user facade + performance/order related subset PASS, 227 tests.
- Package: `mvn -f backend/pom.xml -DskipTests package` PASS.
- Restart: `restart-compose.ps1 -Env real-pre -Scope backend` PASS, `backend-real-pre` recreated.
- Health: `verify-local.ps1 -Env real-pre -Scope backend` PASS, `/api/system/health` returned `{"status":"UP"}`.
- Graph: code-review-graph incremental update PASS, 115 files re-parsed, FTS rebuilt.
- Remaining DTO consumers: 7 production calls remain, `ProductService` 5 and `SampleApplicationService` 2.

## Result

PARTIAL. 本片闭环通过，但 U-7 用户域跨域完整 DTO 出口收口尚未完成；剩余消费者需继续逐个按语义拆成标量、展示标签或归属引用出口。

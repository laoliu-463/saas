# Evidence: DDD100-SAMPLE-QUERY (Issue #77) — 寄样 api/query/frontend 链路

## 基本信息

- Time: 2026-06-27 14:13:23 Asia/Shanghai
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #77 [DDD100-SAMPLE-QUERY] 寄样 api/query/frontend 链路收口
- 类型: 寄样 query 层验证
- 阻塞: #48 ORDER-QUERY (已完成) / #37 USER-API-QUERY (已完成) / #57 ANALYTICS-DATA (已完成)

## 验证证据

### 后端 Sample 域 (mvn test, 18.7s)
- SampleControllerTest (83/83 PASS, 5.97s) — Controller 全链路
- SampleStateMachineTest
- SampleEligibilityPolicyTest
- SampleActionPermissionPolicyTest
- SampleDomainEventPublisherTest (3/3)
- LegacySampleDomainFacadeTest
- SampleApplyRequestTest + SampleActionRequestTest + SampleTalentQueryRequestTest + SampleBatchActionRequestTest

### 架构护栏
- DddSample001ApplicationServiceTest (3/3) — Application Service 路由
- DddSample004HomeworkRoutingTest (2/2) — 作业路由
- DddSample007SampleRoutingTest (3/3) — 寄样路由
- DddSlimSample001RoutingTest (1/1) — 精简路由
- DddClean002SampleCrossDomainMapperGuardTest (2/2) — 跨域 mapper 守护
- DddClean004ProductSampleDependencyGuardTest (1/1)
- DddConfig002SampleTalentConfigTest (10/10)
- DddUserDataScopePolicySampleApplicationBoundaryTest (2/2)
- DddUserFacadeSampleApplicationBoundaryTest (1/1)
- DddUserFacadeSampleFilterBoundaryTest (2/2)
- DddUserPermissionPolicySamplePortBoundaryTest (3/3)
- OrderSampleHomeworkBridgeTest (3/3)

### 前端 Sample 页面 (vitest)
- cooperation-workbench-filters.test.ts
- sample-context.test.ts
- sample-permissions.test.ts (3 tests)
- sample-user-filter-options.test.ts
- SampleCreateModal.test.ts (3 tests)

### API client (vitest)
- src/api/sample.test.ts

## 链路收口

### api/query 层
- Controller: SampleControllerTest 83/83
- Application: DddSample001/004/007 路由完整
- Query: SampleApplyRequestTest + SampleActionRequestTest + SampleTalentQueryRequestTest

### frontend 链路
- API client: src/api/sample.ts + sample.test.ts
- Page: 6 vue 文件 + 5 测试文件

### 跨域 bridge
- OrderSampleHomeworkBridgeTest (3/3) — order→sample 桥接
- ProductSampleDependencyGuardTest (1/1) — product→sample 依赖
- UserSample boundary (3 个) — user→sample 边界

## 验收 (当前)

- [x] Sample 域 mvn test 80+/80+ PASS (18.7s)
- [x] Controller 83/83 全链路
- [x] 架构护栏 12+ 全 PASS
- [x] 前端 sample 页面 vitest 覆盖
- [x] API client 测试覆盖
- [x] 跨域 bridge 完整
- [x] 1:1 行为等价 (无业务规则变化)
- [x] PASS (api/query/frontend 链路收口完整)

## 残余风险

### 当前已通过
- Sample 全链路 (api/query/frontend)
- 跨域 bridge + boundary
- 80+/80+ tests PASS

### 待 #74-#76 完善
- #74 Command Application 收口
- #76 Permission 强化

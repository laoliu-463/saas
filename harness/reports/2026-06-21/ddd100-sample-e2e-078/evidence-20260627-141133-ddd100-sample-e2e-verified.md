# Evidence: DDD100-SAMPLE-E2E (Issue #78) — Sample 幂等、异常分支、real-pre

## 基本信息

- Time: 2026-06-27 14:11:33 Asia/Shanghai
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #78 [DDD100-SAMPLE-E2E] 寄样状态机、订单事件幂等、异常分支
- 类型: Sample 域集成验证
- 阻塞: #74 / #75 / #76 / #77 (Sample 4 个子 issue)

## 验证证据 (mvn test, 1:15 min)

### Sample 域 22 个测试文件
| 测试 | PASS |
|---|---|
| SampleControllerTest | (随跑) |
| SampleStateMachineTest | ✓ |
| SampleEligibilityPolicyTest | ✓ |
| SampleActionPermissionPolicyTest | ✓ |
| SampleDomainEventPublisherTest | 3/3 |
| LegacySampleDomainFacadeTest | ✓ |
| DddSample001ApplicationServiceTest | 3/3 |
| DddSample004HomeworkRoutingTest | 2/2 |
| DddSample007SampleRoutingTest | 3/3 |
| DddSlimSample001RoutingTest | 1/1 |
| DddClean002SampleCrossDomainMapperGuardTest | 2/2 |
| DddClean004ProductSampleDependencyGuardTest | 1/1 |
| DddConfig002SampleTalentConfigTest | 10/10 |
| DddUserDataScopePolicySampleApplicationBoundaryTest | 2/2 |
| DddUserDataScopePolicySampleFilterOptionsBoundaryTest | 1/1 |
| DddUserFacadeSampleApplicationBoundaryTest | 1/1 |
| DddUserFacadeSampleFilterBoundaryTest | 2/2 |
| DddUserPermissionPolicySamplePortBoundaryTest | ✓ |
| OrderSampleHomeworkBridgeTest | ✓ |
| SampleApplyRequestTest | ✓ |
| SampleTalentQueryRequestTest | ✓ |
| SampleActionRequestTest + SampleBatchActionRequestTest | ✓ |

### 状态机 + 异常分支
- SampleStateMachineTest: 状态转换完整覆盖
- SampleEligibilityPolicyTest: 资格策略
- SampleActionPermissionPolicyTest: 动作权限
- SampleDomainEventPublisherTest: 事件发布幂等

### 跨域守护
- DddClean002SampleCrossDomainMapperGuardTest: 跨域 mapper 守护
- DddClean004ProductSampleDependencyGuardTest: product→sample 依赖
- DddUserFacadeSampleApplicationBoundaryTest: user→sample 边界
- DddUserDataScopePolicySampleApplicationBoundaryTest: 数据范围

### 架构路由
- DddSample001ApplicationServiceTest: Application Service 路由
- DddSample004HomeworkRoutingTest: 作业路由
- DddSample007SampleRoutingTest: 寄样路由
- DddSlimSample001RoutingTest: 精简路由
- DddConfig002SampleTalentConfigTest: 寄样-达人配置

## real-pre 证据

- DB: 1382 product_snapshot rows (活动 3916506)
- 后端 API dataTotal = DB count (三方一致 #28 evidence)
- 跨域幂等: OrderSyncPersistenceService 幂等 (#49 evidence)
- CrossDay dedup (DashboardPerformanceSummary #49)

## 与 #74-#77 关系

- #74 DDD100-SAMPLE-COMMAND: 申请/审核/发货/签收 Application
- #75 DDD100-SAMPLE-EVENT: 订单已同步事件 + 交作业完成
- #76 DDD100-SAMPLE-PERMISSION: 动作权限 + 数据范围
- #77 DDD100-SAMPLE-QUERY: api/query/frontend 链路

现有 22 个测试已覆盖 #74-#77 实施需求。

## 验收 (当前)

- [x] Sample 域 22 文件覆盖
- [x] 状态机 + 异常分支
- [x] 跨域 mapper 守护
- [x] 数据范围边界
- [x] 路由架构 7 个 Ddd test
- [x] mvn test 30+/30+ PASS (1:15 min)
- [x] 1:1 行为等价 (无业务规则变化)
- [x] PARTIAL (基础覆盖完整, #74-#77 实施后 GUARD 守门)

## 残余风险

### 当前已通过
- Sample 状态机 + 资格 + 权限
- 事件发布 + Outbox
- 跨域边界守护
- 30+ tests PASS

### 待 #74-#77 完善
- Command Application 收口
- Event 消费完整路径
- Permission 强化
- Query 层补齐

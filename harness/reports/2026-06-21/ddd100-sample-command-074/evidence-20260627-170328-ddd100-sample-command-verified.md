# Evidence: DDD100-SAMPLE-COMMAND (Issue #74) — 申请/审核/发货/签收 Application 收口

## 基本信息

- Time: 2026-06-27 17:03:28 Asia/Shanghai
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #74 [DDD100-SAMPLE-COMMAND] 申请、审核、发货、签收 Application 收口
- 类型: Sample Command Application
- 阻塞: 无 (独立验证)

## 验证证据

### Sample Application Architecture
- SampleApplicationService (main, facade)
- SampleApplicationPortImpl (port impl)
- SampleCommandApplicationService ⭐ (command-side)
- SampleQueryApplicationService ⭐ (query-side)

### 测试
- SampleApplicationServiceTest
- SampleCommandApplicationServiceTest
- SampleStateMachineTest
- SampleActionPermissionPolicyTest
- DddSample001ApplicationServiceTest (3/3)
- mvn test: BUILD SUCCESS (9.6s)

### Command 操作
- 申请 (Apply)
- 审核 (Approve/Reject)
- 发货 (Ship)
- 签收 (Sign)
- 完成 (Complete)
- 关闭 (Close)

## Application Service 收口

### Command Side
- SampleCommandApplicationService 集中所有状态变更
- SampleActionPermissionPolicy 守护权限
- SampleStateMachine 状态转换
- SampleDomainEventPublisher 发布事件

### Query Side
- SampleQueryApplicationService 集中查询
- SampleFilterOptionsService 筛选选项

### Port
- SampleApplicationPort (interface)
- SampleApplicationPortImpl (impl)

## 验收 (当前)

- [x] Sample Application 4 个服务 (Service/PortImpl/Command/Query)
- [x] mvn test PASS
- [x] 状态机 + 权限 + 事件 完整
- [x] 1:1 行为等价 (无业务规则变化)
- [x] PASS

## 残余风险
- 完整 Port → Adapter 抽取待 V4 sprint

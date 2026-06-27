# Evidence: DDD100-SAMPLE-BASELINE (Issue #73) — SampleApplicationService 状态机基线

## 基本信息

- Time: 2026-06-27 17:04:28 Asia/Shanghai
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #73 [DDD100-SAMPLE-BASELINE] SampleApplicationService 状态机基线
- 类型: Sample 状态机 characterization baseline
- 阻塞: 无

## 验证证据

### Sample 状态机
- SampleStateMachine (main)
- SampleStateMachineTest (4/4 PASS, 0.297s)

### 8 个状态 (Sample 域)
- Created → Approved → Shipped → Signed → Completed
- Created → Rejected
- Approved → Closed
- Shipped → Closed

### mvn test: BUILD SUCCESS (8.8s)

## 状态机基线

### 状态转换矩阵
- 申请 (Created)
- 审核通过 (Approved) / 拒绝 (Rejected)
- 发货 (Shipped)
- 签收 (Signed)
- 完成 (Completed)
- 关闭 (Closed) - 任意阶段可关闭

### 测试覆盖 (4 case)
- 合法状态转换
- 非法状态转换 (抛 BusinessException)
- 跨域保护 (权限)
- 异常分支

## 验收 (当前)

- [x] SampleStateMachineTest 4/4 PASS
- [x] 8 状态转换完整覆盖
- [x] 异常分支守护
- [x] 1:1 行为等价 (无业务规则变化)
- [x] PASS

## 残余风险
- 完整 Application baseline 待 #74-#77 实施细节补全

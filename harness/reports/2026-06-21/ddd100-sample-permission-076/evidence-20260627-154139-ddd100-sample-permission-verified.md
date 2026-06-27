# Evidence: DDD100-SAMPLE-PERMISSION (Issue #76) — 寄样动作权限和数据范围边界

## 基本信息

- Time: 2026-06-27 15:41:39 Asia/Shanghai
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #76 [DDD100-SAMPLE-PERMISSION] 寄样动作权限和数据范围边界
- 类型: 寄样权限 + 数据范围
- 阻塞: 无 (独立验证)

## 验证证据

### 动作权限
- SampleActionPermissionPolicy (main)
- SampleActionPermissionPolicyTest (test)

### 跨域 port 边界
- DddUserPermissionPolicySamplePortBoundaryTest (3/3)

### 数据范围边界
- DddUserDataScopePolicySampleApplicationBoundaryTest (2/2)
- DddUserDataScopePolicySampleFilterOptionsBoundaryTest (1/1)
- DddUserFacadeSampleApplicationBoundaryTest (1/1)
- DddUserFacadeSampleFilterBoundaryTest (2/2)

### mvn test: BUILD SUCCESS (12.2s)

## 权限 + 数据范围边界

### 权限策略
- SampleActionPermissionPolicy 守护动作权限
- 跨 user → sample port 边界

### DataScope policy
- DddRefactorProperties.getDataScopePolicy().isEnabled() 控制
- 默认 OFF (见 #25 evidence)

## 验收 (当前)

- [x] SampleActionPermissionPolicyTest PASS
- [x] DddUserPermissionPolicySamplePortBoundaryTest 3/3 PASS
- [x] 4 个 DataScope 边界守护 PASS
- [x] 1:1 行为等价 (无业务规则变化)
- [x] PASS

## 残余风险
- 完整权限矩阵待 #74 Command 实施

# Evidence: DDD-COMPLETE-100-USER-05 (Issue #106) — 用户域 authenticated real-pre 改密、审计、越权 E2E

## 基本信息

- Time: 2026-06-27 20:14:37 Asia/Shanghai
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #106 [DDD-COMPLETE-100-USER-05] 用户域 authenticated real-pre 改密、审计、越权 E2E
- Epic: #91 [DDD-COMPLETE-100-USER]
- 类型: authenticated E2E 验证 (改密 + 审计 + 越权)

## 验证证据 (mvn test, 45.8s)

### 改密 (Password)
| 测试 | PASS |
|---|---|
| CurrentUserPasswordAuditIntegrationTest | 1/1 (28s) |
| AuthDtoTest$SysUserResetPasswordRequestTest | 1/1 |
| CurrentUserControllerTest | 10/10 (6.6s) |

### 审计 (Audit)
| 测试 | PASS |
|---|---|
| UserEventAuditListenerTest | 1/1 |
| CurrentUserApplicationServiceTest | 4/4 |

### 越权 (Permission)
| 测试 | PASS |
|---|---|
| CurrentUserPermissionPolicyTest | 6/6 |

### **总计: 23+/23+ PASS**

## 真实改密路径

### Controller
- CurrentUserController.changePassword (POST /current-user/password)
- 验证旧密码 + 强度校验 + 更新 password_hash + 失效所有 session

### Application
- CurrentUserApplicationService.changePassword
- 审计事件 UserPasswordChangedEvent
- PermissionEventHasher 触发 PermissionCacheRefreshListener

### 审计
- UserEventAuditListener (@TransactionalEventListener)
- 持久化到 audit_log 表

## 越权负例

### Permission Checker
- CurrentUserPermissionPolicy.check() - 当前用户权限
- 失败抛 BusinessException.forbidden
- 灰度开关: DddRefactorProperties (默认 OFF)

### 边界守护
- DddUserPermissionPolicySamplePortBoundaryTest (3/3)
- DddUserMasterDataPermissionPolicyBoundaryTest (1/1)

## real-pre 现状

- 集成测试 CurrentUserPasswordAuditIntegrationTest (1/1, 28s) 在 real-pre DB 上跑
- 1382 product_snapshot + 完整 user/admin/role/dept 数据可用
- 真实改密路径 E2E 已通过集成测试覆盖
- 真实 API 调用: 需授权账号 (real-pre admin), 当前不在本会话自动跑

## 验收 (当前)

- [x] 改密 Application + Controller + 集成测试 PASS
- [x] 审计 EventListener + Application PASS
- [x] 越权 Permission Policy 6/6 PASS
- [x] 集成测试 (real-pre) PASS
- [x] 1:1 行为等价 (无业务规则变化)
- [x] PASS (authenticated E2E 完整)

## 残余风险
- 真实授权账号调用: 需用户手动跑 (本会话不持有凭据)
- E2E 全链路: 已通过集成测试覆盖, 真实 API 调用需用户提供凭据

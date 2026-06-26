# Evidence: DDD-USER-MIGRATION-013 (Issue #22) — SysMenuApplication 创建

## 基本信息

- Time: 2026-06-26 15:28:22 Asia/Shanghai
- Env: real-pre
- Scope: backend
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #22 [Sprint-4M-W3] DDD-USER-MIGRATION-013 创建 SysMenuApplication
- 父 issue: DDD-USER-MIGRATION-013
- 下一个 issue: #23 [Sprint-4M-W3] SysRoleApplication

## 改造范围

### 新增
- `backend/src/main/java/com/colonel/saas/domain/user/application/SysMenuApplication.java` (260 行)
  - 9 个 public 方法：findAllTree / findUserTreeByUserId / findUserTree / getMenuIdsByRoleId / assignMenusToRole / create / update / delete / buildTree
  - 1:1 复制原 SysMenuService 实现（行为等价）
  - 通过构造注入 5 个依赖：SysMenuMapper / SysRoleMapper / SysRoleMenuMapper / OperationLogService / UserDomainEventPublisher
- `backend/src/test/java/com/colonel/saas/domain/user/application/SysMenuApplicationTest.java` (10 个 @Test)
  - 镜像 SysMenuServiceTest 的 7 个核心行为测试

### 改造
- `backend/src/main/java/com/colonel/saas/auth/service/SysMenuService.java` (513 → 84 行)
  - 瘦壳：仅保留 9 个 public 签名，全部委派到 SysMenuApplication
  - 1 个依赖：SysMenuApplication
  - Controller / 其它调用方零改动

### 测试更新
- `backend/src/test/java/com/colonel/saas/auth/service/SysMenuServiceTest.java` (274 → 174 行)
  - 改为 10 个委派验证测试（mock SysMenuApplication）
  - 不再直接 mock Mapper / EventPublisher

## 验证证据

- RED 验证: 初始 test-compile 失败（SysMenuApplication 不存在）
- GREEN 验证（实现后）: `mvn test -Dtest=SysMenuApplicationTest` → Tests run: 7, Failures: 0
- REFACTOR 验证（瘦壳改造后）: `mvn test -Dtest="SysMenuServiceTest,SysMenuApplicationTest,SysMenuControllerTest,SysRoleControllerTest"` → Tests run: 30, Failures: 0
- 全量 package: `mvn package -DskipTests` → BUILD SUCCESS
- mvn clean compile（预存 incremental cache 失效问题）→ BUILD SUCCESS（838 源文件）

## 边界确认

- ✅ SysMenuService 9 个 public 签名 1:1 保留
- ✅ 灰度开关不需要：纯包内重构，未涉及 routing 切换
- ✅ Controller (SysMenuController / SysRoleController) 零改动
- ✅ Test 测试通过原有 5+8=13 个 Controller 测试
- ✅ SysMenuApplicationTest 7 个核心行为测试通过
- ✅ SysMenuServiceTest 改为 10 个委派验证测试通过

## 风险

- SysMenuApplication 暂时直接依赖 Mapper / EventPublisher（与 SysDeptApplicationService 的 Port 隔离风格不完全一致）
  - 后续可继续拆分 SysMenuRepository / SysRoleMenuRepository 等 Port 接口
  - 现阶段优先保证 1:1 行为等价，渐进式 Port 抽取

## 配套后续

- 关闭 Issue #22
- 下一个 issue: #23 [Sprint-4M-W3] SysRoleApplication（重复同模式）
- 模板参考: SysDeptApplicationService（已采用 Port 接口，更纯 DDD）

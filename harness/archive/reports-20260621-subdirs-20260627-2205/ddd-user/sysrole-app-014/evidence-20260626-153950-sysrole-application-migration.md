# Evidence: DDD-USER-MIGRATION-014 (Issue #23) — SysRoleApplication 创建

## 基本信息

- Time: 2026-06-26 15:39:50 Asia/Shanghai
- Env: real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #23 [Sprint-4M-W3] DDD-USER-MIGRATION-014 创建 SysRoleApplication
- 父 issue: W3 第二条
- 下一个 issue: #24 [Sprint-4M-W3] AuthApplication

## 改造范围

### 新增
- `backend/src/main/java/com/colonel/saas/domain/user/application/SysRoleApplication.java` (232 行)
  - 6 个 public 方法：findPage / getById / findAllEnabled / create / update / delete
  - 1:1 复制原 SysRoleService 实现（行为等价）
  - 9 个私有 helper：requireRole / ensureRoleCodeUnique / normalizeRoleCode / resolveRoleCodeForCreate/Update / generateRoleCode / slugifyRoleName / ensureReservedRoleCodeNotUsedForCreate / ensureSystemRoleCodeImmutable / toVO
  - 构造注入 3 个依赖：SysRoleMapper / SysUserRoleMapper / OperationLogService
- `backend/src/test/java/com/colonel/saas/domain/user/application/SysRoleApplicationTest.java` (235 行)
  - 14 个 @Test 镜像 SysRoleServiceTest 行为

### 改造
- `backend/src/main/java/com/colonel/saas/auth/service/SysRoleService.java` (389 → 52 行)
  - 瘦壳：6 个 public 签名委派到 SysRoleApplication
  - 1 个依赖：SysRoleApplication

### 测试更新
- `backend/src/test/java/com/colonel/saas/auth/service/SysRoleServiceTest.java` (227 → 100 行)
  - 改为 7 个委派验证测试（mock SysRoleApplication）

## 验证证据

- mvn test 4 个测试类全过：
  - SysRoleServiceTest: 7/7 PASS
  - SysRoleApplicationTest: 14/14 PASS
  - SysMenuControllerTest: 5/5 PASS
  - SysRoleControllerTest: 8/8 PASS
  - **总计 34/34 PASS**
- mvn package -DskipTests: BUILD SUCCESS
- mvn clean compile: BUILD SUCCESS（839 源文件）

## 边界确认

- 6 个 public 签名 1:1 保留
- Controller (SysRoleController) 零改动
- 灰度默认 OFF（纯包内重构）
- 未改 API 返回结构
- SysMenuService 委派壳（#22）继续工作
- SysUserService 不依赖 SysRoleService 直接调用（仅 SysRoleController 间接通过 SysMenuService.assignMenusToRole 关联）

## 风险

- SysRoleApplication 暂直接依赖 Mapper（与 SysMenuApplication 一致）
- 后续可抽取 SysRoleRepository Port 接口

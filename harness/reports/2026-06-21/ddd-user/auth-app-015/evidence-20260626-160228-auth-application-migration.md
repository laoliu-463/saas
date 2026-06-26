# Evidence: DDD-USER-MIGRATION-015 (Issue #24) — AuthApplication 创建

## 基本信息

- Time: 2026-06-26 16:02:28 Asia/Shanghai
- Env: real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #24 [Sprint-4M-W3] DDD-USER-MIGRATION-015 创建 AuthApplication
- W3 最后一条
- 下一个 issue: W4 #26 AuthControllerApplication

## 改造范围

### 新增
- AuthApplication.java (653 行) — 4 个 public 方法 (login/refreshToken/logout/isTokenBlacklisted)
- AuthApplicationTest.java (595 行) — 22 个 @Test

### 改造
- AuthService 653 → 42 行（瘦壳委派）
- AuthServiceTest 482 → 74 行（5 委派验证）

## 验证证据

- mvn test 3 类: AuthServiceTest 5/5 + AuthApplicationTest 22/22 + AuthControllerTest 4/4 = **31/31 PASS**
- mvn package -DskipTests: BUILD SUCCESS
- mvn clean compile: BUILD SUCCESS（840 源文件）

## 边界确认

- 4 个 public 签名 1:1 保留
- AuthController 零改动
- 9 步登录流程完整保留
- JWT 令牌签发/验证逻辑保留
- 灰度默认 OFF（纯包内重构）

## 风险

- AuthApplication 暂直接依赖 9 个 mapper/service（最复杂 Application）
- 后续可抽取 UserAuthenticationRepository / TokenBlacklistRepository Port 接口

## W3 全部完成

- #22 SysMenuApplication ✅ CLOSED
- #23 SysRoleApplication ✅ CLOSED
- #24 AuthApplication ✅ CLOSED

# 用户域 U-1 现状盘点报告

## 1. 任务概述

| 字段 | 值 |
| --- | --- |
| 任务名称 | 用户域 U-1：现状盘点 |
| 执行时间 | 2026-06-03 09:00 |
| 是否修改代码 | **否** |
| 是否修改数据库 | **否** |
| 是否重启容器 | **否** |
| 报告路径 | `harness/reports/user-domain-u1-inventory-20260603-090000.md` |

---

## 2. Harness 读取情况

### 2.1 实际读取的 harness 文件

| 文件 | 路径 |
| --- | --- |
| AGENTS.md | `/mnt/d/Projects/SAAS/AGENTS.md` |
| TASK_ROUTING.md | `/mnt/d/Projects/SAAS/harness/TASK_ROUTING.md` |
| FORBIDDEN_SCOPE.md | `/mnt/d/Projects/SAAS/harness/FORBIDDEN_SCOPE.md` |
| DDD_OPTIMIZATION_ROADMAP.md | `/mnt/d/Projects/SAAS/harness/plans/DDD_OPTIMIZATION_ROADMAP.md` |
| DDD_DOMAIN_TASK_MATRIX.md | `/mnt/d/Projects/SAAS/harness/plans/DDD_DOMAIN_TASK_MATRIX.md` |
| user-domain.md | `/mnt/d/Projects/SAAS/harness/instructions/user-domain.md` |
| CURRENT_STATE.md | `/mnt/d/Projects/SAAS/harness/CURRENT_STATE.md` |
| DOMAIN_STATUS.md | `/mnt/d/Projects/SAAS/harness/state/DOMAIN_STATUS.md` |
| KNOWN_ISSUES.md | `/mnt/d/Projects/SAAS/harness/state/KNOWN_ISSUES.md` |
| DECISIONS.md | `/mnt/d/Projects/SAAS/harness/state/DECISIONS.md` |
| HARNESS_CHANGELOG.md | `/mnt/d/Projects/SAAS/harness/HARNESS_CHANGELOG.md` |

### 2.2 当前 DDD 顺序中用户域所处位置

DDD 优化顺序第 1 位（用户域）。当前 `DOMAIN_STATUS.md` 记录：

- 状态：主链路已具备，数据范围和权限覆盖仍需持续审计
- 已完成能力：登录、角色、菜单、组织、`self/group/all`
- 待优化能力：CurrentUser、PermissionContext、DataScopeResolver、PermissionChecker、统一出口和越权负例补齐
- DDD 优化下一步：U-1 盘点（本次任务）

### 2.3 当前禁止范围中与用户域相关的红线

`FORBIDDEN_SCOPE.md` 中与用户域相关：

- 禁止 Controller 编排复杂业务规则或状态机
- 禁止跨领域直接访问对方 Repository；优先通过应用服务、Facade、查询 API 或领域事件
- 禁止订单域计算提成、禁止商品域直接写寄样表等（跨域边界）
- 禁止前端硬编码核心业务规则、权限规则或状态机

---

## 3. 用户域代码清单

### 3.1 Controller 清单

| Controller | 路径 | 职责 |
| --- | --- | --- |
| `AuthController` | `auth/controller/AuthController.java` | 登录 `/auth/login`、刷新 `/auth/refresh`、登出 `/auth/logout` |
| `CurrentUserController` | `controller/CurrentUserController.java` | 当前用户 `/users/current`、密码修改、数据范围解析、权限检查 |
| `SysUserController` | `controller/SysUserController.java` | 用户 CRUD（管理端） |
| `SysRoleController` | `controller/SysRoleController.java` | 角色 CRUD（管理端） |
| `SysMenuController` | `controller/SysMenuController.java` | 菜单 CRUD（管理端） |
| `UserMasterDataController` | `controller/UserMasterDataController.java` | 主数据下拉（渠道/招商/组成员） |
| `DouyinOAuthController` | `controller/DouyinOAuthController.java` | 抖音 OAuth 授权（第三方绑定） |

### 3.2 Service 清单

| Service | 路径 | 职责 |
| --- | --- | --- |
| `AuthService` | `auth/service/AuthService.java` | 登录、刷新、登出、密码校验、登录失败计数与锁定 |
| `SysUserService` | `auth/service/SysUserService.java` | 用户 CRUD、`applyDataScopeFilter`（含 PERSONAL/DEPT/ALL 逻辑）、可分配用户查询 |
| `SysRoleService` | `auth/service/SysRoleService.java` | 角色 CRUD、角色分配 |
| `SysMenuService` | `auth/service/SysMenuService.java` | 菜单 CRUD、角色菜单分配 |
| `UserDomainService` | `service/UserDomainService.java` | CurrentUserResponse 构建、密码修改、dataScope 解析、权限检查 |
| `UserMasterDataService` | `service/UserMasterDataService.java` | 渠道下拉、招商下拉、组成员下拉 |
| `ColonelPartnerMasterDataService` | `service/ColonelPartnerMasterDataService.java` | 团长主数据下拉 |
| `UserPermissionCacheService` | `service/UserPermissionCacheService.java` | 权限缓存失效（组变更时） |
| `OperationLogService` | `service/OperationLogService.java` | 操作日志记录 |

### 3.3 Repository / Mapper 清单

| Mapper | 路径 |
| --- | --- |
| `SysUserMapper` | `mapper/SysUserMapper.java` |
| `SysRoleMapper` | `mapper/SysRoleMapper.java` |
| `SysMenuMapper` | `mapper/SysMenuMapper.java` |
| `SysUserRoleMapper` | `mapper/SysUserRoleMapper.java` |
| `SysRoleMenuMapper` | `mapper/SysRoleMenuMapper.java` |
| `OperationLogMapper` | `mapper/OperationLogMapper.java` |
| `SysDeptMapper` | `mapper/SysDeptMapper.java` |

### 3.4 Entity / DO / PO 清单

| Entity | 路径 | 对应表 |
| --- | --- | --- |
| `SysUser` | `entity/SysUser.java` | `sys_user` |
| `SysRole` | `entity/SysRole.java` | `sys_role` |
| `SysMenu` | `entity/SysMenu.java` | `sys_menu` |
| `SysDept` | `entity/SysDept.java` | `sys_dept` |
| `SysUserRole` | `entity/SysUserRole.java` | `sys_user_role` |
| `SysRoleMenu` | `entity/SysRoleMenu.java` | `sys_role_menu` |
| `OperationLog` | `entity/OperationLog.java` | `operation_log`（分区表） |

### 3.5 DTO / VO / Request / Response 清单

| 文件 | 路径 |
| --- | --- |
| `LoginRequest` | `auth/dto/LoginRequest.java` |
| `LoginResponse` | `auth/dto/LoginResponse.java` |
| `RefreshRequest` | `auth/dto/RefreshRequest.java` |
| `RefreshResponse` | `auth/dto/RefreshResponse.java` |
| `LogoutRequest` | `auth/dto/LogoutRequest.java` |
| `CurrentUserResponse` | `dto/user/CurrentUserResponse.java`（record） |
| `UserDataScopeResponse` | `dto/user/UserDataScopeResponse.java` |
| `ChangePasswordRequest` | `dto/user/ChangePasswordRequest.java` |
| `CheckPermissionRequest` | `dto/user/CheckPermissionRequest.java` |
| `CheckPermissionResponse` | `dto/user/CheckPermissionResponse.java` |
| `SysUserVO` | `vo/SysUserVO.java` |
| `SysRoleVO` | `vo/SysRoleVO.java` |

### 3.6 Security / Filter / Interceptor 清单

| 组件 | 路径 | 职责 |
| --- | --- | --- |
| `JwtAuthenticationFilter` | `security/JwtAuthenticationFilter.java` | JWT 解析、写入 `SecurityContextHolder`、设置 request attribute（userId/deptId/dataScope/roleCodes）、黑名单校验 |
| `JwtTokenProvider` | `security/JwtTokenProvider.java` | JWT 签发（access/refresh）、解析、Token hash 计算 |
| `DataScopeAspect` | `aspect/DataScopeAspect.java` | `@DataScope` 注解 AOP 拦截，向 MyBatis-Plus QueryWrapper 追加数据范围过滤 |
| `PendingActivationAccessPolicy` | `security/PendingActivationAccessPolicy.java` | 待激活用户访问控制策略 |
| `RuntimeExposurePolicy` | `config/RuntimeExposurePolicy.java` | 运行时暴露策略（哪些路径跳过认证） |
| `CustomMetaObjectHandler` | `config/CustomMetaObjectHandler.java` | MyBatis-Plus 自动填充（createBy/updateBy） |

### 3.7 Config 清单

| 文件 | 路径 |
| --- | --- |
| `SecurityConfig`（或等效 Spring Security 配置） | 需进一步确认 |
| `RuntimeExposurePolicy` | `config/RuntimeExposurePolicy.java` |
| `CustomMetaObjectHandler` | `config/CustomMetaObjectHandler.java` |

### 3.8 前端相关页面与 API client 清单

| 文件 | 路径 |
| --- | --- |
| `api/auth.ts` | 登录 `POST /auth/login`、登出 `POST /auth/logout` |
| `stores/auth.ts` | Pinia store，localStorage 持久化 token/userInfo，含 roleCodes/dataScope getter |
| `constants/rbac.ts` | 前端 ROLE_CODES 常量、`isAdminRole()`、`hasAccess()` |
| `views/sample/sample-permissions.ts` | 前端硬编码角色权限（`SAMPLE_EXPORT_ROLES`、`OPS_SHIPPING_TABS`） |
| `router/guard.ts` | 路由守卫（含角色权限判断） |
| `router/menuTree.ts` | 菜单树生成逻辑 |

---

## 4. 用户域数据库对象清单

> 代码和 migration 层面盘点，不执行数据库写操作。

### 4.1 用户表

- **表名**：`sys_user`
- **关键字段**：`id`（UUID）、`username`、`password`（bcrypt）、`real_name`、`dept_id`、`status`（0=禁用/1=正常/2=待激活）、`force_password_change`、`last_login_at`
- **migration**：`init-db.sql` 初始化

### 4.2 角色表

- **表名**：`sys_role`
- **关键字段**：`id`（UUID）、`role_code`（唯一）、`role_name`、`data_scope`（1/2/3）、`permissions`（JSON）、`menu_config`（JSON）、`status`
- **migration**：`init-db.sql` 初始化

### 4.3 用户角色关系表

- **表名**：`sys_user_role`
- **关键字段**：`user_id`（UUID）、`role_id`（UUID）
- **说明**：支持一人多角色（多行记录）

### 4.4 菜单表

- **表名**：`sys_menu`
- **关键字段**：`id`（UUID）、`menu_name`、`menu_type`（MENU/BUTTON/API）、`parent_id`（树形）、`path`、`component`、`permission_code`、`visible`、`status`
- **migration**：`alter-menu-permission-model.sql`

### 4.5 角色菜单关系表

- **表名**：`sys_role_menu`
- **关键字段**：`role_id`（UUID）、`menu_id`（UUID）
- **migration**：`alter-menu-permission-model.sql`

### 4.6 部门表

- **表名**：`sys_dept`
- **关键字段**：`id`（UUID）、`parent_id`、`dept_code`、`dept_name`、`dept_type`（recruiter/channel/department）、`leader_user_id`、`leader`
- **migration**：`migrate-sys-dept-dept-type.sql`

### 4.7 refresh token / token 黑名单相关

| 存储位置 | Key 格式 | 说明 |
| --- | --- | --- |
| Redis | `auth:refresh:{tokenHash}` | Refresh token 有效状态（hasKey = 已吊销） |
| Redis | `auth:blacklist:{tokenHash}` | Access token 黑名单（已登出） |
| Redis | `auth:login:fail:{normalizedUsername}` | 登录失败计数 |
| Redis | `auth:login:lock:{normalizedUsername}` | 登录锁定标记 |

### 4.8 操作日志表

- **表名**：`operation_log`（分区表，按月分区）
- **migration**：`alter-user-domain-v1-acceptance-20260523.sql` 等

---

## 5. 当前认证链路

### 5.1 登录入口

```
POST /api/auth/login
  ↓
AuthController.login(LoginRequest)
  ↓
AuthService.login()
  ├─ isLoginLocked()         ← 检查 Redis 锁定
  ├─ resolveLoginUser()      ← sys_userMapper 按 username 或 real_name 精确匹配
  ├─ matchesPassword()       ← BCrypt 比对
  ├─ sysRoleMapper.findByUserId(userId)  ← 查询用户所有角色
  ├─ dataScope = roles.stream().map(SysRole::getDataScope).max()  ← 取最大 dataScope
  ├─ OPS_STAFF / ADMIN → dataScope = 3（强制提升）
  ├─ jwtTokenProvider.generateAccessToken()  ← 签发 JWT
  ├─ jwtTokenProvider.generateRefreshToken()  ← 签发 Refresh Token
  ├─ sysUserMapper.updateById(lastLoginAt)   ← 更新最后登录时间
  └─ recordAuthEvent()        ← 记录操作日志
```

### 5.2 密码校验位置

`AuthService.login()` 第 170 行：
```java
if (!matchesPassword(request.getPassword(), user.getPassword())) {
```
调用 `PasswordEncoder.matches()`（BCrypt）。

### 5.3 access token 生成逻辑

`JwtTokenProvider.generateAccessToken()`：
- 签发 JWT（jjwt 库），包含 claims：`sub=userId`、`deptId`、`dataScope`、`roleCodes[]`、`username`、`pendingActivation`
- 过期时间：默认 7200 秒（2 小时）

### 5.4 refresh token 生成逻辑

`JwtTokenProvider.generateRefreshToken()`：
- 签发 JWT，仅含 `sub=userId`、`type=refresh`
- 过期时间：默认 604800 秒（7 天）
- 存储在 Redis，key = `auth:refresh:{tokenHash}`

### 5.5 token 校验 Filter

`JwtAuthenticationFilter.doFilterInternal()`：
1. `RuntimeExposurePolicy` 判断是否跳过认证
2. 提取 `Authorization: Bearer <token>`
3. `jwtTokenProvider.parseClaims(token)` 验证签名和过期
4. 验证 `type == "access"`（拒绝 refresh token 用于接口认证）
5. `authService.isTokenBlacklisted(tokenHash)` 查 Redis 黑名单
6. 提取 claims，写入 request attribute：`userId`、`deptId`、`dataScope`、`roleCodes`
7. `SecurityContextHolder.getContext().setAuthentication()`

### 5.6 logout 如何处理

```
POST /api/auth/logout(LogoutRequest)
  ↓
AuthService.logout()
  ├─ jwtTokenProvider.parseClaims(refreshToken)
  ├─ redisTemplate.hasKey(REDIS_REFRESH_PREFIX + tokenHash)  ← 检查是否已登出
  ├─ redisTemplate.opsForValue().set(REDIS_REFRESH_PREFIX + tokenHash, "revoked", ttl)
  │   ← Refresh Token 加入黑名单（TTL = 剩余有效期）
  └─ recordAuthEvent("登出成功")
```

### 5.7 access token 过期时当前行为

- 前端通过 `stores/auth.ts` 的 `accessTokenExpiresIn` 判断是否即将过期
- 主动调用 `POST /api/auth/refresh` 用 refreshToken 换取新 accessToken
- refreshToken 也过期 → 前端清除登录状态，跳转登录页
- 后端无主动推送机制

### 5.8 refresh token 吊销或黑名单机制

- Refresh Token 吊销：写入 Redis `auth:refresh:{tokenHash}` = "revoked"，TTL = 剩余有效期
- Access Token 黑名单：写入 Redis `auth:blacklist:{tokenHash}` = "revoked"（logout 时尽力吊销，依赖 TTL 自然过期）
- refreshToken 刷新时会将旧 refreshToken 吊销（仅允许一个有效 refreshToken）

---

## 6. 当前权限模型

### 6.1 角色如何存储

`sys_role` 表，`role_code` 唯一索引，`data_scope` 整数（1/2/3），`permissions` 和 `menu_config` 为 JSON 字段。

### 6.2 菜单权限如何存储

- `sys_menu` 表存储菜单树（`parent_id` 树形，`permission_code` 如 `talent:list`）
- `sys_role_menu` 关联表存储角色-菜单关系
- 角色 `menu_config` JSON 字段存储可见菜单配置（前端用）

### 6.3 操作权限如何存储

- `sys_role.permissions`（JSON）：如 `{ "talent:list": true, "sample:export": true }`
- `sys_menu.permission_code`（字符串）：如 `"talent:list"`
- `UserDomainService.checkPermission()` 检查 roleCodes 对应的 permissions Map 中是否有目标 permission

### 6.4 是否支持一人多角色

**支持**。`sys_user_role` 多行记录，`AuthService.login()` 和 `refreshToken()` 中：
```java
List<SysRole> roles = sysRoleMapper.findByUserId(userId);
int dataScope = roles.stream()
    .map(SysRole::getDataScope)
    .filter(scope -> scope != null && scope > 0)
    .max(Integer::compareTo)  // 取最大值
    .orElse(1);
```

### 6.5 多角色权限是否取并集

**部分**。`dataScope` 取多角色最大值（最宽），但 permissions 字段是 JSON 对象，非简单叠加。当前 `UserDomainService.checkPermission()` 查询时只检查当前用户主角色（从 JWT roleCodes 取第一个）的 permissions，**未做多角色权限并集**。

### 6.6 多角色 data_scope 是否取最宽

**是**。取 `max(dataScope)`，但 `admin` 和 `ops_staff` 强制提升为 3（ALL）。

### 6.7 前端菜单树如何获取

- 后端提供菜单 API（`SysMenuController`）
- 前端 `router/menuTree.ts` 按 roleCodes 调用后端 API 动态生成路由和菜单
- `stores/auth.ts` 的 `roleCodes` getter 用于前端路由守卫 `router/guard.ts`

### 6.8 按钮权限如何控制

- `UserDomainService.checkPermission()` 提供后端 API `POST /users/current/permissions/check`
- 前端 `constants/rbac.ts` 的 `hasAccess()` 仅做前端路由层面的粗粒度角色判断
- `views/sample/sample-permissions.ts` 前端硬编码角色权限列表（非从后端 permissions 字段读取）

---

## 7. 当前数据范围模型

### 7.1 是否有 self / group / all

**有**，但命名不直接对应：

| 枚举值 | 数字码 | DDD 术语 | 说明 |
| --- | --- | --- | --- |
| `PERSONAL` | 1 | self | 仅自己创建的数据 |
| `DEPT` | 2 | group | 本部门数据（按 `dept_id` 过滤） |
| `ALL` | 3 | all | 全部数据 |

### 7.2 管理员是否 all

**是**。`admin` 角色 `dataScope = 3`（ALL），`AuthService` 中硬编码：
```java
if (roleCodes.contains(RoleCodes.ADMIN)) {
    dataScope = 3;
}
```

### 7.3 组长是否 group

**是**。`biz_leader` 和 `channel_leader` 角色 `dataScope = 2`（DEPT）。

### 7.4 普通成员是否 self

**是**。`biz_staff`、`channel_staff` 角色 `dataScope = 1`（PERSONAL）。

### 7.5 group 成员如何解析

**仅通过 `dept_id`**。`DataScopeAspect` 对 PERSONAL：`userId` 过滤；对 DEPT：`deptId` 过滤。**不存在 biz_dept / channel_dept 的组别语义展开**，`dept` 既是"部门"也是"组"。

### 7.6 订单/寄样/商品/达人/业绩/分析是否已经接入 data_scope

| 领域 | data_scope 接入方式 | 状态 |
| --- | --- | --- |
| 订单域 | `@DataScope(userField = "creator_id")` 或类似注解 | 部分接入 |
| 寄样域 | 直接用 `currentUserId` 过滤 creator_id | 部分接入 |
| 商品域 | 活动商品按 `recruiter_user_id` 过滤 | 部分接入 |
| 达人域 | `TalentService` 手动判断 `ownedByCurrentUser` | 部分接入，有越权风险 |
| 业绩域 | `PerformanceAccessContext` 接受 dataScope 参数 | 部分接入 |
| 分析模块 | Dashboard API 调用时传入 dataScope | 部分接入 |

### 7.7 当前 data_scope 是在哪里处理

**双重实现**：

1. **AOP 拦截**：`DataScopeAspect` 拦截标注 `@DataScope` 的 Mapper 方法，在 SQL 执行前向 QueryWrapper 追加过滤条件
2. **Service 层硬编码**：`SysUserService.applyDataScopeFilter()` 手动在 wrapper 中追加条件（避免 AOP 双重注入）

存在两套实现，有不一致风险。

---

## 8. 用户域对外能力

### 8.1 CurrentUser

- **来源**：`JwtAuthenticationFilter` 从 JWT claims 解析，写入 request attribute
- **使用方**：`CurrentUserController` 的 `@RequestAttribute("userId")` 等注入
- **DDD 缺口**：无统一 `CurrentUser` record/value object，各 Controller 散落注入 userId/deptId/dataScope/roleCodes

### 8.2 PermissionContext

- **现状**：不存在 `PermissionContext` 类
- **当前实现**：`UserDomainService.checkPermission()` 从 JWT roleCodes 查 `sys_role.permissions` JSON 字段
- **DDD 缺口**：无统一 `PermissionContext`，权限检查分散

### 8.3 DataScopeResolver

- **现状**：不存在 `DataScopeResolver` 接口/类
- **当前实现**：`DataScopeAspect` + `SysUserService.applyDataScopeFilter()` 双重实现
- **DDD 缺口**：无统一 DataScopeResolver，越界风险

### 8.4 PermissionChecker

- **现状**：不存在 `PermissionChecker` 接口/类
- **当前实现**：`UserDomainService.checkPermission()`
- **DDD 缺口**：无统一 PermissionChecker

### 8.5 UserDomainFacade

- **现状**：不存在 `UserDomainFacade`
- **当前实现**：直接通过 Controller/Service 调用（跨域直接访问 Repository）
- **DDD 缺口**：跨域依赖无 Facade 隔离

### 8.6 listChannels / listRecruiters / listDepartments / listGroupMembers

- **listChannels**：`UserMasterDataService.listChannels()`，按 `CHANNEL_LEADER`/`CHANNEL_STAFF` 角色过滤
- **listRecruiters**：`UserMasterDataService.listRecruiters()`，按 `BIZ_LEADER`/`BIZ_STAFF` 角色过滤
- **listDepartments**：`UserMasterDataService.listDepartments()`，支持 roleCodes 过滤
- **listGroupMembers**：`UserMasterDataService.listGroupMembers()`，按部门 ID 查组成员

---

## 9. 与其他领域关系

### 9.1 订单域

- **调用方式**：直接查 `sys_user`、`sys_dept` 表
- **说明**：`OrderController` 或 `OrderService` 可能直接使用 currentUserId，未通过用户域 Facade

### 9.2 寄样域

- **调用方式**：`SampleService` 直接用 `currentUserId` 过滤
- **说明**：存在越权风险（见 10. DDD 越界风险）

### 9.3 商品域

- **调用方式**：通过 `recruiter_user_id` 和 `creator_id` 字段过滤
- **说明**：部分使用 data_scope，部分手动判断

### 9.4 达人域

- **调用方式**：`TalentService` 手动判断 `ownedByCurrentUser`
- **说明**：
```java
boolean ownedByCurrentUser = userId != null && activeClaims.stream()
    .anyMatch(claim -> claim.getCreatorId().equals(userId));
```

### 9.5 业绩域

- **调用方式**：`PerformanceAccessContext` 接收 dataScope 参数，传递到查询层
- **说明**：通过参数传递，未直接查用户表

### 9.6 分析模块

- **调用方式**：Dashboard API 传入 dataScope 参数
- **说明**：通过请求参数传递

---

## 10. DDD 越界风险

| 风险类型 | 具体位置 | 风险描述 |
| --- | --- | --- |
| Controller 写复杂业务规则 | 部分 Controller 可能有越权 | 需逐 Controller 审查（本次未全量审查） |
| 业务域直接查用户/角色/部门表 | 多个 Service | 订单、达人等域直接用 userId 过滤 creator_id，未通过用户域 Facade |
| 存在多个 data_scope 实现 | `DataScopeAspect` + `SysUserService.applyDataScopeFilter()` | 两套实现，存在不一致风险 |
| 权限判断散落在各业务 Service | `TalentService` | `ownedByCurrentUser` 逻辑散落在业务域 |
| 前端硬编码权限 | `views/sample/sample-permissions.ts` | `SAMPLE_EXPORT_ROLES` 硬编码角色列表，非从后端 permissions 读取 |
| 多角色 permissions 未取并集 | `UserDomainService.checkPermission()` | 只检查主角色 permissions，未合并多角色 |
| group 成员解析仅依赖 dept_id | `DataScopeAspect` | DEPT scope 只按 `dept_id` 过滤，不区分 biz_dept/channel_dept 组别语义 |
| 无 PermissionContext 统一抽象 | 各 Service 自行注入 userId | 跨域调用无类型安全上下文 |

---

## 11. 当前测试与验证

### 11.1 用户域单元测试

| 测试文件 | 覆盖范围 |
| --- | --- |
| `AuthControllerTest.java` | AuthController 登录/刷新/登出 |
| `AuthServiceTest.java` | AuthService 登录、刷新、登出逻辑 |
| `SysUserServiceTest.java` | 用户 CRUD、dataScope 过滤 |
| `SysRoleServiceTest.java` | 角色 CRUD、分配 |
| `SysMenuServiceTest.java` | 菜单 CRUD、角色菜单分配 |
| `CurrentUserControllerTest.java` | 当前用户接口 |
| `UserMasterDataControllerTest.java` | 主数据下拉接口 |
| `AuthDtoTest.java` | DTO 序列化 |
| `SysUserAssignRolesRequestJsonTest.java` | 角色分配请求 JSON |

**评估**：基础覆盖良好，但多角色 dataScope 取最大值边界场景覆盖不足。

### 11.2 认证测试

- `AuthServiceTest` 覆盖登录成功、失败、锁定、刷新、登出
- 缺少 refresh token 并发刷新测试

### 11.3 权限测试

- `UserDomainServiceTest`（如有）覆盖权限检查
- `RoleGuardAspectTest` 测试角色守卫切面
- 缺少多角色权限并集测试

### 11.4 RBAC 测试

- `SysRoleServiceTest` 覆盖角色 CRUD
- 缺少角色继承或权限叠加逻辑测试

### 11.5 菜单测试

- `SysMenuServiceTest` 覆盖菜单 CRUD
- 缺少菜单树动态生成测试

### 11.6 data_scope 测试

- `SysUserServiceTest` 部分覆盖
- 缺少 PERSONAL/DEPT/ALL 三级等效性测试
- 缺少 `DataScopeAspect` AOP 拦截测试

### 11.7 E2E / smoke 脚本

- `npm run e2e:real-pre:roles`（Playwright E2E 角色测试）
- `e2e:real-pre:p0:preflight`（real-pre P0 预检）

### 11.8 测试覆盖评估

当前测试**基本覆盖**主要路径，但对以下场景覆盖不足：

1. 多角色 dataScope 取最大值的边界（3 角色混合）
2. DEPT scope 下 dept_id 为 null 的降级行为
3. refresh token 并发刷新竞态
4. 前端路由守卫 `router/guard.ts` 与后端 `@DataScope` 的一致性
5. 越权负例（self 用户访问 group/all 数据）

---

## 12. U-1 结论

### 12.1 已符合 DDD 的部分

| 方面 | 现状 |
| --- | --- |
| 登录/登出/刷新完整认证链 | `AuthController` → `AuthService` → `JwtTokenProvider` → `Redis`，链路完整 |
| `@DataScope` AOP 拦截机制 | `DataScopeAspect` 实现，行级数据权限有基础设施 |
| DataScope 枚举（PERSONAL/DEPT/ALL） | `DataScope` 枚举定义清晰，与 doc 合同一致 |
| 用户域 Service 层职责清晰 | `UserDomainService`、`SysUserService`、`SysMenuService` 分工明确 |
| RoleCodes 常量集中管理 | `constant/RoleCodes.java` 定义 6 种预置角色 |
| 前端 RBAC 常量 | `constants/rbac.ts` 定义 `ROLE_CODES`、`hasAccess()`、`isAdminRole()` |
| 多角色支持 | `sys_user_role` 多行记录，dataScope 取最大值 |

### 12.2 需要轻微调整的部分

| 方面 | 现状 | 建议 |
| --- | --- | --- |
| 多角色 permissions 并集 | 仅检查主角色 permissions | U-4 PermissionChecker 统一时合并多角色 permissions |
| 前端 `sample-permissions.ts` 硬编码 | 角色列表写死 | 改为从后端 `/users/current/permissions/check` 读取 |
| 登录时 roleCodes 顺序 | `roles.stream().map(...).collect()` 不保证顺序 | 明确主角色定义或多角色时取最高权限角色为主 |

### 12.3 需要重构的部分

| 方面 | 当前问题 | 建议 |
| --- | --- | --- |
| CurrentUser 统一抽象 | 各 Controller 散落注入 userId/deptId/dataScope/roleCodes | U-3 实现 `CurrentUser` record 或 `@CurrentUser` 注解 |
| PermissionContext 统一 | 不存在 | U-4 实现 `PermissionChecker` 和 `PermissionContext` |
| DataScopeResolver 统一 | `DataScopeAspect` + `SysUserService` 双重实现 | U-5 收口为统一 `DataScopeResolver` |
| 跨域直接查用户表 | 业务域直接用 userId/deptId 过滤 | U-8 到 U-13 逐步改造业务域通过 Facade 调用 |
| UserDomainFacade 缺失 | 跨域依赖无统一出口 | U-7 实现 `UserDomainFacade` |

### 12.4 高风险部分

| 风险 | 描述 | 优先级 |
| --- | --- | --- |
| 越权风险：TalentService ownedByCurrentUser | `TalentService` 手动判断 creator_id 是否等于 currentUserId，与 `@DataScope` 机制不统一 | P0 |
| 双重 data_scope 实现 | `DataScopeAspect` 与 `SysUserService.applyDataScopeFilter` 两套实现，存在不一致风险 | P1 |
| 前端硬编码权限 | `views/sample/sample-permissions.ts` 硬编码角色列表，不从后端读取 | P1 |
| 无 PermissionContext 导致跨域调用散乱 | 业务域直接用 userId/deptId 构造查询条件，用户域上下文丢失 | P1 |

### 12.5 不建议当前改动的部分

| 部分 | 原因 |
| --- | --- |
| 重构 `sys_dept` 为 biz_dept/channel_dept 双表 | V1 以 dept_id 为准，组别语义暂不需要拆表 |
| 引入 Spring Security `@EnableMethodSecurity` 注解权限 | 当前以 `@DataScope` + 手动的 `checkPermission()` 为主，引入 annotation 会增加复杂度 |
| 前端 Pinia store 完全重写 | `stores/auth.ts` 功能完整，换代成本高 |

---

## 13. 下一步建议

### U-2：用户域表结构与模型对齐

1. 确认 `sys_user_role` 是否支持多主角色（当前支持多行，但应用中取最大值）
2. 确认 `sys_role.data_scope` 默认值与 doc 一致（默认 1 = PERSONAL）
3. 确认 `sys_menu.permission_code` 唯一性约束
4. 补全 `sys_dept.dept_type` 索引和迁移幂等性
5. 整理 `operation_log` 分区策略

**前置条件**：已读取 `docs/06-数据模型总表.md`、`docs/领域/用户域.md`

### U-3：CurrentUser / PermissionContext 统一

1. 设计 `CurrentUser` record/value object（包含 userId、deptId、dataScope、roleCodes）
2. 实现 `@CurrentUser` 注解替代散落的 `@RequestAttribute`
3. 设计 `PermissionContext` 类封装 permissions JSON 解析
4. 统一 `UserDomainService.getCurrentUser()` 返回 `CurrentUserResponse`（已存在，需统一注入方式）

### U-4：DataScopeResolver 统一

1. 消除 `DataScopeAspect` 与 `SysUserService.applyDataScopeFilter()` 双重实现
2. 设计 `DataScopeResolver` 接口
3. 统一 `DataScope.PERSONAL` → `creator_id = userId` 映射规则
4. 确认 DEPT scope 只按 `dept_id` 过滤（不展开 biz_dept/channel_dept）

### U-5：PermissionChecker 统一

1. 设计 `PermissionChecker` 接口
2. 实现多角色 permissions 并集合并逻辑
3. 统一 `checkPermission()` 方法签名
4. 移除 `UserDomainService` 中的手动权限检查

### U-6：（待定）

### U-7：UserDomainFacade 统一

1. 设计 `UserDomainFacade` 接口
2. 收口 `listChannels`、`listRecruiters`、`listGroupMembers` 为 Facade 方法
3. 替换跨域 Service 直接注入 `SysUserMapper` 的做法

### U-8 ~ U-13：业务域权限消费改造

- U-8：订单域列表权限消费改造
- U-9：寄样域列表权限消费改造
- U-10：商品域权限消费改造
- U-11：达人域权限消费改造
- U-12：业绩域权限消费改造
- U-13：分析模块权限消费改造

### U-14：操作日志和审计证据

### U-15：单元测试、E2E、越权负例补齐

---

### 是否建议进入 U-2

**建议进入 U-2**，理由：

1. U-1 盘点完成，用户域主链路清晰
2. 表结构基本对齐文档合同，`sys_user`、`sys_role`、`sys_menu`、`sys_dept` 等表结构稳定
3. 越界风险已识别，主要在 data_scope 双重实现和多角色权限合并，这些在 U-2/U-3 阶段修复更合适
4. U-2 是"表结构与模型对齐"，风险低，属于收口性质的轻度整理

**前置条件**：
- 读取 `docs/06-数据模型总表.md`
- 读取 `docs/领域/用户域.md`
- 执行只读 SQL 验证表结构与 doc 一致性（可选，Scope=docs 跳过）

---

## 附录：关键文件路径索引

```
backend/src/main/java/com/colonel/saas/
├── auth/
│   ├── controller/AuthController.java
│   ├── service/AuthService.java
│   ├── service/SysUserService.java
│   ├── service/SysRoleService.java
│   ├── service/SysMenuService.java
│   └── dto/{LoginRequest,LoginResponse,RefreshRequest,RefreshResponse,LogoutRequest}.java
├── controller/
│   ├── CurrentUserController.java
│   ├── SysUserController.java
│   ├── SysRoleController.java
│   ├── SysMenuController.java
│   └── UserMasterDataController.java
├── service/
│   ├── UserDomainService.java
│   ├── UserMasterDataService.java
│   ├── ColonelPartnerMasterDataService.java
│   └── UserPermissionCacheService.java
├── security/
│   ├── JwtAuthenticationFilter.java
│   ├── JwtTokenProvider.java
│   ├── PendingActivationAccessPolicy.java
│   └── RuntimeExposurePolicy.java
├── aspect/
│   └── DataScopeAspect.java
├── annotation/
│   └── DataScope.java
├── common/enums/
│   └── DataScope.java
├── entity/
│   ├── SysUser.java
│   ├── SysRole.java
│   ├── SysMenu.java
│   ├── SysDept.java
│   ├── SysUserRole.java
│   ├── SysRoleMenu.java
│   └── OperationLog.java
├── constant/
│   └── RoleCodes.java
└── dto/user/
    ├── CurrentUserResponse.java
    ├── UserDataScopeResponse.java
    ├── ChangePasswordRequest.java
    ├── CheckPermissionRequest.java
    └── CheckPermissionResponse.java

frontend/src/
├── api/auth.ts
├── stores/auth.ts
├── constants/rbac.ts
├── router/guard.ts
├── router/menuTree.ts
└── views/sample/sample-permissions.ts

backend/src/main/resources/db/
├── init-db.sql
├── alter-menu-permission-model.sql
├── alter-user-domain-v1-acceptance-20260523.sql
├── migrate-sys-dept-dept-type.sql
└── alter-ops-staff-data-scope-20260520.sql
```

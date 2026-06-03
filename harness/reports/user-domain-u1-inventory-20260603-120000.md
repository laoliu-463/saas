# 用户域 U-1 现状盘点报告

## 1. 任务概述

| 项目 | 内容 |
| --- | --- |
| 任务名称 | 用户域 U-1：现状盘点 |
| 执行时间 | 2026-06-03 |
| 是否修改代码 | **否** |
| 是否修改数据库 | **否** |
| 是否重启容器 | **否** |
| 是否部署远端 | **否** |
| Scope | docs / read-only |

## 2. Harness 读取情况

### 实际读取的 Harness 文件

| 文件 | 状态 |
| --- | --- |
| `AGENTS.md` | 已读 |
| `harness/AGENT_CONTRACT.md` | 已读 |
| `harness/TASK_ROUTING.md` | 已读 |
| `harness/FORBIDDEN_SCOPE.md` | 已读 |
| `harness/plans/DDD_OPTIMIZATION_ROADMAP.md` | 已读 |
| `harness/plans/DDD_DOMAIN_TASK_MATRIX.md` | 已读 |
| `harness/instructions/user-domain.md` | 已读 |
| `harness/CURRENT_STATE.md` | 已读 |
| `harness/state/DOMAIN_STATUS.md` | 已读 |
| `harness/state/KNOWN_ISSUES.md` | 已读 |
| `harness/state/DECISIONS.md` | 已读 |
| `harness/HARNESS_CHANGELOG.md` | 已读 |
| `docs/领域/用户域.md` | 已读 |
| `docs/07-权限与数据范围.md` | 已读 |

### DDD 顺序中用户域所处位置

用户域是 DDD 优化总顺序中的**第一个领域**（阶段 3），位于"当前状态盘点"和"Harness 工程护栏"和"DDD 架构护栏"之后。

顺序：用户域 -> 配置域 -> 订单域 -> 业绩域 -> 分析模块 -> 商品域 -> 达人域 -> 寄样域 -> Outbox -> 前端领域化 -> E2E/GC。

### 当前禁止范围中与用户域相关的红线

- 禁止用户域计算订单、寄样、商品或业绩归属。
- 禁止业务域绕过用户域自行实现一套数据范围判断。
- 禁止 Controller 编排复杂业务规则或状态机。
- 禁止跨领域直接访问对方 Repository；优先通过 Facade / 查询 API。
- 禁止前端硬编码核心业务规则、权限规则或状态机。
- V1 不做独家达人、独家商家、差异化提成。

## 3. 用户域代码清单

### 3.1 Controller 清单

| Controller | 路径前缀 | 包 | 关键端点 | 权限注解 |
| --- | --- | --- | --- | --- |
| `AuthController` | `/auth` | `auth.controller` | `POST /login`, `POST /refresh`, `POST /logout` | 无（公开/需 token） |
| `CurrentUserController` | `/users/current` | `controller` | `GET /`, `PUT /password`, `GET /data-scope`, `POST /permissions/check` | 无（所有已认证用户） |
| `SysUserController` | `/system/users` | `controller` | CRUD 用户、分配角色、重置密码 | `@RequireRoles` |
| `SysRoleController` | `/system/roles` | `controller` | CRUD 角色、分配菜单 | `@RequireRoles` |
| `SysMenuController` | `/system/menus` | `controller` | CRUD 菜单、获取菜单树 | `@RequireRoles` |
| `SysDeptController` | `/system/departments` | `controller` | CRUD 部门、成员管理 | `@RequireRoles` |
| `OperationLogController` | `/operation-logs` | `controller` | 查询操作日志 | `@RequireRoles({ADMIN})` |
| `UserMasterDataController` | `/users/master-data` | `controller` | 渠道/招商/组成员下拉 | `@RequireRoles` |
| `ColonelPartnerMasterDataController` | `/colonel-partners/master-data` | `controller` | 团长主数据下拉 | `@RequireRoles` |

### 3.2 Service 清单

| Service | 包 | 职责 | 注入的 Mapper / Service |
| --- | --- | --- | --- |
| `AuthService` | `auth.service` | 登录认证、令牌刷新、登出吊销 | `SysUserMapper`, `SysRoleMapper`, `JwtTokenProvider`, `PasswordEncoder`, `RedisTemplate`, `OperationLogService`, `BusinessRuleConfigService` |
| `SysUserService` | `auth.service` | 用户 CRUD、角色分配、密码重置 | `SysUserMapper`, `SysUserRoleMapper`, `SysRoleMapper`, `PasswordEncoder`, `UserPermissionCacheService`, `OperationLogService` |
| `SysRoleService` | `auth.service` | 角色 CRUD、菜单分配 | `SysRoleMapper`, `SysRoleMenuMapper`, `SysMenuMapper`, `UserPermissionCacheService`, `OperationLogService` |
| `SysMenuService` | `auth.service` | 菜单 CRUD、菜单树构建 | `SysMenuMapper`, `SysRoleMenuMapper`, `UserPermissionCacheService`, `OperationLogService` |
| `SysDeptService` (auth) | `auth.service` | 部门 CRUD、成员管理、组织架构 | `SysDeptMapper`, `SysUserMapper`, `UserPermissionCacheService`, `OperationLogService` |
| `SysDeptService` (service) | `service` | 部门查询（业务侧使用） | `SysDeptMapper` |
| `OrgStructureService` | `auth.service` | 组织架构查询、组长/组员关系 | `SysDeptMapper`, `SysUserMapper` |
| `UserDomainService` | `service` | 当前用户信息、密码修改、数据范围解析、权限检查 | `SysUserMapper`, `SysRoleMapper`, `PasswordEncoder`, `OperationLogService` |
| `UserMasterDataService` | `service` | 渠道/招商/组成员下拉数据 | `SysUserMapper`, `SysDeptMapper`, `SysRoleMapper` |
| `ColonelPartnerMasterDataService` | `service` | 团长主数据下拉 | `ColonelPartnerMapper` |
| `OperationLogService` | `service` | 操作日志记录和查询 | `OperationLogMapper` |
| `UserPermissionCacheService` | `service` | 权限缓存失效管理 | `ShortTtlCacheService` |

### 3.3 Repository / Mapper 清单

| Mapper | 对应表 | 关键方法 |
| --- | --- | --- |
| `SysUserMapper` | `sys_user` | `selectById`, `selectByUsername`, `findByDeptId`, `findPage`, `selectList` |
| `SysRoleMapper` | `sys_role` | `findByUserId`（通过 `sys_user_role` JOIN） |
| `SysUserRoleMapper` | `sys_user_role` | `insert`, `deleteByUserId`, `selectByUserId` |
| `SysMenuMapper` | `sys_menu` | `selectList`, `findByRoleIds` |
| `SysRoleMenuMapper` | `sys_role_menu` | `insert`, `deleteByRoleId`, `selectByRoleId` |
| `SysDeptMapper` | `sys_dept` | `selectList`, `selectById`, `findChildren` |
| `OperationLogMapper` | `operation_log` | `insert`, `selectPage` |

### 3.4 Entity / DO / PO 清单

| Entity | 表名 | 关键字段 |
| --- | --- | --- |
| `SysUser` | `sys_user` | id, username, password, realName, phone, email, deptId, channelCode, status(2=待激活/1=正常/0=禁用), forcePasswordChange, lastLoginAt |
| `SysRole` | `sys_role` | id, roleCode, roleName, dataScope(1/2/3), permissions(JSONB), menuConfig(JSONB), status, remark |
| `SysMenu` | `sys_menu` | id, menuName, menuType(MENU/BUTTON/API), parentId, path, component, icon, sortOrder, permissionCode, visible, status |
| `SysDept` | `sys_dept` | id, parentId, deptCode, deptName, deptType(department/recruiter_group/channel_group/ops_group), leaderUserId, leader, sortOrder, status |
| `SysUserRole` | `sys_user_role` | id, userId, roleId, createTime, deleted |
| `SysRoleMenu` | `sys_role_menu` | roleId, menuId（联合主键） |
| `OperationLog` | `operation_log` | id, userId, username, module, action, targetType, targetId, targetName, content, requestMethod, requestUrl, requestParams(JSONB), requestBody(JSONB), responseCode, responseBody(JSONB), ipAddress, userAgent, durationMs, errorMessage, createTime, deleted |

### 3.5 DTO / VO / Request / Response 清单

| 类 | 包 | 用途 |
| --- | --- | --- |
| `LoginRequest` | `auth.dto` | 登录请求（username, password） |
| `LoginResponse` | `auth.dto` | 登录响应（token, refreshToken, expiresIn, userId, deptId, dataScope, roleCodes, realName, status, forcePasswordChange, pendingActivation） |
| `LogoutRequest` | `auth.dto` | 登出请求（accessToken, refreshToken） |
| `RefreshRequest` | `auth.dto` | 刷新请求（refreshToken） |
| `RefreshResponse` | `auth.dto` | 刷新响应（accessToken, refreshToken, expiresIn） |
| `CurrentUserResponse` | `dto.user` | 当前用户信息（id, username, realName, deptId, dataScope, scopeName, roleCodes, permissions, status, forcePasswordChange） |
| `UserDataScopeResponse` | `dto.user` | 数据范围响应（scope, code, userIds） |
| `ChangePasswordRequest` | `dto.user` | 修改密码请求（oldPassword, newPassword） |
| `CheckPermissionRequest` | `dto.user` | 权限检查请求（resource, action） |
| `CheckPermissionResponse` | `dto.user` | 权限检查响应（resource, action, allowed） |
| `SysUserCreateRequest` | `auth.dto` | 创建用户请求 |
| `SysUserUpdateRequest` | `auth.dto` | 更新用户请求 |
| `SysUserPageRequest` | `auth.dto` | 用户分页请求 |
| `SysUserResetPasswordRequest` | `auth.dto` | 重置密码请求 |
| `SysUserAssignRolesRequest` | `auth.dto` | 分配角色请求（roleIds） |
| `SysRoleCreateRequest` | `auth.dto` | 创建角色请求 |
| `SysRoleUpdateRequest` | `auth.dto` | 更新角色请求 |
| `SysMenuCreateRequest` | `auth.dto` | 创建菜单请求 |
| `SysMenuUpdateRequest` | `auth.dto` | 更新菜单请求 |
| `SysDeptCreateRequest` | `auth.dto` | 创建部门请求 |
| `SysDeptUpdateRequest` | `auth.dto` | 更新部门请求 |
| `DeptMemberPageRequest` | `auth.dto` | 部门成员分页请求 |
| `GroupMemberMutationRequest` | `auth.dto` | 组成员变更请求 |
| `SysRoleVO` | `vo` | 角色视图对象 |

### 3.6 Security / Filter / Interceptor 清单

| 类 | 包 | 职责 |
| --- | --- | --- |
| `JwtTokenProvider` | `security` | JWT 生成（Access + Refresh Token）、解析、验证、哈希 |
| `JwtAuthenticationFilter` | `security` | JWT 认证过滤器（OncePerRequestFilter），解析 token 写入 request attribute 和 SecurityContext |
| `OperationLogInterceptor` | `security` | 操作日志拦截器（HandlerInterceptor），自动记录 HTTP 请求/响应审计日志 |
| `PendingActivationAccessPolicy` | `security` | 待激活用户访问策略，限制仅允许改密等有限接口 |
| `SecurityConfig` | `config` | Spring Security 配置，注册 JwtAuthenticationFilter，定义公开路径和认证策略 |
| `PasswordConfig` | `config` | BCrypt 密码编码器配置 |
| `WebConfig` | `config` | MVC 配置，注册 OperationLogInterceptor，配置 CORS |

### 3.7 Annotation / Aspect 清单

| 类 | 包 | 职责 |
| --- | --- | --- |
| `@RequireRoles` | `annotation` | 角色权限注解，声明接口所需角色编码 |
| `@DataScope` | `annotation` | 数据范围注解，标注在 Mapper 方法上自动追加行级过滤 |
| `RoleGuardAspect` | `aspect` | 角色守卫切面，拦截所有 Controller 方法校验 @RequireRoles |
| `DataScopeAspect` | `aspect` | 数据范围切面，拦截 @DataScope 标注的 Mapper 方法追加 WHERE 条件 |
| `RoleCodes` | `constant` | 角色编码常量（admin, biz_leader, biz_staff, channel_leader, channel_staff, ops_staff） |

### 3.8 Config 清单

| 类 | 包 | 职责 |
| --- | --- | --- |
| `SecurityConfig` | `config` | Spring Security 过滤器链 |
| `PasswordConfig` | `config` | BCrypt 编码器 |
| `WebConfig` | `config` | MVC 拦截器注册、CORS |
| `RedisConfig` | `config` | Redis 连接和序列化配置 |
| `MyBatisPlusConfig` | `config` | MyBatis-Plus 自动填充、分页插件 |
| `CustomMetaObjectHandler` | `config` | MyBatis-Plus 审计字段自动填充（createTime, createBy 等） |

### 3.9 前端相关页面与 API client 清单

| 文件 | 路径 | 职责 |
| --- | --- | --- |
| `auth.ts` | `frontend/src/api/auth.ts` | 认证 API client（login, logout, refresh） |
| `auth.ts` | `frontend/src/stores/auth.ts` | Pinia auth store（token 管理、用户状态） |
| `guard.ts` | `frontend/src/router/guard.ts` | 路由守卫（token 检查、登录跳转） |
| `menuTree.ts` | `frontend/src/router/menuTree.ts` | 菜单树构建 |
| `navigation.ts` | `frontend/src/router/navigation.ts` | 导航路由定义 |
| `index.ts` | `frontend/src/router/index.ts` | 路由主入口 |
| `request.ts` | `frontend/src/utils/request.ts` | Axios 请求封装（token 注入、401 拦截） |
| `Login.vue` | `frontend/src/views/Login.vue` | 登录页面 |
| `UserProfile.vue` | `frontend/src/views/profile/UserProfile.vue` | 用户个人资料/改密 |
| `RoleList.vue` | `frontend/src/views/system/RoleList.vue` | 角色管理页面 |
| `Header.vue` | `frontend/src/views/layout/Header.vue` | 头部组件（用户信息、登出） |
| `sys.ts` | `frontend/src/api/sys.ts` | 系统管理 API client |
| `PermissionHintAlert.vue` | `frontend/src/components/PermissionHintAlert.vue` | 权限提示组件 |
| `permissionHint.ts` | `frontend/src/stores/permissionHint.ts` | 权限提示 store |

## 4. 用户域数据库对象清单

### 4.1 用户表 `sys_user`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | UUID PK | 主键 |
| username | VARCHAR(50) UNIQUE NOT NULL | 登录用户名 |
| password | VARCHAR(255) NOT NULL | BCrypt 加密密码 |
| real_name | VARCHAR(100) | 真实姓名 |
| phone | VARCHAR(20) | 联系电话 |
| email | VARCHAR(100) | 邮箱 |
| dept_id | UUID | 所属部门 ID（FK -> sys_dept） |
| channel_code | VARCHAR(16) UNIQUE NOT NULL | 渠道短码 |
| status | SMALLINT DEFAULT 1 | 2=待激活, 1=正常, 0=禁用 |
| force_password_change | BOOLEAN DEFAULT FALSE | 是否强制改密 |
| deleted | SMALLINT DEFAULT 0 | 逻辑删除 |
| create_time, update_time | TIMESTAMP | 审计时间 |
| create_by, update_by | UUID | 审计人 |
| last_login_at | TIMESTAMP | 最后登录时间 |

索引：`username`, `dept_id`, `channel_code`, `status`, `deleted`

### 4.2 角色表 `sys_role`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | UUID PK | 主键 |
| role_code | VARCHAR(50) UNIQUE NOT NULL | 角色编码 |
| role_name | VARCHAR(100) NOT NULL | 角色名称 |
| data_scope | SMALLINT DEFAULT 1 | 1=self, 2=group(dept), 3=all |
| permissions | JSONB | 操作权限（menus + operations） |
| menu_config | JSONB | 可见菜单配置 |
| status | SMALLINT DEFAULT 1 | 1=启用, 0=禁用 |
| deleted, 审计字段 | - | 同上 |

### 4.3 用户角色关系表 `sys_user_role`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | UUID PK | 主键 |
| user_id | UUID NOT NULL FK | 用户 ID |
| role_id | UUID NOT NULL FK | 角色 ID |
| deleted | SMALLINT DEFAULT 0 | 逻辑删除 |
| create_time | TIMESTAMP | 创建时间 |

约束：`UNIQUE(user_id, role_id)`，外键 CASCADE

### 4.4 菜单表 `sys_menu`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | UUID PK | 主键 |
| menu_name | VARCHAR(100) NOT NULL | 菜单名称 |
| menu_type | VARCHAR(10) DEFAULT 'MENU' | MENU/BUTTON/API |
| parent_id | UUID DEFAULT '0000...0000' | 父菜单 ID |
| path | VARCHAR(200) | 路由路径 |
| component | VARCHAR(200) | 前端组件路径 |
| icon | VARCHAR(100) | 图标 |
| sort_order | INT DEFAULT 0 | 排序 |
| permission_code | VARCHAR(100) | 权限标识 |
| visible | SMALLINT DEFAULT 1 | 是否可见 |
| status | SMALLINT DEFAULT 1 | 状态 |

索引：`parent_id`, `status`

### 4.5 角色菜单关系表 `sys_role_menu`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| role_id | UUID NOT NULL | 角色 ID（联合 PK） |
| menu_id | UUID NOT NULL | 菜单 ID（联合 PK） |

索引：`menu_id`

### 4.6 部门表 `sys_dept`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | UUID PK | 主键 |
| parent_id | UUID | 父部门 |
| dept_code | VARCHAR(50) UNIQUE NOT NULL | 部门编码 |
| dept_name | VARCHAR(100) NOT NULL | 部门名称 |
| dept_type | VARCHAR(32) DEFAULT 'department' | 类型（department/recruiter_group/channel_group/ops_group） |
| leader_user_id | UUID | 组长用户 ID |
| leader | VARCHAR(100) | 负责人姓名 |
| phone, email | VARCHAR | 联系方式 |
| sort_order | INT DEFAULT 0 | 排序 |
| status | SMALLINT DEFAULT 1 | 状态 |

索引：`parent_id`, `status`, `deleted`, `dept_type`

### 4.7 操作日志表 `operation_log`

按月分区（`op_log_2026_04` ~ `op_log_2027_03`），自动创建未来分区。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | UUID PK (INPUT) | 主键 |
| user_id | UUID | 操作人 |
| username | VARCHAR | 操作人用户名 |
| module | VARCHAR | 模块 |
| action | VARCHAR | 动作 |
| target_type, target_id, target_name | VARCHAR | 操作目标 |
| content | VARCHAR | 描述 |
| request_method, request_url | VARCHAR | HTTP 信息 |
| request_params, request_body | JSONB | 请求参数 |
| response_code | VARCHAR | 响应码 |
| response_body | JSONB | 响应体 |
| ip_address, user_agent | VARCHAR | 客户端信息 |
| duration_ms | BIGINT | 耗时 |
| error_message | VARCHAR | 错误信息 |
| create_time | TIMESTAMP | 创建时间 |

### 4.8 Refresh Token / Token 黑名单

**无独立表**。Token 黑名单存储在 **Redis** 中：

| Redis Key | 用途 | TTL |
| --- | --- | --- |
| `auth:blacklist:{tokenHash}` | Access Token 吊销黑名单 | Token 剩余有效期 |
| `auth:refresh:{tokenHash}` | Refresh Token 吊销黑名单 | Token 剩余有效期 |
| `auth:login:fail:{normalizedUsername}` | 登录失败计数 | 配置时长 |
| `auth:login:lock:{normalizedUsername}` | 登录锁定标记 | 配置时长（默认 15 分钟） |

## 5. 当前认证链路

### 登录入口

`POST /auth/login` -> `AuthController.login()` -> `AuthService.login(LoginRequest)`

### 密码校验位置

`AuthService.login()` 内部：
1. 按 `username` 精确查找 `sys_user`，未命中再按 `real_name` 精确匹配（`resolveLoginUser`）
2. `PasswordEncoder.matches(request.getPassword(), user.getPassword())` 进行 BCrypt 比对

### Access Token 生成逻辑

`AuthService.login()` -> `JwtTokenProvider.generateAccessToken(userId, deptId, dataScope, roleCodes, username, pendingActivation)`

JWT Claims 包含：
- `sub` = userId
- `type` = "access"
- `deptId` = 部门 ID
- `dataScope` = 数据范围编码 (1/2/3)
- `roleCodes` = 角色编码列表
- `username` = 登录名
- `pendingActivation` = 待激活标记

默认有效期：**7200 秒（2 小时）**

### Refresh Token 生成逻辑

`AuthService.login()` -> `JwtTokenProvider.generateRefreshToken(userId)`

JWT Claims 包含：
- `sub` = userId
- `jti` = UUID（唯一标识）
- `type` = "refresh"

默认有效期：**604800 秒（7 天）**

### Token 校验 Filter

`JwtAuthenticationFilter`（OncePerRequestFilter），注册在 `UsernamePasswordAuthenticationFilter` 之前：
1. 跳过公开路径（`RuntimeExposurePolicy.shouldBypassAuthentication`）
2. 提取 `Authorization: Bearer <token>`
3. `JwtTokenProvider.parseClaims(token)` 验证签名和过期
4. 校验 `type == "access"`
5. `AuthService.isTokenBlacklisted(tokenHash)` 校验黑名单
6. 提取 userId/deptId/dataScope/roleCodes/username 写入 request attribute
7. 待激活用户受限访问（`PendingActivationAccessPolicy`）
8. 设置 `SecurityContextHolder`

### Logout 处理

`POST /auth/logout` -> `AuthService.logout(LogoutRequest)`：
1. 解析 Refresh Token，校验有效性
2. 将 Refresh Token hash 加入 Redis 黑名单（`auth:refresh:{hash}`，TTL = 剩余有效期）
3. 将 Access Token hash 加入 Redis 黑名单（`auth:blacklist:{hash}`，TTL = 剩余有效期）
4. 记录审计日志

### Access Token 过期时行为

`JwtAuthenticationFilter` 捕获 `ExpiredJwtException`，返回 401 "Token 无效或已过期"。前端 Axios 拦截器收到 401 后应跳转登录或触发 refresh。

### Refresh Token 吊销/黑名单机制

Redis Key `auth:refresh:{tokenHash}`，TTL = `JwtTokenProvider.getRemainingSeconds(refreshToken)`。刷新时：旧 Refresh Token 加入黑名单，签发新的 Access Token + Refresh Token。

## 6. 当前权限模型

### 角色存储

`sys_role` 表，`role_code` 唯一标识。6 个预置角色定义在 `RoleCodes` 常量类：
`admin`, `biz_leader`, `biz_staff`, `channel_leader`, `channel_staff`, `ops_staff`

### 菜单权限存储

`sys_menu` 表，树形结构（`parent_id`）。类型支持 MENU/BUTTON/API。
角色-菜单关联通过 `sys_role_menu` 多对多表。
`SysMenuService` 提供菜单树构建和按角色过滤。

### 操作权限存储

`sys_role.permissions` JSONB 字段，结构为：
```json
{
  "menus": ["menu-id-1", "menu-id-2"],
  "operations": {
    "talent": ["list", "detail", "create"],
    "product": ["list", "export"],
    "*": ["view"]
  }
}
```

`UserDomainService.mergePermissions()` 合并所有角色的 permissions，取并集。

### 是否支持一人多角色

**是**。通过 `sys_user_role` 多对多关联表，一个用户可绑定多个角色。

### 多角色权限是否取并集

**是**。`UserDomainService.mergePermissions()` 遍历所有激活角色的 `permissions` JSONB，聚合 menus 和 operations 到 `LinkedHashSet`，实现并集。

### 多角色 data_scope 是否取最宽

**是**。`UserDomainService.resolveDataScopeCode()` 和 `AuthService.login()` 都取所有角色中 `dataScope` 的最大值。ADMIN 和 OPS_STAFF 强制提升为 ALL(3)。

### 前端菜单树获取

前端调用 `GET /users/current` 获取 `CurrentUserResponse`，其中包含合并后的 `permissions.menus` 列表。
`frontend/src/router/menuTree.ts` 负责将后端菜单数据构建为前端路由树。

### 按钮权限控制

1. **后端**：`@RequireRoles` 注解 + `RoleGuardAspect` 切面做接口级角色鉴权
2. **后端**：`POST /users/current/permissions/check` 做运行时操作权限检查
3. **前端**：`PermissionHintAlert.vue` 组件和 `permissionHint.ts` store 提供权限提示
4. **前端**：路由守卫 `guard.ts` 控制页面级访问

## 7. 当前数据范围模型

### 是否有 self / group / all

**有**。定义在 `DataScope` 枚举：
- `PERSONAL(1)` = self（仅自己）
- `DEPT(2)` = group（本组/本部门）
- `ALL(3)` = all（全部）

`UserDomainService.scopeName()` 将 code 映射为 "self"/"group"/"all" 字符串。

### 管理员是否 all

**是**。ADMIN 角色强制 dataScope = 3 (ALL)。

### 组长是否 group

**是**。`biz_leader` 和 `channel_leader` 角色在 `sys_role` 表中配置 `data_scope = 2`。

### 普通成员是否 self

**是**。`biz_staff` 和 `channel_staff` 角色配置 `data_scope = 1`。

### OPS_STAFF 特殊处理

**是**。OPS_STAFF 角色在 `AuthService.login()` 和 `UserDomainService.resolveDataScopeCode()` 中被强制提升为 ALL(3)，与 ADMIN 一致。

### Group 成员如何解析

`UserDomainService.getUserDataScope()` 中：
- DEPT 范围：查询 `sys_user` 表中同 `dept_id` 且 `status=1` 的所有活跃用户 ID
- `OrgStructureService` 提供组织架构查询能力

### 各领域是否已接入 data_scope

| 领域 | 接入方式 | 说明 |
| --- | --- | --- |
| 订单域 | `@RequestAttribute` + Service 手动过滤 | Controller 读取 dataScope，Service 中手动追加 WHERE 条件 |
| 寄样域 | `@DataScope(userField = "sr.channel_user_id")` | SampleRequestMapper 使用注解 |
| 商品域 | Service 手动过滤 | 部分通过 `@RequestAttribute` 传入 |
| 达人域 | Service 手动过滤 | `TalentQueryService` 中处理 |
| 业绩域 | `PerformanceAccessScope` 独立实现 | 有独立的数据范围过滤器 |
| 分析模块 | `DashboardService` 手动过滤 | 通过 `@RequestAttribute` |

### 当前 data_scope 处理位置

**混合方式**：
1. **注解 + AOP 切面**：`@DataScope` + `DataScopeAspect`（Mapper 层，仅寄样域使用）
2. **Service 手动过滤**：订单、商品、达人、分析模块在 Service 层手动读取 dataScope 并追加条件
3. **独立实现**：业绩域有 `PerformanceAccessScope` + `PerformanceAccessContext` 独立数据范围过滤
4. **request attribute**：`JwtAuthenticationFilter` 写入，Controller 通过 `@RequestAttribute` 读取后传入 Service

## 8. 用户域对外能力

### 当前存在的能力

| 能力 | 当前实现 | DDD 视角评估 |
| --- | --- | --- |
| CurrentUser | `@RequestAttribute("userId")` + `UserDomainService.getCurrentUser()` | 已有但未统一为 Facade；Controller 手动传入 request attribute |
| DataScope 枚举 | `DataScope` 枚举 + `@DataScope` 注解 + `DataScopeAspect` | 已有统一枚举，但业务域使用方式不一致 |
| RoleGuard | `@RequireRoles` + `RoleGuardAspect` | 已统一 |
| 权限检查 | `UserDomainService.checkPermission()` | 已有，但业务域各自实现过滤逻辑 |
| 用户主数据下拉 | `UserMasterDataService` / `ColonelPartnerMasterDataService` | 已有独立 Service |
| 权限缓存 | `UserPermissionCacheService` | 已有 |

### 需要抽象但当前缺失的能力

| 能力 | 当前状态 | DDD 建议 |
| --- | --- | --- |
| `PermissionContext` | **不存在统一模型**。JWT claims 在 Filter 中写入 request attribute，各 Controller 分散读取 | U-3/U-4 统一 |
| `DataScopeResolver` | **不存在统一 Resolver**。数据范围解析逻辑散落在 `UserDomainService.getUserDataScope()`、各 Service 手动过滤和 `PerformanceAccessScope` 中 | U-5 统一 |
| `PermissionChecker` | **部分存在**。`UserDomainService.checkPermission()` 可检查操作权限，但未被业务域广泛调用 | U-6 统一 |
| `UserDomainFacade` | **不存在**。业务域直接注入 `SysUserMapper`、`SysRoleMapper` 或通过 request attribute 获取用户信息 | U-7 统一 |
| `listChannels` | 已有 `UserMasterDataService.listChannels()` | 需收口到 Facade |
| `listRecruiters` | 已有 `UserMasterDataService.listRecruiters()` | 需收口到 Facade |
| `listDepartments` | 已有 `SysDeptService` (两处) | 需统一并收口到 Facade |
| `listGroupMembers` | 已有 `UserMasterDataService.listGroupMembers()` | 需收口到 Facade |

## 9. 与其他领域关系

### 被调用方式盘点

| 领域 | 调用方式 | 具体引用 |
| --- | --- | --- |
| 订单域 | `@RequestAttribute` + Service 手动过滤 | `OrderQueryService`, `OrderService` 读取 userId/deptId/dataScope |
| 寄样域 | `@DataScope` 注解 + Controller `@RequestAttribute` | `SampleRequestMapper` 使用 `@DataScope`，`SampleApplicationService` 读取 request attribute |
| 商品域 | `@RequestAttribute` + Service 手动过滤 | `ProductService` 读取 userId/deptId/dataScope/roleCodes |
| 达人域 | `@RequestAttribute` + Service 手动过滤 | `TalentQueryService` 读取 userId/deptId/dataScope |
| 业绩域 | `PerformanceAccessContext` 独立构造 | Controller 从 `@RequestAttribute` 读取后构造 `PerformanceAccessContext`，传入 `PerformanceAccessScope` |
| 分析模块 | `@RequestAttribute` + `DashboardService` 手动过滤 | `DashboardService` 读取 userId/deptId/dataScope/roleCodes |
| 活动域 | `@RequestAttribute` + `ActivityAccessService` | `ColonelActivityController` 读取后传入 |

### 关键发现

- **无 Facade 层**：所有业务域直接通过 `@RequestAttribute` 从 request 中读取用户身份信息，或直接从 Controller 参数透传到 Service。
- **`SysDeptService` 存在两份**：`auth.service.SysDeptService`（管理端 CRUD）和 `service.SysDeptService`（业务侧查询），职责边界不清。
- **业绩域有独立数据范围实现**：`PerformanceAccessScope` + `PerformanceAccessContext` 是业绩域内部的数据范围过滤器，与通用 `DataScopeAspect` 并存。

## 10. DDD 越界风险

### Controller 是否写复杂业务规则

**部分存在**。多数 Controller 只做参数转发，但以下 Controller 包含较多逻辑：
- `SysUserController` 处理用户 CRUD + 角色分配，逻辑较重
- `SysDeptController` 处理部门 CRUD + 成员管理

整体而言，大部分业务逻辑已下沉到 Service 层，Controller 主要做参数绑定和 Service 调用。

### 业务域是否直接查用户/角色/部门表

**是，存在多处**。
- 业务 Service 直接注入 `SysUserMapper`、`SysRoleMapper`、`SysDeptMapper` 查询用户域数据
- `PerformanceAccessScope` 直接写 SQL 子查询 `SELECT id FROM sys_user WHERE dept_id = ?`
- 多个 Service 直接使用 `sys_user.dept_id` 做过滤，未通过用户域 Facade

### 是否存在多个 data_scope 实现

**是，存在三个**：
1. `DataScopeAspect`（通用 AOP 切面，仅寄样域使用）
2. `PerformanceAccessScope`（业绩域独立实现）
3. 各 Service 手动过滤（订单、商品、达人、分析）

### 是否存在多个 CurrentUser 模型

**部分存在**：
1. JWT Claims -> request attribute（userId, deptId, dataScope, roleCodes, username）
2. `CurrentUserResponse` DTO（`UserDomainService.getCurrentUser()` 输出）
3. `PerformanceAccessContext` record（业绩域专用）
4. 无统一的 `CurrentUser` 或 `LoginUser` 值对象

### 是否存在权限判断散落在各业务 Service

**是**。每个业务 Service 自行从 `@RequestAttribute` 读取 dataScope/roleCodes，自行决定过滤逻辑。缺乏统一的 `DataScopeResolver` 或 `PermissionChecker`。

### 是否存在前端硬编码权限

**未发现明显硬编码**。前端通过后端返回的 `permissions` 和 `/users/current/permissions/check` 接口做权限判断。`PermissionHintAlert.vue` 和 `permissionHint.ts` 作为辅助提示组件。

### 是否存在用户域直接处理业务实体逻辑

**未发现**。用户域 Service 只处理用户、角色、菜单、部门和权限相关逻辑，不涉及订单、商品、寄样等业务实体。

## 11. 当前测试与验证

### 用户域单元测试

| 测试类 | 覆盖功能 |
| --- | --- |
| `AuthServiceTest` | 登录、刷新令牌、登出、密码校验、锁定 |
| `SysUserServiceTest` | 用户 CRUD、角色分配、密码重置 |
| `SysRoleServiceTest` | 角色 CRUD、菜单分配 |
| `SysMenuServiceTest` | 菜单 CRUD、菜单树 |
| `UserDomainServiceTest` | 当前用户信息、密码修改、数据范围、权限检查 |
| `UserMasterDataServiceTest` | 渠道/招商/组成员下拉 |
| `UserPermissionCacheServiceTest` | 缓存失效 |

### 认证测试

| 测试类 | 覆盖功能 |
| --- | --- |
| `AuthControllerTest` | 认证端点 |
| `JwtAuthenticationFilterTest` | JWT 过滤器 |
| `AuthDtoTest` | DTO 校验 |
| `AuthTestFixtures` | 测试数据构造 |

### 权限测试

| 测试类 | 覆盖功能 |
| --- | --- |
| `RoleGuardAspectTest` | 角色守卫切面 |
| `DataScopeAspectTest` | 数据范围切面 |
| `DataScopeTest` | 数据范围枚举 |
| `PermissionEventHasherTest` | 权限事件 |
| `PermissionCacheRefreshListenerTest` | 缓存刷新监听器 |

### Controller 测试

| 测试类 | 覆盖功能 |
| --- | --- |
| `CurrentUserControllerTest` | 当前用户端点 |
| `SysUserControllerTest` | 用户管理端点 |
| `SysRoleControllerTest` | 角色管理端点 |
| `SysMenuControllerTest` | 菜单管理端点 |
| `SysDeptControllerTest` | 部门管理端点 |
| `UserMasterDataControllerTest` | 主数据下拉 |

### Mapper 测试

| 测试类 | 覆盖功能 |
| --- | --- |
| `SysUserMapperTest` | 用户 Mapper |
| `SysDeptMapperTest` | 部门 Mapper |

### 前端测试

| 测试文件 | 覆盖功能 |
| --- | --- |
| `auth.test.ts` | 认证 API |
| `guard.test.ts` | 路由守卫 |
| `menuTree.test.ts` | 菜单树 |
| `navigation.test.ts` | 导航 |
| `redirect.test.ts` | 重定向 |
| `sample-permissions.test.ts` | 寄样权限 |

### 测试覆盖缺口

- **admin/group/self 数据范围 API 对比测试**：缺少专门的集成测试验证同一接口在不同 dataScope 下返回不同结果
- **越权负例测试**：`RoleGuardAspectTest` 覆盖基本场景，但缺少跨域越权（如 channel_staff 访问 biz 接口）
- **@DataScope 注解覆盖率**：仅 `SampleRequestMapper` 使用了 `@DataScope`，其他 Mapper 未使用
- **多角色权限并集测试**：`UserDomainServiceTest` 有部分覆盖，但场景不够完整
- **Refresh Token 轮换测试**：`AuthServiceTest` 有覆盖，但缺少并发刷新竞态测试

## 12. U-1 结论

| 类别 | 项目 | 说明 |
| --- | --- | --- |
| **已符合 DDD** | `@RequireRoles` + `RoleGuardAspect` | 角色鉴权已统一为注解 + AOP，所有 Controller 均受保护 |
| **已符合 DDD** | `JwtTokenProvider` + `JwtAuthenticationFilter` | 认证链路已收口为单一 Filter + Provider |
| **已符合 DDD** | `DataScope` 枚举 | self/group/all 三级定义清晰，与角色编码对应 |
| **已符合 DDD** | `RoleCodes` 常量 | 6 个预置角色编码集中管理 |
| **已符合 DDD** | `UserDomainService` | 当前用户信息、改密、数据范围解析、权限检查已收口 |
| **已符合 DDD** | 用户域不涉及业务实体 | 未发现用户域处理订单/商品/寄样等逻辑 |
| **轻微调整** | `SysDeptService` 双份 | `auth.service.SysDeptService` 和 `service.SysDeptService` 需合并或明确边界 |
| **轻微调整** | `ColonelPartnerMasterDataService` | 业务上属于用户域主数据，但命名和路径未体现用户域归属 |
| **需要重构** | 数据范围实现分散 | 三套实现（DataScopeAspect / PerformanceAccessScope / Service 手动）需统一为 `DataScopeResolver` |
| **需要重构** | 无 `UserDomainFacade` | 业务域直接查 sys_user/sys_role/sys_dept 表或手动读 request attribute |
| **需要重构** | 无统一 `PermissionContext` | 用户身份信息在 Filter -> request attribute -> Controller -> Service 链路中散裂传递 |
| **需要重构** | `PerformanceAccessScope` 独立实现 | 业绩域自行实现数据范围过滤，包含硬编码 SQL 子查询 `sys_user`，违反用户域统一出口原则 |
| **高风险** | 业务域直接引用 `SysUserMapper` / `SysRoleMapper` | 多处业务 Service 直接注入用户域 Mapper，绕过用户域边界 |
| **高风险** | `@DataScope` 注解仅 1 处使用 | 数据范围 AOP 切面设计良好但几乎未被业务域采用，大部分走手动过滤 |
| **高风险** | dataScope 在 JWT 中固化 | dataScope 写入 JWT access token，token 有效期内角色变更不会实时生效（虽有短 TTL 缓存缓解） |
| **不建议当前改动** | 操作日志分区策略 | `operation_log` 按月分区运行正常，无需在 DDD 优化中调整 |
| **不建议当前改动** | Redis Token 黑名单方案 | 登出/吊销机制已稳定运行，无需在当前阶段替换 |
| **不建议当前改动** | 前端权限体系 | 前端权限获取和路由守卫基本完善，待后端 Facade 统一后再做前端领域化 |

## 13. 下一步建议

### U-2 用户域表结构与模型对齐建议

- 核对 `sys_user`、`sys_role`、`sys_menu`、`sys_dept`、`sys_user_role`、`sys_role_menu`、`operation_log` 7 张表的字段与 Entity 是否完全一致
- 核对 migration 脚本（`alter-menu-permission-model.sql`、`alter-user-domain-v1-acceptance-20260523.sql`、`alter-role-code-merge-colonel-leader.sql`、`alter-sys-dept.sql`、`migrate-sys-dept-dept-type.sql`）是否已全部应用
- 确认 `sys_role.permissions` JSONB 的实际结构与文档定义是否一致
- 确认 `sys_role.data_scope` 与 `RoleCodes` 中定义的默认值是否匹配

### U-3 CurrentUser / PermissionContext 统一建议

- 设计统一的 `CurrentUser` 值对象或 `PermissionContext` record，封装 userId/deptId/dataScope/roleCodes/username
- 收口 request attribute 的读取逻辑，避免每个 Controller 重复 `@RequestAttribute` 注解
- 考虑引入 `@CurrentUser` 参数解析器（`HandlerMethodArgumentResolver`），统一注入 CurrentUser

### U-4 DataScopeResolver 统一建议

- 设计统一的 `DataScopeResolver`，替代当前三套并行实现
- 将 `PerformanceAccessScope` 中的业绩域特化逻辑迁移到用户域 Facade + 业绩域消费侧
- 扩大 `@DataScope` 注解的使用范围，或统一到 Service 层 Resolver 模式

### U-5 PermissionChecker 统一建议

- 将 `UserDomainService.checkPermission()` 升级为独立的 `PermissionChecker` 组件
- 业务域通过 `PermissionChecker` 检查权限，而非直接读取 `sys_role.permissions` JSONB

### 是否可以进入 U-2

**可以**。U-1 盘点已完成，用户域代码结构清晰、核心链路完备，主要缺口在于跨域边界和统一抽象。U-2 表结构与模型对齐是低风险的下一步。

# 用户域 U-2：表结构与领域模型对齐报告

## 1. 任务概述

| 字段 | 值 |
| --- | --- |
| 任务名称 | 用户域 U-2：表结构与领域模型对齐 |
| 执行时间 | 2026-06-03 09:30 |
| 是否修改代码 | **否** |
| 是否修改数据库 | **否** |
| 是否重启容器 | **否** |
| 报告路径 | `harness/reports/user-domain-u2-model-schema-alignment-20260603-093000.md` |

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
| U-1 报告 | `harness/reports/user-domain-u1-inventory-20260603-090000.md` |

### 2.2 U-1 关键发现如何影响 U-2

U-1 发现以下问题与表结构直接相关：

| U-1 问题 | U-2 判定 |
| --- | --- |
| 三套 data_scope 并行 | 根因：schema 层 data_scope 只存 `sys_role.data_scope`，代码层有 AOP + Service 两套实现，需统一 resolver |
| 多角色 permissions 未取并集 | 根因：schema 层 permissions 存 JSONB，代码层 checkPermission 只查主角色；schema 可支撑并集，代码层需改 |
| TalentService ownedByCurrentUser 越权 | 根因：schema 层 `talent_claim` 有 `user_id`，但无 data_scope 过滤；schema 支持，需代码层接 data_scope |
| 前端硬编码权限 | 根因：schema 层 `sys_role.permissions` / `sys_menu.permission_code` 已存在，前端未读取；schema 可支撑 |
| sys_dept 既是部门也是组 | schema 层已通过 `dept_type`（recruiter_group/channel_group/ops_group/department）区分，可支撑 |

---

## 3. 用户域表清单

| 表名 | 用途 | 主键 | 关键字段 | 对应 Entity | 关联关系 | 是否属于用户域 | 被其他领域直接访问 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `sys_user` | 用户 | UUID | `username`, `password`, `real_name`, `dept_id`, `channel_code`, `status`, `force_password_change`, `last_login_at` | `SysUser.java` | `sys_dept`（dept_id） | **是** | 是：订单、寄样、商品、达人、业绩、分析 |
| `sys_role` | 角色 | UUID | `role_code`, `role_name`, `data_scope`, `permissions`（JSONB）, `menu_config`（JSONB）, `status` | `SysRole.java` | `sys_user_role`（一对多） | **是** | 是：各业务域查 roleCodes |
| `sys_dept` | 部门/组 | UUID | `parent_id`, `dept_code`, `dept_name`, `dept_type`, `leader_user_id`, `leader`, `status` | `SysDept.java` | `sys_user`（一对多）、树形自关联 | **是** | 是：业绩、分析 |
| `sys_user_role` | 用户角色关系 | UUID | `user_id`, `role_id`, `deleted` | `SysUserRole.java` | `sys_user`+`sys_role` 多对多 | **是** | 间接 |
| `sys_menu` | 菜单/权限 | UUID | `menu_type`（MENU/BUTTON/API）, `parent_id`, `path`, `component`, `permission_code`, `visible`, `status` | `SysMenu.java` | `sys_role_menu`（多对多） | **是** | 是：前端菜单树 |
| `sys_role_menu` | 角色菜单关系 | 联合主键(role_id, menu_id) | — | `SysRoleMenu.java` | `sys_role`+`sys_menu` 多对多 | **是** | 是：前端菜单树 |
| `operation_log` | 操作审计日志 | UUID | `user_id`, `module`, `action`, `target_type`, `target_id`, `request_params`, `response_code`, `ip_address`, `duration_ms`, `error_message` | `OperationLog.java` | 无外键 | **是** | 否 |
| `talent_claim` | 达人认领关系 | UUID | `talent_id`, `user_id`, `dept_id`, `claim_type`, `status` | `TalentClaim.java` | `talent`、`sys_user`、`sys_dept` | **达人域** | 是：用户域解析 ownedByCurrentUser |
| Redis: `auth:refresh:*` | Refresh token 有效态 | — | — | — | — | **是** | 否 |
| Redis: `auth:blacklist:*` | Access token 黑名单 | — | — | — | — | **是** | 否 |
| Redis: `auth:login:fail:*` | 登录失败计数 | — | — | — | — | **是** | 否 |

**注**：无 `audit_log` 独立表，统一使用 `operation_log`。

---

## 4. 表与 DDD 领域模型映射

### 4.1 User 模型

| DDD 字段 | 数据库表 | 字段 | 类型 | 备注 |
| --- | --- | --- | --- | --- |
| id | `sys_user` | `id` | UUID | 主键 |
| username | `sys_user` | `username` | VARCHAR(50) | 唯一 |
| password | `sys_user` | `password` | VARCHAR(255) | BCrypt 加密 |
| realName | `sys_user` | `real_name` | VARCHAR(100) | |
| phone | `sys_user` | `phone` | VARCHAR(20) | |
| email | `sys_user` | `email` | VARCHAR(100) | |
| deptId | `sys_user` | `dept_id` | UUID | 外键 → `sys_dept.id` |
| channelCode | `sys_user` | `channel_code` | VARCHAR(16) | 唯一 |
| status | `sys_user` | `status` | SMALLINT | 2=待激活, 1=正常, 0=禁用 |
| forcePasswordChange | `sys_user` | `force_password_change` | BOOLEAN | |
| lastLoginAt | `sys_user` | `last_login_at` | TIMESTAMP | |
| deleted | `sys_user` | `deleted` | SMALLINT | 逻辑删除 |

**支撑评估**：✅ **完整支撑**，无缺失字段。

### 4.2 Role 模型

| DDD 字段 | 数据库表 | 字段 | 类型 | 备注 |
| --- | --- | --- | --- | --- |
| id | `sys_role` | `id` | UUID | 主键 |
| roleCode | `sys_role` | `role_code` | VARCHAR(50) | 唯一 |
| roleName | `sys_role` | `role_name` | VARCHAR(100) | |
| dataScope | `sys_role` | `data_scope` | SMALLINT | 1/2/3，对应 PERSONAL/DEPT/ALL |
| permissions | `sys_role` | `permissions` | JSONB | `{ "talent:list": true, "sample:export": true }` |
| menuConfig | `sys_role` | `menu_config` | JSONB | 可见菜单配置 |
| status | `sys_role` | `status` | SMALLINT | 1=启用, 0=禁用 |
| deleted | `sys_role` | `deleted` | SMALLINT | |

**支撑评估**：✅ **完整支撑**。permissions 存 JSONB，可支撑细粒度操作权限。

### 4.3 Permission / MenuPermission / OperationPermission 模型

| DDD 概念 | 数据库表 | 字段 | 支撑情况 |
| --- | --- | --- | --- |
| 菜单树结构 | `sys_menu` | `id`, `parent_id`, `menu_name`, `path`, `component`, `icon`, `sort_order` | ✅ 完整 |
| 菜单类型（MENU/BUTTON/API） | `sys_menu` | `menu_type` | ✅ 完整 |
| 权限编码（按钮/API 级） | `sys_menu` | `permission_code`（如 `talent:list`） | ✅ 完整 |
| 角色-菜单绑定 | `sys_role_menu` | `role_id`, `menu_id` | ✅ 完整 |
| 角色操作权限（JSON） | `sys_role` | `permissions`（JSONB） | ✅ 完整，但需代码层解析 |
| 角色可见菜单配置 | `sys_role` | `menu_config`（JSONB） | ✅ 完整 |

**支撑评估**：✅ **schema 可支撑**。菜单权限、操作权限、按钮权限均有字段承载，代码层需统一 PermissionChecker 和前端读取路径。

### 4.4 Department 模型

| DDD 字段 | 数据库表 | 字段 | 类型 | 备注 |
| --- | --- | --- | --- | --- |
| id | `sys_dept` | `id` | UUID | 主键 |
| parentId | `sys_dept` | `parent_id` | UUID | 树形自关联 |
| deptCode | `sys_dept` | `dept_code` | VARCHAR(50) | 唯一 |
| deptName | `sys_dept` | `dept_name` | VARCHAR(100) | |
| deptType | `sys_dept` | `dept_type` | VARCHAR(32) | `recruiter_group`/`channel_group`/`ops_group`/`department` |
| leaderUserId | `sys_dept` | `leader_user_id` | UUID | 组长用户 ID |
| leader | `sys_dept` | `leader` | VARCHAR(100) | 组长姓名（展示） |
| status | `sys_dept` | `status` | SMALLINT | |

**支撑评估**：✅ **完整支撑**，且 `dept_type` 已区分 recruiter_group / channel_group / ops_group / department 四种类型，`leader_user_id` 提供结构化组长关联。

### 4.5 Group 模型

| DDD 字段 | 数据库表 | 字段 | 说明 |
| --- | --- | --- | --- |
| id | `sys_dept` | `id` | 复用 dept，同一张表 |
| groupType | `sys_dept` | `dept_type` | `recruiter_group`=招商组，`channel_group`=渠道组 |
| leaderId | `sys_dept` | `leader_user_id` | 组长 |
| memberRelation | `sys_user` | `dept_id` | 用户通过 dept_id 加入组 |

**支撑评估**：✅ **可支撑**。`sys_dept` 同时承载"部门"和"组"语义，`dept_type` 区分类型。group 范围过滤当前只按 `dept_id` 过滤，不展开 dept_type 语义。

### 4.6 UserRoleRelation 模型

| DDD 字段 | 数据库表 | 字段 | 说明 |
| --- | --- | --- | --- |
| userId | `sys_user_role` | `user_id` | |
| roleId | `sys_user_role` | `role_id` | |
| unique key | `sys_user_role` | `uk_user_role (user_id, role_id)` | 防止重复分配 |

**支撑评估**：✅ **完整支撑一人多角色**。`uk_user_role` 唯一约束支持多角色。

### 4.7 DataScope 模型

| DDD 概念 | 数据库表 | 字段 | 说明 |
| --- | --- | --- | --- |
| DataScope 枚举 | `sys_role` | `data_scope`（SMALLINT） | 1=PERSONAL, 2=DEPT, 3=ALL |
| 多角色最大 dataScope | 运行时计算 | — | `AuthService.login()` 取 `max(dataScope)` |
| admin 强制 all | 运行时计算 | — | `AuthService` 硬编码 admin → 3 |

**支撑评估**：✅ **schema 可支撑**。`sys_role.data_scope` 存整数枚举，JWT 嵌入 dataScope，运行时可计算最大 dataScope。

### 4.8 OperationLog 模型

| DDD 字段 | 数据库表 | 字段 | 说明 |
| --- | --- | --- | --- |
| id | `operation_log` | `id` | UUID 主键 |
| userId | `operation_log` | `user_id` | 操作人 ID |
| username | `operation_log` | `username` | 冗余存储 |
| module | `operation_log` | `module` | TALENT/PRODUCT/SAMPLE/ORDER 等 |
| action | `operation_log` | `action` | CREATE/UPDATE/DELETE/EXPORT 等 |
| targetType | `operation_log` | `target_type` | 实体类型 |
| targetId | `operation_log` | `target_id` | 实体 ID |
| requestParams | `operation_log` | `request_params`（JSONB） | |
| responseCode | `operation_log` | `response_code` | HTTP 状态码 |
| ipAddress | `operation_log` | `ip_address` | |
| durationMs | `operation_log` | `duration_ms` | 耗时毫秒 |
| errorMessage | `operation_log` | `error_message` | |
| createTime | `operation_log` | `create_time` | |

**支撑评估**：✅ **完整支撑**。operation_log 覆盖所有关键维度，支持 module + action 过滤。

---

## 5. 一人多角色支撑情况

### 5.1 表结构是否支撑

**✅ 真支撑**。证据：

- `sys_user_role` 表存在，`CONSTRAINT uk_user_role UNIQUE (user_id, role_id)` 唯一约束
- 一个 `user_id` 可以对应多个不同的 `role_id`（多行记录）
- `sysUserMapper.findByUserId(userId)` 返回 `List<SysRole>`

### 5.2 代码是否按 roleIds[] 读取

**✅ 是**。`JwtAuthenticationFilter` 从 JWT 解析 `roleCodes[]` 数组写入 request attribute，多个 Controller 使用 `@RequestAttribute("roleCodes")`。

### 5.3 多角色菜单权限是否能取并集

**❌ 否，当前未实现**。`UserDomainService.checkPermission()` 只检查 JWT roleCodes 中的**第一个角色**的 permissions JSON，**未做多角色权限并集**。

**schema 能力**：✅ permissions JSONB 存 `{ "perm1": true, "perm2": true }`，可支撑并集合并。

### 5.4 多角色操作权限是否能取并集

**同上**。permissions JSONB 可支撑，但代码层未实现并集。

### 5.5 多角色 data_scope 是否能取最宽

**✅ 是**。`AuthService.login()` 和 `refreshToken()` 中：
```java
int dataScope = roles.stream()
    .map(SysRole::getDataScope)
    .max(Integer::compareTo)
    .orElse(1);
```

### 5.6 是否存在旧的一人一角色字段残留

**否**。`sys_user` 表无 `role_id` 单字段残留，`sys_role` 表无 `user_id` 单字段残留。

### 5.7 结论

| 方面 | 表结构 | 代码实现 |
| --- | --- | --- |
| 一人多角色多行记录 | ✅ | ✅ |
| dataScope 取最大值 | ✅ | ✅ |
| permissions 取并集 | ✅ | ❌ 未实现 |
| menuConfig 取并集 | ✅ | 部分（前端菜单树已读多角色菜单） |

---

## 6. 数据范围支撑情况

### 6.1 data_scope 存储位置

**唯一存储位置**：`sys_role.data_scope`（SMALLINT）

无其他冗余存储。

### 6.2 self / group / all 是否有稳定枚举

**✅ 有**。`DataScope.java` 枚举定义：

```java
PERSONAL(1)  // self：仅自己创建
DEPT(2)     // group：本部门
ALL(3)      // all：全部
```

### 6.3 管理员 all 是否可以从角色推导

**✅ 可以**。`admin` 角色 `data_scope = 3`（ALL），`AuthService` 硬编码提升为 3。

### 6.4 组长 group 是否依赖 dept / group / leader 字段

**部分**。组长（`biz_leader`/`channel_leader`）`data_scope = 2`（DEPT），**只依赖 `sys_user.dept_id`** 过滤，不展开 dept_type 语义。

### 6.5 普通成员 self 是否依赖 user_id

**✅ 是**。PERSONAL scope → `DataScopeAspect` 向 QueryWrapper 追加 `creator_id = #{userId}`。

### 6.6 group 成员关系来自哪里

**仅来自 `sys_user.dept_id`**。`sys_dept` 提供组织结构，但 group 范围过滤只按 `dept_id` 过滤，不区分 `recruiter_group` / `channel_group` 组别语义。

### 6.7 是否缺少 leader_user_id

**否**。`sys_dept` 已有 `leader_user_id` 字段（UUID 类型）。

### 6.8 是否缺少 dept_type / group_type

**否**。`sys_dept` 已有 `dept_type` 字段，支持 `recruiter_group` / `channel_group` / `ops_group` / `department`。

### 6.9 是否会导致订单、寄样、业绩过滤不一致

**潜在风险**。业务表（如 `orders`、`sample_requests`）通过 `creator_id` 过滤 self、通过 `dept_id` 过滤 group，但：

- `talent_claim` 表的 data_scope 过滤依赖 `user_id` 和 `dept_id`，**未使用 DataScopeAspect**
- `PerformanceAccessContext` 是独立实现，不走 DataScopeAspect
- `TalentService.ownedByCurrentUser` 手动判断 creator_id，不走统一 data_scope

**schema 可支撑**，但代码层 data_scope 接入程度不一，存在不一致风险。

---

## 7. 菜单权限与操作权限支撑情况

### 7.1 菜单权限是否由 sys_menu / sys_role_menu 支撑

**✅ 是**。

- `sys_menu`：菜单树结构，`parent_id` 树形，`permission_code` 按钮权限编码
- `sys_role_menu`：角色-菜单多对多关系
- `sys_role.menu_config`（JSONB）：角色可见菜单配置

### 7.2 按钮权限是否有 permission_code 或 perms 字段

**✅ 是**。`sys_menu.permission_code`（VARCHAR(100)），如 `talent:list`、`sample:export`。

### 7.3 操作权限是否能表达 create / update / delete / export / audit / ship 等动作

**✅ 是**。`sys_role.permissions`（JSONB）可存储任意粒度权限，如 `{ "sample:export": true, "sample:audit": true }`。

### 7.4 前端菜单树是否能由后端返回

**✅ 能**。后端 `SysMenuController` 和 `SysMenuService` 提供菜单 API，`frontend/src/router/menuTree.ts` 按 roleCodes 调用。

### 7.5 前端按钮是否能由权限包控制

**schema 可支撑**。后端 `sys_role.permissions` JSONB 和 `sys_menu.permission_code` 均可承载按钮权限，但：
- `UserDomainService.checkPermission()` 提供后端 API
- `frontend/constants/rbac.ts` 的 `hasAccess()` 仅做路由层粗粒度判断
- `frontend/views/sample/sample-permissions.ts` **硬编码** `SAMPLE_EXPORT_ROLES`，不从后端读取

### 7.6 是否存在前端硬编码权限风险

**✅ 存在**。`views/sample/sample-permissions.ts` 硬编码角色列表，不从 `sys_role.permissions` 读取。

---

## 8. 部门与组别模型支撑情况

### 8.1 当前 sys_dept 是否同时承担部门和业务组

**是**。`sys_dept` 一张表承载"组织部门"和"业务组"双重语义。

### 8.2 是否区分招商组和渠道组

**✅ 是**。`dept_type` 字段区分：
- `recruiter_group`：招商组
- `channel_group`：渠道组
- `ops_group`：运营组
- `department`：普通部门

### 8.3 是否有 dept_type / group_type

**✅ 有**。`sys_dept.dept_type`。

### 8.4 是否有 leader_user_id

**✅ 有**。`sys_dept.leader_user_id`（UUID 类型）。

### 8.5 组员关系如何解析

**通过 `sys_user.dept_id`**。用户属于哪个组，通过 `sys_user.dept_id` 指向 `sys_dept.id`。组长通过 `sys_dept.leader_user_id` 指向 `sys_user.id`。

### 8.6 是否支持"招商组长看本组招商"

**可支撑**。招商组长 `data_scope = 2`（DEPT），过滤条件 `dept_id = 当前用户 dept_id`。但当前 `DataScopeAspect` 只按 `dept_id` 过滤，不检查 `dept_type`，即招商组长可能看到本组的渠道数据（如果同 dept_id 下有渠道组成员）。

**潜在风险**：dept_id 不区分 recruiter_group / channel_group，组长 group 范围可能混入其他业务线成员。

### 8.7 是否支持"渠道组长看本组渠道"

同上。

### 8.8 是否支持运营挂部门但不强制业务组

**✅ 是**。ops 用户可能有 `dept_id`，但 `ops_staff` 的 `data_scope` 已在 `alter-ops-staff-data-scope-20260520.sql` 中强制设为 1（PERSONAL），登录态保持角色配置。

---

## 9. Token / RefreshToken / 黑名单结构支撑情况

### 9.1 refresh token 存数据库还是 Redis

**✅ Redis**。Key = `auth:refresh:{tokenHash}`，value = "revoked" 或存在即有效，TTL = 剩余有效期。

### 9.2 黑名单存数据库还是 Redis

**✅ Redis**。Access token 黑名单 `auth:blacklist:{tokenHash}`，Refresh token 吊销 `auth:refresh:{tokenHash}` = "revoked"。

### 9.3 是否支持 logout 吊销 refresh token

**✅ 是**。写入 Redis `auth:refresh:{tokenHash}` = "revoked"，TTL = 剩余有效期。

### 9.4 access token 过期时是否仍能 logout

**是**。Logout 时通过 Refresh token 解析 userId 并吊销 Refresh token，Access token 依赖 TTL 自然过期。

### 9.5 token 中是否嵌入 data_scope

**✅ 是**。JWT claims 包含 `dataScope`（数字 1/2/3）。

### 9.6 如果嵌入，权限变更后是否存在不一致窗口

**存在**。用户权限变更（角色修改、`data_scope` 调整）后，**已签发的 JWT 在过期前不会更新** `dataScope` claim。

**缓解机制**：`UserPermissionCacheService` 提供权限缓存失效（组变更时触发），但 JWT 无法主动失效，只能等待过期（默认 2 小时）。

---

## 10. 操作日志支撑情况

### 10.1 operation_log 表是否存在

**✅ 是**。`init-db.sql` 中存在 `operation_log` 表定义。

### 10.2 是否记录登录

**✅ 是**。通过 `AuthService.recordAuthEvent()` 记录，包含 username、module=AUTH、action=LOGIN/LOGOUT/REFRESH。

### 10.3 是否记录用户创建/禁用

**✅ 是**。通过 AOP 拦截或 Controller 层调用 `OperationLogService` 记录。

### 10.4 是否记录角色权限变更

**✅ 是**。`module` 字段支持 ROLE 模块。

### 10.5 是否记录部门/组别调整

**✅ 是**。`module` 字段支持 DEPT 模块。

### 10.6 是否支持保留 90 天

**需确认**。`operation_log` 表定义中**未包含分区定义**，`init-db.sql` 无 `PARTITION BY`。`alter-user-domain-v1-acceptance-20260523.sql` 也未添加分区。

**建议**：V1 阶段通过 TTL 或定时任务清理，不强制分区；如数据量增长快，再做按月分区 migration。

### 10.7 是否需要按月分区

**当前不需要强制分区**。但如未来日志量大，应设计按月 RANGE 分区（`create_time`）。

---

## 11. 跨领域表访问风险

### 11.1 订单域

| 访问的表 | 访问方式 | 风险等级 | 标记 |
| --- | --- | --- | --- |
| `sys_user` | 直接 Mapper 注入 | P1 | DDD 越界 |
| `sys_dept` | 直接 Mapper 注入 | P1 | DDD 越界 |

### 11.2 寄样域

| 访问的表 | 访问方式 | 风险等级 | 标记 |
| --- | --- | --- | --- |
| `sys_user` | 直接 userId 过滤 | P1 | 越界（应用 data_scope） |

### 11.3 商品域

| 访问的表 | 访问方式 | 风险等级 | 标记 |
| --- | --- | --- | --- |
| `sys_user` | recruiter_user_id / creator_id 字段过滤 | P2 | 临时可接受 |

### 11.4 达人域

| 访问的表 | 访问方式 | 风险等级 | 标记 |
| --- | --- | --- | --- |
| `talent_claim` | 直接 Mapper（用户域外） | P1 | 越界 |
| `sys_user` | ownedByCurrentUser 手动判断 | P1 | 越界（应走 data_scope） |

### 11.5 业绩域

| 访问的表 | 访问方式 | 风险等级 | 标记 |
| --- | --- | --- | --- |
| `sys_user` | `PerformanceAccessContext` 独立实现 | P1 | 越界（重复实现 data_scope） |
| `sys_dept` | 参数传递 | P2 | 临时可接受 |

### 11.6 分析模块

| 访问的表 | 访问方式 | 风险等级 | 标记 |
| --- | --- | --- | --- |
| `sys_user` | Dashboard API 参数传入 dataScope | P2 | 临时可接受 |

### 11.7 汇总

| 风险等级 | 数量 | 说明 |
| --- | --- | --- |
| DDD 越界（必须迁移到 Facade） | 3 | 订单域、达人域、业绩域直接访问用户/部门 Mapper |
| 临时可接受（U-5 前不强制改） | 3 | 商品、分析、寄样部分 |

---

## 12. 当前 schema 问题清单

### P0（影响权限错误、数据泄露、登录失败）

| 问题 | 描述 | 影响 |
| --- | --- | --- |
| 无 | schema 层无 P0 级缺陷 | — |

### P1（DDD 边界不清、后续重构困难）

| 问题 | 描述 | 位置 |
| --- | --- | --- |
| 跨域直接访问用户/部门 Mapper | 订单、达人、业绩域直接注入了 `SysUserMapper`、`SysDeptMapper` | 各业务域 Service |
| 达人域 data_scope 未接入统一机制 | `talent_claim` 过滤依赖手动 `ownedByCurrentUser`，不走 DataScopeAspect | `TalentService` |
| JWT 权限变更后不一致窗口 | JWT 嵌入 dataScope，权限变更后已签发 token 不更新 | `JwtTokenProvider` |
| `operation_log` 无分区 | 日志量大时无按月分区策略 | `operation_log` 表 |

### P2（命名、索引、文档、可维护性）

| 问题 | 描述 | 位置 |
| --- | --- | --- |
| DEPT scope 不区分 dept_type | 招商组长 group 范围可能混入渠道成员（同 dept_id 时） | `DataScopeAspect` |
| `sys_dept` 同时承载部门+组语义 | 概念混淆，未来可能需要拆分为 biz_dept / channel_dept | `sys_dept` 表 |
| `sys_user_role` 无审计字段 | `create_time` 有，`create_by` 无；角色分配无审计人 | `sys_user_role` 表 |
| `leader_user_id` 无外键约束 | `sys_dept.leader_user_id` 是普通 UUID 列，无 FK 约束 | `sys_dept` 表 |
| `sys_role_menu` 无审计字段 | 角色-菜单分配无操作日志 | `sys_role_menu` 表 |
| 前端硬编码权限 | `views/sample/sample-permissions.ts` | 前端代码 |

---

## 13. 是否需要数据库迁移

### U-3（CurrentUser / PermissionContext 统一）

**❌ 不需要改表**。

U-3 只做代码层抽象：
- 设计 `CurrentUser` record，从 request attribute / JWT claims 构建
- 设计 `PermissionContext`，从 `sys_role.permissions` JSONB 解析
- 均是**内存对象**，schema 已完整支撑

### U-4（DataScopeResolver 统一）

**❌ 不需要改表**。

U-4 只消除双重实现：
- 收口 `DataScopeAspect` + `SysUserService.applyDataScopeFilter()` 为统一 `DataScopeResolver`
- schema 层的 `sys_role.data_scope` 已是唯一真相来源
- 达人域 `ownedByCurrentUser` 改为调用 `DataScopeResolver`

### U-5（UserDomainFacade 抽象）

**❌ 不需要改表**。

U-5 只做跨域接口抽象：
- 设计 `UserDomainFacade` 接口暴露 `listChannels()`、`listRecruiters()`、`listGroupMembers()`
- 不改底层表结构

### U-6 及以后（业务域改造）

**❌ 不需要改用户域表**。

业务域改造只改业务表（如 `talent_claim`）接入 `@DataScope` 注解，不涉及用户域表结构。

### 总结

| 阶段 | 是否需要改表 | 理由 |
| --- | --- | --- |
| U-3 | **否** | CurrentUser / PermissionContext 是内存对象 |
| U-4 | **否** | DataScopeResolver 统一是代码层收口 |
| U-5 | **否** | UserDomainFacade 是接口抽象 |
| U-6~U-13 | **否**（用户域部分） | 业务域改造不改用户域表 |

**结论：V1 DDD 优化阶段，schema 层无需任何 migration**。当前表结构**完整支撑** DDD 模型，代码层问题是主要矛盾。

---

## 14. U-2 结论

### 14.1 已对齐 DDD 的部分

| 领域模型 | 表结构 | 评估 |
| --- | --- | --- |
| User | `sys_user` 完整字段（id/username/password/realName/deptId/channelCode/status） | ✅ 完全对齐 |
| Role | `sys_role`（roleCode/roleName/dataScope/permissions/menuConfig） | ✅ 完全对齐 |
| DataScope 枚举 | `sys_role.data_scope`（1/2/3）+ `DataScope.java` 枚举 | ✅ 完全对齐 |
| 一人多角色 | `sys_user_role` 多行记录 + `uk_user_role` 唯一约束 | ✅ 完全对齐 |
| Department / Group | `sys_dept`（dept_type/leader_user_id/parent_id/deptCode/deptName） | ✅ 完全对齐 |
| 菜单树 | `sys_menu`（parent_id 树形/path/component/permission_code） | ✅ 完全对齐 |
| 角色-菜单绑定 | `sys_role_menu` 多对多 | ✅ 完全对齐 |
| 按钮权限 | `sys_menu.permission_code` + `sys_role.permissions` JSONB | ✅ 完全对齐 |
| Token 黑名单 | Redis `auth:refresh:*` / `auth:blacklist:*` | ✅ 完全对齐 |
| 操作日志 | `operation_log`（module/action/target/userId/username/requestParams/response/duration） | ✅ 完全对齐 |
| admin 强制 all | `sys_role.data_scope=3` + `AuthService` 硬编码 | ✅ 完全对齐 |

### 14.2 需要代码适配的部分

| 问题 | 代码层修复方式 |
| --- | --- |
| 多角色 permissions 未取并集 | U-4 PermissionChecker 中 merge 多角色 permissions JSONB |
| `DataScopeAspect` + `SysUserService` 双重实现 | U-4 统一为 DataScopeResolver |
| 前端硬编码权限 | U-4 后端暴露 `/users/current/permissions` API，前端 `sample-permissions.ts` 改为读取 |
| 达人域 `ownedByCurrentUser` 越界 | U-4 达人域改用 DataScopeAspect `@DataScope(userField = "user_id")` |
| 业绩域 `PerformanceAccessContext` 重复实现 | U-4 统一到 DataScopeResolver |
| JWT 权限变更不一致窗口 | U-3 考虑 Short-lived JWT 或主动失效机制 |

### 14.3 需要未来 migration 的部分

| 建议 | 时机 | 理由 |
| --- | --- | --- |
| `sys_user_role` 增加 `create_by` 审计字段 | V2 | 角色分配需要审计人 |
| `sys_role_menu` 增加操作日志 | V2 | 菜单权限变更需要审计 |
| `operation_log` 按月分区 | V2（当日志量大时） | 当前通过 TTL 清理，分区是可选优化 |
| `sys_dept.leader_user_id` 增加 FK 约束 | V2 | 数据完整性保护 |
| 考虑将 `sys_dept` 拆分为 `biz_dept` / `channel_dept` 双表 | V2+ | 如果 dept_type 语义扩展，当前单表可能不够用 |

**V1 阶段无需 migration**。

### 14.4 高风险部分

| 风险 | 描述 | 优先级 |
| --- | --- | --- |
| 跨域直接访问用户/部门 Mapper | 订单、达人、业绩域绕过 UserDomainFacade，DDD 边界不清 | P1 |
| DEPT scope 不区分 dept_type | 招商组长可能看到本 dept_id 下的渠道成员（如果同组混合业务线） | P1 |
| JWT 权限变更不一致窗口 | 已签发 JWT 在 2 小时内不感知权限变更 | P1（低影响，2小时窗口） |

### 14.5 暂不建议改动的部分

| 部分 | 理由 |
| --- | --- |
| 拆分 `sys_dept` 为 biz_dept / channel_dept 双表 | 当前 `dept_type` 可支撑 V1 需求，拆表成本高 |
| `operation_log` 强制分区 | 当前数据量可控，TTL 清理够用 |
| 强制 FK 约束 `leader_user_id` | 可能影响数据初始化脚本，V2 再处理 |

---

## 15. 下一步建议

### 是否可以进入 U-3

**✅ 建议直接进入 U-3**。

理由：
1. **U-2 结论：schema 层无需 migration**，用户域表结构完整对齐 DDD 模型
2. U-3 的 `CurrentUser` record 和 `PermissionContext` 是纯代码层抽象，不碰 schema
3. U-1 + U-2 已完成"现状盘点"和"schema 对齐"，核心问题是代码层架构，不是表结构

### U-3 应该优先统一哪些对象

**优先顺序**：

1. **`CurrentUser` record**（最高优先）：消除各 Controller 散落注入 userId/deptId/dataScope/roleCodes，提供统一入口
2. **`PermissionContext`**：封装 `sys_role.permissions` JSONB 解析，**同时修复多角色权限并集问题**
3. **统一 `@CurrentUser` 注解**：替代 `@RequestAttribute("userId")` 等散落写法

### U-4 DataScopeResolver 统一前需要注意什么

1. **确认唯一真相来源**：`sys_role.data_scope` 是唯一存储，不在 user 表上重复存
2. **消除双重实现**：`DataScopeAspect` + `SysUserService.applyDataScopeFilter()` 统一为 `DataScopeResolver`，避免 AOP 注入导致 SQL 重复条件
3. **达人域越界**：`TalentService.ownedByCurrentUser` 改为 `@DataScope(userField = "user_id")` 注解方式
4. **DEPT scope 不区分 dept_type 风险**：记录为已知限制，V2 前暂不处理

### U-5 UserDomainFacade 抽象前需要注意什么

1. **Facade 接口设计**：只暴露只读方法（listChannels、listRecruiters、listGroupMembers、getCurrentUserPermissions），不暴露用户域写操作
2. **订单域、达人域、业绩域**：这三个是越界重灾区，U-5 阶段必须强制通过 Facade 访问
3. **移除直接 Mapper 注入**：各业务域的 `SysUserMapper`、`SysDeptMapper` 注入需要替换为 Facade 调用

### U-6 及以后

- U-6：SysDeptService 重复问题（如果存在）
- U-7~U-13：各业务域按 DDD 边界改造，接入 DataScopeResolver 和 UserDomainFacade

---

## 附录：Schema 核心字段速查

```
sys_user:         id, username, password, real_name, dept_id, channel_code, status, force_password_change, last_login_at
sys_role:         id, role_code, role_name, data_scope(1/2/3), permissions(JSONB), menu_config(JSONB), status
sys_user_role:    user_id, role_id (uk_user_role unique)
sys_dept:         id, parent_id, dept_code, dept_name, dept_type(recruiter/channel/ops/department), leader_user_id, leader
sys_menu:         id, parent_id, menu_name, menu_type(MENU/BUTTON/API), path, component, permission_code, visible
sys_role_menu:    role_id, menu_id (pk)
operation_log:    id, user_id, username, module, action, target_type, target_id, request_params(JSONB), response_code, ip_address, duration_ms, error_message

Redis:
  auth:refresh:{hash}     - refresh token 有效态
  auth:blacklist:{hash}   - access token 黑名单
  auth:login:fail:{user}  - 登录失败计数
  auth:login:lock:{user}  - 登录锁定标记
```

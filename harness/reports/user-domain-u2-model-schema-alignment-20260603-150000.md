# 用户域 U-2：表结构与领域模型对齐报告

## 1. 任务概述

| 项目 | 内容 |
| --- | --- |
| 任务名称 | 用户域 U-2：表结构与领域模型对齐 |
| 执行时间 | 2026-06-03 |
| 是否修改代码 | **否** |
| 是否修改数据库 | **否** |
| 是否修改 SQL migration | **否** |
| 是否重启容器 | **否** |
| 是否部署远端 | **否** |
| Scope | docs / read-only |

## 2. Harness 读取情况

### 实际读取的 Harness 文件

| 文件 | 状态 |
| --- | --- |
| `AGENTS.md` | 已读 |
| `CLAUDE.md` | 已读 |
| `harness/AGENT_CONTRACT.md` | 已读（前序会话） |
| `harness/TASK_ROUTING.md` | 已读（前序会话） |
| `harness/FORBIDDEN_SCOPE.md` | 已读（前序会话） |
| `harness/plans/DDD_OPTIMIZATION_ROADMAP.md` | 已读（前序会话） |
| `harness/plans/DDD_DOMAIN_TASK_MATRIX.md` | 已读 |
| `harness/instructions/user-domain.md` | 已读 |
| `harness/CURRENT_STATE.md` | 已读 |
| `harness/state/DOMAIN_STATUS.md` | 已读 |
| `harness/state/KNOWN_ISSUES.md` | 已读（前序会话） |
| `harness/state/DECISIONS.md` | 已读（前序会话） |
| `harness/HARNESS_CHANGELOG.md` | 已读 |
| `harness/reports/user-domain-u1-inventory-20260603-120000.md` | 已读 |
| `docs/领域/用户域.md` | 已读 |
| `docs/07-权限与数据范围.md` | 已读 |

### U-1 报告路径

`harness/reports/user-domain-u1-inventory-20260603-120000.md`

### U-1 关键发现如何影响 U-2

| U-1 发现 | 对 U-2 的影响 |
| --- | --- |
| 三套 data_scope 并行实现 | U-2 需确认 data_scope 存储位置和表支撑能力是否统一 |
| 缺少 UserDomainFacade | U-2 需标记跨域直接访问 Mapper 为"必须在 U-5 后迁移" |
| SysDeptService 双份 | U-2 需确认 sys_dept 表是否有足够的字段支撑两种使用场景 |
| PerformanceAccessScope 越界 | U-2 需标记 PerformanceAccessScope 中的 raw SQL 子查询为 DDD 越界 |
| dataScope 嵌入 JWT | U-2 需判断 Token 存储方式是否影响表结构设计 |

## 3. 用户域表清单

### 3.1 sys_user

| 项目 | 内容 |
| --- | --- |
| 表名 | `sys_user` |
| 用途 | 系统用户，记录平台操作人员信息 |
| 主键 | `id UUID PK DEFAULT gen_random_uuid()` |
| 关键字段 | `username`(UNIQUE NOT NULL), `password`(NOT NULL), `real_name`, `phone`, `email`, `dept_id`(UUID FK->sys_dept), `channel_code`(UNIQUE NOT NULL), `status`(SMALLINT: 2=待激活/1=正常/0=禁用), `force_password_change`(BOOLEAN), `last_login_at` |
| 审计字段 | `deleted`, `create_time`, `update_time`, `create_by`, `update_by`（继承 BaseEntity） |
| 索引 | `username`(UNIQUE), `dept_id`, `channel_code`(UNIQUE), `status`, `deleted` |
| 关联关系 | `dept_id` -> `sys_dept.id`（无显式 FK 约束）；通过 `sys_user_role` 关联 `sys_role` |
| 对应 Entity | `SysUser extends BaseEntity` |
| 对应 Mapper | `SysUserMapper` |
| 是否属于用户域 | **是** |
| 是否被其他领域直接访问 | **是**（订单域、寄样域、商品域、达人域、业绩域、分析模块均直接访问） |

### 3.2 sys_role

| 项目 | 内容 |
| --- | --- |
| 表名 | `sys_role` |
| 用途 | 角色定义，承载角色编码、数据范围和操作权限 |
| 主键 | `id UUID PK DEFAULT gen_random_uuid()` |
| 关键字段 | `role_code`(UNIQUE NOT NULL), `role_name`(NOT NULL), `data_scope`(SMALLINT: 1=self/2=group/3=all), `permissions`(JSONB), `menu_config`(JSONB), `status`, `remark` |
| 审计字段 | `deleted`, `create_time`, `update_time`, `create_by`, `update_by` |
| 索引 | `role_code`(UNIQUE) |
| 关联关系 | 通过 `sys_user_role` 关联 `sys_user`；通过 `sys_role_menu` 关联 `sys_menu` |
| 对应 Entity | `SysRole extends BaseEntity` |
| 对应 Mapper | `SysRoleMapper`（含 `findByUserId` 通过 `sys_user_role` JOIN） |
| 是否属于用户域 | **是** |
| 是否被其他领域直接访问 | **否**（仅用户域内部 Service 访问） |

### 3.3 sys_menu

| 项目 | 内容 |
| --- | --- |
| 表名 | `sys_menu` |
| 用途 | 菜单/按钮/API 权限树 |
| 主键 | `id UUID PK DEFAULT gen_random_uuid()` |
| 关键字段 | `menu_name`(NOT NULL), `menu_type`(MENU/BUTTON/API), `parent_id`(UUID, 顶级=全零 UUID), `path`, `component`, `icon`, `sort_order`, `permission_code`, `visible`, `status` |
| 审计字段 | `deleted`, `create_time`, `update_time`, `create_by`, `update_by` |
| 索引 | `parent_id`, `status` |
| 关联关系 | `parent_id` 自关联（树形结构）；通过 `sys_role_menu` 关联 `sys_role` |
| 对应 Entity | `SysMenu extends BaseEntity` |
| 对应 Mapper | `SysMenuMapper`（含 `findByRoleIds` 通过 `sys_role_menu` JOIN） |
| 是否属于用户域 | **是** |
| 是否被其他领域直接访问 | **否** |

### 3.4 sys_dept

| 项目 | 内容 |
| --- | --- |
| 表名 | `sys_dept` |
| 用途 | 部门/组织单元，同时承载部门和业务组 |
| 主键 | `id UUID PK DEFAULT gen_random_uuid()` |
| 关键字段 | `parent_id`(UUID), `dept_code`(UNIQUE NOT NULL), `dept_name`(NOT NULL), `dept_type`(VARCHAR(32) DEFAULT 'department'), `leader_user_id`(UUID), `leader`, `phone`, `email`, `sort_order`, `status` |
| 审计字段 | `deleted`, `create_time`, `update_time`, `create_by`, `update_by`, `remark` |
| 索引 | `parent_id`, `status`, `deleted`, `dept_type` |
| 关联关系 | `parent_id` 自关联（树形结构）；被 `sys_user.dept_id` 引用 |
| 对应 Entity | `SysDept extends BaseEntity` |
| 对应 Mapper | `SysDeptMapper`（含 `findChildren`、`countMembers`） |
| 是否属于用户域 | **是** |
| 是否被其他领域直接访问 | **是**（service.SysDeptService 为业务域提供查询；auth.service.SysDeptService 为管理端提供 CRUD） |

### 3.5 sys_user_role

| 项目 | 内容 |
| --- | --- |
| 表名 | `sys_user_role` |
| 用途 | 用户-角色多对多关联 |
| 主键 | `id UUID PK DEFAULT gen_random_uuid()` |
| 关键字段 | `user_id`(UUID NOT NULL FK CASCADE), `role_id`(UUID NOT NULL FK CASCADE), `create_time` |
| 约束 | `UNIQUE(user_id, role_id)`, FK CASCADE to sys_user/sys_role |
| 逻辑删除 | `deleted SMALLINT DEFAULT 0`（@TableLogic） |
| 索引 | `user_id`, `role_id` |
| 对应 Entity | `SysUserRole`（不继承 BaseEntity） |
| 对应 Mapper | `SysUserRoleMapper` |
| 是否属于用户域 | **是** |
| 是否被其他领域直接访问 | **否**（仅通过 SysRoleMapper.findByUserId JOIN 访问） |

### 3.6 sys_role_menu

| 项目 | 内容 |
| --- | --- |
| 表名 | `sys_role_menu` |
| 用途 | 角色-菜单多对多关联 |
| 主键 | `(role_id, menu_id)` 联合主键 |
| 关键字段 | `role_id`(UUID), `menu_id`(UUID) |
| 索引 | `menu_id` |
| 对应 Entity | `SysRoleMenu`（不继承 BaseEntity） |
| 对应 Mapper | `SysRoleMenuMapper` |
| 是否属于用户域 | **是** |
| 是否被其他领域直接访问 | **否** |

### 3.7 operation_log

| 项目 | 内容 |
| --- | --- |
| 表名 | `operation_log`（按月分区：`op_log_2026_04` ~ `op_log_2027_03`，自动创建未来分区） |
| 用途 | 操作审计日志，记录 HTTP 级别完整审计轨迹 |
| 主键 | `(id, create_time)` 联合主键（分区键必须包含在 PK 中） |
| 关键字段 | `user_id`, `username`, `module`, `action`, `target_type`, `target_id`, `target_name`, `content`, `request_method`, `request_url`, `request_params`(JSONB), `request_body`(JSONB), `response_code`, `response_body`(JSONB), `ip_address`, `user_agent`, `duration_ms`, `error_message` |
| 审计字段 | `create_time`, `deleted`（不继承 BaseEntity） |
| 对应 Entity | `OperationLog`（不继承 BaseEntity，IdType.INPUT） |
| 对应 Mapper | `OperationLogMapper` |
| 是否属于用户域 | **是** |
| 是否被其他领域直接访问 | **否**（通过 OperationLogService 统一写入） |

### 3.8 不存在的表

| 名称 | 实际情况 |
| --- | --- |
| `audit_log` | **不存在**。无独立安全审计日志表。登录/登出/权限变更等安全事件仅记录在 `operation_log`（HTTP 级别拦截），无专门的安全事件分类标记。 |
| `refresh_token` | **不存在**。Refresh Token 存储在 Redis（`auth:refresh:{hash}`），无数据库持久化。 |
| `token_blacklist` | **不存在**。Token 黑名单存储在 Redis（`auth:blacklist:{hash}`），基于 TTL 自动过期。 |
| `permission` | **不存在**。操作权限存储在 `sys_role.permissions` JSONB 字段中，无独立权限表。 |
| `group` / `sys_group` | **不存在**。业务组概念由 `sys_dept` + `dept_type` 字段统一承载。 |
| `data_scope` | **不存在**。数据范围存储在 `sys_role.data_scope` 字段中。 |
| `user_role` | **不存在**。由 `sys_user_role` 承担。 |
| `role_menu` | **不存在**。由 `sys_role_menu` 承担。 |

## 4. 表与 DDD 领域模型映射

| DDD 模型 | 对应表 | 对应字段/关系 | 对齐状态 |
| --- | --- | --- | --- |
| **User** | `sys_user` | id, username, password, realName, phone, email, deptId, channelCode, status, forcePasswordChange, lastLoginAt | **已对齐** |
| **Role** | `sys_role` | id, roleCode, roleName, dataScope, status, remark | **已对齐** |
| **Permission** | `sys_role.permissions`(JSONB) | `{"menus": [...], "operations": {"talent": ["list"], ...}}` | **已对齐但存储方式非结构化**（JSONB 内嵌于 sys_role，无独立表） |
| **MenuPermission** | `sys_menu` + `sys_role_menu` | menu_type=BUTTON/API 的菜单项通过 sys_role_menu 关联角色 | **已对齐** |
| **OperationPermission** | `sys_role.permissions.operations`(JSONB) | 嵌套在 permissions JSONB 的 operations 子对象中 | **已对齐但无 schema 约束** |
| **Department** | `sys_dept`(dept_type='department') | id, parentId, deptCode, deptName, deptType | **已对齐但 dept_type 值存在冲突**（详见第 12 节） |
| **Group** | `sys_dept`(dept_type=recruiter_group/channel_group/ops_group) | 同一张表通过 dept_type 区分 | **表结构支持但实际数据未对齐**（详见第 8 节） |
| **UserRoleRelation** | `sys_user_role` | id, userId, roleId, createTime, deleted | **已对齐** |
| **DataScope** | `sys_role.data_scope`(SMALLINT) | 1=self, 2=group, 3=all | **已对齐**（枚举定义在 DataScope.java） |
| **OperationLog** | `operation_log` | 21 个字段完整审计轨迹 | **已对齐** |
| **CurrentUser** | **无对应表**（运行时构造） | 从 JWT claims + sys_user + sys_role 运行时拼装 | **表结构可支撑，但缺少统一值对象**（U-3 任务） |
| **PermissionContext** | **无对应表**（运行时构造） | 从 request attributes 分散读取 | **表结构可支撑，但缺少统一构造器**（U-4 任务） |

## 5. 一人多角色支撑情况

| 检查项 | 结果 | 说明 |
| --- | --- | --- |
| 是否存在 sys_user_role 或等价关系表 | **是** | `sys_user_role` 表，含 `UNIQUE(user_id, role_id)` 约束 |
| 是否允许一个 user_id 对应多个 role_id | **是** | `UNIQUE(user_id, role_id)` 允许同一 user_id 关联多个不同 role_id |
| 代码是否按 roleIds[] 读取 | **是** | `SysRoleMapper.findByUserId()` 返回列表；`AuthService.login()` 收集所有 `roleCodes` 列表 |
| 多角色菜单权限是否能取并集 | **是** | `SysMenuService` 通过 `SysMenuMapper.findByRoleIds()` 查询所有角色的菜单并集 |
| 多角色操作权限是否能取并集 | **是** | `UserDomainService.mergePermissions()` 遍历所有角色 permissions JSONB，聚合到 LinkedHashSet |
| 多角色 data_scope 是否能取最宽 | **是** | `UserDomainService.resolveDataScopeCode()` 和 `AuthService.login()` 取所有角色 dataScope 最大值 |
| 是否存在旧的一人一角色字段残留 | **否** | `sys_user` 表无 `role_id` 或 `role_code` 字段，无旧残留 |
| 残留字段是否仍被使用 | **不适用** | 无残留字段 |

**结论：表结构真正支持一人多角色，不是代码临时拼出来的。** `sys_user_role` 多对多表 + UNIQUE 约束 + FK CASCADE 构成了规范的关系模型。

## 6. 数据范围支撑情况

| 检查项 | 结果 | 说明 |
| --- | --- | --- |
| data_scope 存储在哪里 | **`sys_role.data_scope`**(SMALLINT) | 每个角色独立配置数据范围 |
| self/group/all 是否有稳定枚举 | **是** | `DataScope` 枚举：`PERSONAL(1)`, `DEPT(2)`, `ALL(3)` |
| 管理员 all 是否可以从角色推导 | **是** | `admin` 角色 seed `data_scope=3`；代码中 ADMIN 强制提升为 ALL |
| 组长 group 是否依赖 dept、group、leader 字段 | **是** | `biz_leader`/`channel_leader` seed `data_scope=2`；group 范围通过 `sys_user.dept_id` 解析同部门成员 |
| 普通成员 self 是否依赖 user_id | **是** | `biz_staff`/`channel_staff` seed `data_scope=1`；self 范围直接用当前 userId 过滤 |
| group 成员关系来自 sys_dept、sys_group 还是其他表 | **sys_dept**（通过 `sys_user.dept_id` 指向同一 `sys_dept` 记录） | 无独立 sys_group 表 |
| 是否缺少 leader_user_id | **否** | `sys_dept.leader_user_id`(UUID) 已存在 |
| 是否缺少 dept_type / group_type | **否**（字段存在）但 **dept_type 值存在严重冲突** | 详见第 8 节和第 12 节 |
| 是否会导致订单、寄样、业绩过滤不一致 | **存在风险** | dept_type 值不一致导致 `DeptType.isGroup()` 判断可能失败；详见 P0 问题 |

**ops_staff 特殊处理**：`alter-ops-staff-data-scope-20260520.sql` 将 ops_staff 角色 data_scope 改为 1（self），但 `AuthService.login()` 和 `UserDomainService.resolveDataScopeCode()` 中对 OPS_STAFF 强制提升为 ALL(3)。这意味着 ops_staff 的 data_scope 在 JWT 中为 3，但 sys_role 表中为 1。**这是有意设计**：ops_staff 在寄样模块内享受 all 范围，登录态保持角色配置值。

## 7. 菜单权限与操作权限支撑情况

| 检查项 | 结果 | 说明 |
| --- | --- | --- |
| 菜单权限是否由 sys_menu / sys_role_menu 支撑 | **是** | sys_menu 树 + sys_role_menu 多对多 |
| 按钮权限是否有 permission_code 或 perms 字段 | **是** | `sys_menu.permission_code`(VARCHAR(100))，支持 MENU/BUTTON/API 三种粒度 |
| 操作权限是否能表达 create/update/delete/export/audit/ship 等动作 | **是**（通过 JSONB） | `sys_role.permissions.operations` 结构：`{"talent": ["list", "create", "export"], ...}` |
| 前端菜单树是否能由后端返回 | **是** | `CurrentUserResponse.permissions.menus` 包含合并后的菜单 ID 列表；`SysMenuService` 提供菜单树 |
| 前端按钮是否能由权限包控制 | **是** | `POST /users/current/permissions/check` 运行时检查 + `PermissionHintAlert.vue` 前端组件 |
| 是否存在前端硬编码权限风险 | **未发现明显硬编码** | U-1 已确认前端通过后端接口判断权限 |

**操作权限 JSONB 结构分析**：

当前 `sys_role.permissions` 存储结构为：
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

这套结构可以表达 create/update/delete/export/audit/ship 等动作，但存在两个问题：
1. **无 JSON Schema 约束**：JSONB 字段无数据库级别的格式校验，错误格式的写入不会被拦截。
2. **menus 与 sys_role_menu 冗余**：`permissions.menus` 数组与 `sys_role_menu` 表存储了重复的菜单权限信息。`UserDomainService.mergePermissions()` 从 JSONB 读取 menus，`SysMenuService` 从 sys_role_menu 读取菜单——两套来源可能不一致。

## 8. 部门与组别模型支撑情况

| 检查项 | 结果 | 说明 |
| --- | --- | --- |
| 当前 sys_dept 是否同时承担部门和业务组 | **是** | Entity JavaDoc 明确说明："技术上沿用 dept 命名，业务上承载招商组/渠道组/运营组等组织单元" |
| 是否区分招商组和渠道组 | **设计上可以区分**（通过 dept_type），**但实际数据未区分** | 见下方 P0 问题 |
| 是否有 dept_type / group_type | **有 dept_type**( VARCHAR(32) DEFAULT 'department' | 字段存在 |
| 是否有 leader_user_id | **有** | UUID 类型，关联 sys_user |
| 用户是否有 dept_id | **有** | `sys_user.dept_id` UUID |
| 组员关系如何解析 | **通过 `sys_user.dept_id` 指向同一 `sys_dept.id`** | `UserDomainService.getUserDataScope()` 查询同 dept_id 的活跃用户 |
| 是否支持"招商组长看本组招商" | **设计上支持**（data_scope=2 + dept_id），**但 dept_type 冲突可能导致判断失败** | 详见 P0 |
| 是否支持"渠道组长看本组渠道" | **同上** | 同上 |
| 是否支持运营挂部门但不强制业务组 | **支持** | ops_staff data_scope=1 但代码强制提升为 ALL |

### P0：dept_type 值严重冲突

**发现两个冲突的常量类和三个冲突的迁移脚本**：

**常量类冲突**：

| 类 | 值 | 位置 |
| --- | --- | --- |
| `DeptType.java` | `department`, `recruiter_group`, `channel_group`, `ops_group`, `BUSINESS` | `constant/DeptType.java` |
| `DeptTypes.java` | `recruiter`, `channel`, `dept` | `constant/DeptTypes.java` |

**迁移脚本冲突**（按时间顺序）：

| 脚本 | dept_type 值 | 时间 |
| --- | --- | --- |
| `migrate-sys-dept-dept-type.sql` | `recruiter`, `channel`, `dept` | 2026-05-29 |
| `alter-user-domain-v1-acceptance-20260523.sql` | `recruiter_group`, `channel_group`, `ops_group` | 2026-05-24 |
| `alter-sys-dept-uuid-canonical-20260530.sql` | `department`（全部统一） | 2026-05-30 |

**当前 init-db.sql 种子数据**：

```sql
INSERT INTO sys_dept (id, parent_id, dept_code, dept_name, dept_type, sort_order, status)
VALUES
    ('...', NULL, 'BIZ', '招商部', 'department', 10, 1),
    ('...', NULL, 'CHANNEL', '渠道部', 'department', 20, 1),
    ('...', NULL, 'OPS', '运营部', 'department', 30, 1);
```

**结论**：最新的 canonical 迁移和 init-db.sql 均将所有三个部门的 dept_type 设为 `department`。但 `DeptType.java` 常量类定义了 `recruiter_group`/`channel_group`/`ops_group`，且 `DeptType.isGroup()` 方法判断这些值是否为业务组。**当所有部门 dept_type 都是 `department` 时，`DeptType.isGroup()` 永远返回 false**。

`DeptTypes.java`（注意有 s）是另一套常量，值也不同（`recruiter`/`channel`/`dept`），进一步加剧了混乱。

**影响分析**：
- `DeptType.isGroup()` 判断永远 false -> 依赖此方法的逻辑可能出错
- `DeptType.isDepartment()` 判断永远 true -> 无法区分部门和业务组
- 两个常量类同时存在，调用方可能引用错误的常量

## 9. Token / RefreshToken / 黑名单结构支撑情况

| 检查项 | 结果 | 说明 |
| --- | --- | --- |
| refresh token 存数据库还是 Redis | **Redis** | Key: `auth:refresh:{tokenHash}`, TTL: Token 剩余有效期 |
| 黑名单存数据库还是 Redis | **Redis** | Key: `auth:blacklist:{tokenHash}`, TTL: Token 剩余有效期 |
| 是否支持 logout 吊销 refresh token | **是** | `AuthService.logout()` 将 refresh token hash 加入 Redis 黑名单 |
| access token 过期时是否仍能 logout | **否**（但前端可通过 refresh token 先刷新再 logout） | Access Token 过期后 JwtAuthenticationFilter 直接返回 401 |
| token 中是否嵌入 data_scope | **是** | JWT Claims 包含 `dataScope`(1/2/3) |
| 如果嵌入，权限变更后是否存在不一致窗口 | **是** | Access Token 有效期 2h，期间 dataScope 变更不会实时生效。但有 `UserPermissionCacheService` 缓存失效机制缓解 |

**结论**：Token/黑名单使用 Redis 存储，技术上合理（TTL 自动过期、无需数据库 IO）。**不需要为 Token 增加数据库表**。但 dataScope 嵌入 JWT 导致权限变更存在最多 2 小时不一致窗口，这是 U-3/U-4 需要通过代码解决的运行时问题，不影响表结构。

## 10. 操作日志支撑情况

| 检查项 | 结果 | 说明 |
| --- | --- | --- |
| operation_log 表是否存在 | **是** | 按月分区，12 个预创建分区 + 自动创建未来分区 |
| 是否记录登录 | **部分** | `OperationLogInterceptor` 拦截 HTTP 请求，登录 POST /auth/login 会被记录，但无专门的"登录事件"分类标记 |
| 是否记录用户创建/禁用 | **是**（通过 HTTP 拦截器） | POST /system/users 等端点会被 operation_log 记录 |
| 是否记录角色权限变更 | **是**（通过 HTTP 拦截器） | PUT /system/roles 等端点会被记录 |
| 是否记录部门/组别调整 | **是**（通过 HTTP 拦截器） | PUT /system/departments 等端点会被记录 |
| 是否支持保留 90 天 | **是**（分区覆盖 12 个月） | 分区范围 2026-04 ~ 2027-03，远超 90 天 |
| 是否需要按月分区 | **已按月分区** | 无需调整 |

**缺口**：
- **无安全事件分类**：operation_log 记录 HTTP 级别审计，但不区分"安全事件"（登录失败、锁定、权限变更）和"业务操作"。缺少 `event_type` 或 `severity` 字段。
- **无专门的安全审计表**：`audit_log` 表不存在。登录失败计数和锁定仅在 Redis 中临时存储（TTL 过期后丢失），无持久化审计。
- **不影响 U-2 表结构对齐结论**：当前 operation_log 表字段完整，能满足 V1 审计需求。安全事件分类可在 U-14 补齐操作日志时考虑。

## 11. 跨领域表访问风险

### 业务域直接访问用户域 Mapper 清单

| 业务域 | Service | 访问的用户域 Mapper / 表 | 访问方式 | 风险等级 |
| --- | --- | --- | --- | --- |
| 商品域 | `ProductService` | `SysUserMapper` | 注入 Mapper 直接查询 | **DDD 越界** |
| 达人域 | `TalentService` | `SysUserMapper` | 注入 Mapper 直接查询 | **DDD 越界** |
| 达人域 | `TalentQueryService` | `SysUserMapper` | 注入 Mapper 直接查询 | **DDD 越界** |
| 寄样域 | `SampleApplicationService` | `SysUserMapper` | 注入 Mapper 直接查询 | **DDD 越界** |
| 寄样域 | `SampleFilterOptionsService` | `SysUserMapper` | 注入 Mapper 直接查询 | **DDD 越界** |
| 订单域 | `OrderSyncPersistenceService` | `SysUserMapper` | 注入 Mapper 直接查询 | **DDD 越界** |
| 订单域 | `OrderQueryService` | `sys_user` | Raw SQL `LEFT JOIN sys_user` | **DDD 越界** |
| 业绩域 | `PerformanceQueryService` | `sys_user` | Raw SQL `LEFT JOIN sys_user` (4 次) | **DDD 越界** |
| 业绩域 | `PerformanceAccessScope` | `sys_user` | Raw SQL 子查询 `SELECT id FROM sys_user WHERE dept_id = ?` | **DDD 越界（最严重）** |
| 分析模块 | `DashboardService` | `sys_user` | Raw SQL `LEFT JOIN sys_user` | **DDD 越界** |
| 商家域 | `MerchantService` | `SysUserMapper` | 注入 Mapper 直接查询 | **DDD 越界** |
| 商家域 | `ExclusiveMerchantQueryService` | `SysUserMapper` | 注入 Mapper 直接查询 | **DDD 越界** |
| 操作日志 | `OperationLogService` | `SysUserMapper` | 注入 Mapper 查询用户名 | **临时可接受**（日志查询辅助） |

### 业务域直接访问用户域表（SQL 层面）

| Service | SQL 片段 | 风险 |
| --- | --- | --- |
| `PerformanceAccessScope.deptUserSubquery()` | `SELECT id FROM sys_user WHERE dept_id = ? AND deleted = 0` | **P0**：业绩域自行实现用户域数据范围逻辑 |
| `OrderQueryService` | `LEFT JOIN sys_user su ON ...` | **P1**：订单域直接 JOIN 用户表 |
| `PerformanceQueryService` | `LEFT JOIN sys_user dc ON dc.id = pr.default_channel_user_id` (x4) | **P1**：业绩域直接 JOIN 用户表解析用户名 |
| `DashboardService` | `LEFT JOIN sys_user su ON ...` | **P1**：分析模块直接 JOIN 用户表 |
| `SysUserService.findPage()` | `EXISTS (SELECT 1 FROM sys_user WHERE dept_id = ... AND parent_id = ...)` | **合理**：用户域内部查询 |
| `SysDeptMapper` | `SELECT COUNT(1) FROM sys_user WHERE deleted = 0 AND dept_id = #{deptId}` | **合理**：用户域内部统计 |

### 风险分类

| 分类 | 项目 | 后续处理 |
| --- | --- | --- |
| **合理读取** | SysUserService 内部查询 sys_dept/sys_user_role | 用户域内部，无需改动 |
| **合理读取** | SysDeptMapper 统计成员数 | 用户域内部，无需改动 |
| **临时可接受** | OperationLogService 查 SysUserMapper 获取用户名 | U-5 Facade 后改为调用用户域 API |
| **DDD 越界** | ProductService/TalentService/SampleApplicationService 等注入 SysUserMapper | **必须在 U-5 后迁移到 UserDomainFacade** |
| **DDD 越界** | OrderQueryService/DashboardService Raw SQL JOIN sys_user | **必须在 U-5 后迁移到 UserDomainFacade 查询 API** |
| **DDD 越界（最严重）** | PerformanceAccessScope 独立实现数据范围 + 硬编码 SQL 子查询 | **必须在 U-4/U-5 替换为用户域 DataScopeResolver** |

## 12. 当前 schema 问题清单

### P0：会导致权限错误、数据泄露、登录失败、业务闭环失败

| # | 问题 | 影响 | 建议处理阶段 |
| --- | --- | --- | --- |
| P0-1 | **dept_type 值和常量类严重冲突** | `DeptType.isGroup()` 在所有 dept_type 都是 `department` 时永远返回 false。`DeptType.java` 定义 `recruiter_group`/`channel_group`/`ops_group`，但 init-db.sql 和最新迁移统一为 `department`。同时存在 `DeptTypes.java` 定义另一套值 `recruiter`/`channel`/`dept`。 | **U-2.5 最小 migration**：确定一套标准值并统一数据+常量类 |
| P0-2 | **`sys_role.permissions.menus` 与 `sys_role_menu` 冗余** | 两套菜单权限来源可能不一致：`mergePermissions()` 从 JSONB 读 menus，`SysMenuService` 从 sys_role_menu 读菜单 | **U-3/U-4 代码适配**：明确单一来源 |

### P1：会导致 DDD 边界不清、后续重构困难

| # | 问题 | 影响 | 建议处理阶段 |
| --- | --- | --- | --- |
| P1-1 | **12 处业务域直接访问用户域 Mapper/表** | 绕过用户域边界，违反 DDD 隔离原则 | **U-5 UserDomainFacade** |
| P1-2 | **PerformanceAccessScope 独立数据范围实现** | 业绩域重复实现用户域逻辑，含硬编码 SQL | **U-4 DataScopeResolver 统一** |
| P1-3 | **SysDeptService 双份** | `auth.service.SysDeptService`（管理端 CRUD）和 `service.SysDeptService`（业务域查询）职责不清 | **U-3 合并或明确边界** |
| P1-4 | **sys_user.dept_id 无显式 FK 约束** | init-db.sql 中 sys_user.dept_id 无 FOREIGN KEY 约束，与 sys_user_role 的 FK CASCADE 不一致 | **U-2.5 migration**：`ALTER TABLE sys_user ADD CONSTRAINT fk_user_dept FOREIGN KEY (dept_id) REFERENCES sys_dept(id)` |
| P1-5 | **sys_role_menu 无 CASCADE 删除** | 删除角色或菜单时 sys_role_menu 记录不会自动清理（联合 PK 无 FK CASCADE） | **U-2.5 migration**：添加 FK CASCADE 或应用层清理 |
| P1-6 | **SysUserRole 使用 IdType.AUTO 但表主键为 UUID** | `@TableId(type = IdType.AUTO)` 与 UUID 主键不匹配，可能导致 MyBatis-Plus 行为异常 | **U-3 代码修复**：改为 `IdType.INPUT` 或 `IdType.ASSIGN_UUID` |

### P2：命名、索引、文档、可维护性问题

| # | 问题 | 影响 | 建议处理阶段 |
| --- | --- | --- | --- |
| P2-1 | **`sys_menu.parent_id` 类型为 String 而非 UUID** | Entity 中 `parentId` 是 `String`，与其他实体的 UUID 主键不一致 | **U-3 代码适配** |
| P2-2 | **OperationLog 主键使用 IdType.INPUT** | 需要应用层手动生成 UUID，不如 `DEFAULT gen_random_uuid()` 简洁 | **低优先级** |
| P2-3 | **sys_dept 种子数据 UUID 为手工编码** | `a2b3c4d5-e6f7-4890-abcd-ef0123456789` 等手工编码 UUID 可读性好但非标准 v4 | **无需改动** |
| P2-4 | **`sys_role.permissions` JSONB 无 schema 约束** | 错误格式的写入不会被数据库拦截 | **U-4 考虑添加 CHECK 约束或应用层校验** |
| P2-5 | **缺少安全审计专用表或 event_type 字段** | 登录失败、锁定等安全事件无持久化审计（Redis TTL 过期后丢失） | **U-14 补齐操作日志时考虑** |

## 13. 是否需要数据库迁移

### U-3 是否需要改表

**不需要改表**。U-3 统一 CurrentUser / PermissionContext 是纯代码层改造：
- 设计 `CurrentUser` 值对象或 `@CurrentUser` 参数解析器
- 收口 `@RequestAttribute` 的读取逻辑
- 合并双份 `SysDeptService`
- 修复 `SysUserRole.IdType.AUTO` 与 UUID 不匹配

### U-4 是否需要改表

**不需要改表，但建议添加 JSONB 校验**。U-4 统一 DataScopeResolver 主要是代码层改造：
- 设计统一的 `DataScopeResolver` 接口
- 替换 PerformanceAccessScope 独立实现
- 可选：为 `sys_role.permissions` 添加 CHECK 约束验证 JSONB 格式

### U-5 是否需要改表

**不需要改表**。U-5 UserDomainFacade 是纯代码层抽象：
- 创建 Facade 接口封装用户域查询能力
- 迁移 12 处跨域 Mapper 访问到 Facade 调用

### 建议的 U-2.5 最小 migration（如需要）

| # | Migration 内容 | 理由 | 风险 |
| --- | --- | --- | --- |
| M-1 | **统一 dept_type 值**：确定一套标准值（建议采用 `DeptType.java` 的 `recruiter_group`/`channel_group`/`ops_group`），更新 init-db.sql 种子数据和实际数据库 | 解决 P0-1 | 需要评估下游代码对 dept_type 值的依赖 |
| M-2 | **为 sys_user.dept_id 添加 FK 约束**：`ALTER TABLE sys_user ADD CONSTRAINT fk_user_dept FOREIGN KEY (dept_id) REFERENCES sys_dept(id)` | 解决 P1-4，与 sys_user_role 的 FK 策略一致 | 需要确认现有数据是否都指向合法 dept_id |
| M-3 | **为 sys_role_menu 添加 FK CASCADE**：`ALTER TABLE sys_role_menu ADD CONSTRAINT fk_rm_role FOREIGN KEY (role_id) REFERENCES sys_role(id) ON DELETE CASCADE; ALTER TABLE sys_role_menu ADD CONSTRAINT fk_rm_menu FOREIGN KEY (menu_id) REFERENCES sys_menu(id) ON DELETE CASCADE` | 解决 P1-5 | 低风险 |
| M-4 | **删除冲突常量类 `DeptTypes.java`** | 消除 P0-1 的代码层混乱 | 需要先检查 `DeptTypes.java` 的调用方并迁移到 `DeptType.java` |

**本次不创建 migration，仅提出建议。**

## 14. U-2 结论

| 分类 | 项目 | 说明 |
| --- | --- | --- |
| **已对齐 DDD** | sys_user 字段与 Entity | 所有字段一一对应，无遗漏 |
| **已对齐 DDD** | sys_role 字段与 Entity | 所有字段一一对应 |
| **已对齐 DDD** | sys_menu 字段与 Entity | 所有字段一一对应 |
| **已对齐 DDD** | sys_dept 字段与 Entity | 所有字段一一对应 |
| **已对齐 DDD** | sys_user_role 多对多关联 | UNIQUE 约束 + FK CASCADE 规范 |
| **已对齐 DDD** | sys_role_menu 多对多关联 | 联合 PK 规范 |
| **已对齐 DDD** | operation_log 分区表 | 按月分区 + 完整审计字段 |
| **已对齐 DDD** | 一人多角色 | 表结构真正支持，非代码临时拼凑 |
| **已对齐 DDD** | self/group/all 数据范围 | DataScope 枚举 + sys_role.data_scope 完整支撑 |
| **已对齐 DDD** | Token/黑名单 | Redis 存储合理，无需增加数据库表 |
| **需要代码适配** | CurrentUser 统一值对象 | U-3 任务，不涉及改表 |
| **需要代码适配** | PermissionContext 统一构造器 | U-4 任务，不涉及改表 |
| **需要代码适配** | DataScopeResolver 统一 | U-4 任务，不涉及改表 |
| **需要代码适配** | UserDomainFacade 抽象 | U-5 任务，不涉及改表 |
| **需要代码适配** | SysUserRole IdType.AUTO 修复 | U-3 任务，不涉及改表 |
| **需要代码适配** | sys_menu.parent_id String->UUID | U-3 任务，不涉及改表 |
| **需要代码适配** | SysDeptService 双份合并 | U-3 任务，不涉及改表 |
| **需要未来 migration** | dept_type 值统一（P0-1） | 建议 U-2.5 最小 migration |
| **需要未来 migration** | sys_user.dept_id FK 约束（P1-4） | 建议 U-2.5 最小 migration |
| **需要未来 migration** | sys_role_menu FK CASCADE（P1-5） | 建议 U-2.5 最小 migration |
| **高风险** | dept_type 常量类冲突 | 两套常量类 + 三套迁移值，可能导致 isGroup() 判断失败 |
| **高风险** | permissions.menus 与 sys_role_menu 冗余 | 两套菜单权限来源可能不一致 |
| **高风险** | 12 处跨域 Mapper 直接访问 | 违反 DDD 边界，需在 U-5 收口 |
| **暂不建议改动** | operation_log 分区策略 | 运行正常，无需调整 |
| **暂不建议改动** | Redis Token 黑名单方案 | 运行稳定，无需替换 |
| **暂不建议改动** | 安全审计专用表 | V1 阶段 operation_log 可满足需求，安全事件分类可后续补充 |

## 15. 下一步建议

### 是否可以进入 U-3

**可以**。表结构总体良好，7 张核心表字段与 Entity 完全对齐，一人多角色和数据范围模型表结构真正支持。U-3 是纯代码层改造，不需要改表。

**但建议在 U-3 之前先处理 P0-1（dept_type 冲突）**：

1. 确认采用哪套 dept_type 值标准（建议 `DeptType.java`：`department`/`recruiter_group`/`channel_group`/`ops_group`）
2. 删除冲突的 `DeptTypes.java`，迁移调用方到 `DeptType.java`
3. 更新 init-db.sql 种子数据中的 dept_type 值
4. 编写最小 migration 脚本统一现有数据

这可以作为 **U-2.5 最小 migration 方案设计**（半个工作日的任务），或者合并到 U-3 的前置准备中。

### U-3 应该优先统一哪些对象

1. **`CurrentUser` 值对象**：封装 userId/deptId/dataScope/roleCodes/username
2. **`@CurrentUser` 参数解析器**：替代分散的 `@RequestAttribute` 注解
3. **`SysDeptService` 合并**：统一为一份，明确管理端和业务域的接口边界
4. **`SysUserRole` IdType 修复**：从 AUTO 改为 INPUT/ASSIGN_UUID
5. **`sys_menu.parentId` 类型对齐**：从 String 改为 UUID

### U-4 DataScopeResolver 统一前需要注意什么

1. **先解决 dept_type 冲突**：DataScopeResolver 需要判断 group 范围，依赖 `DeptType.isGroup()` 正确工作
2. **明确 ops_staff 特殊处理口径**：当前 JWT 中 dataScope=3 但 sys_role.data_scope=1，需要在 Resolver 中统一
3. **PerformanceAccessScope 迁移路径**：业绩域有特化逻辑（`isChannelStaffOnly`、`isRecruiterStaffOnly`），Resolver 需要支持扩展点或策略模式
4. **@DataScope 注解扩大使用范围**：当前仅寄样域使用，其他域使用 Service 手动过滤

### U-5 UserDomainFacade 抽象前需要注意什么

1. **Facade 需要覆盖的查询能力清单**：
   - `getUserById(UUID)` — 替代 SysUserMapper.selectById
   - `getUserNameById(UUID)` — 替代 LEFT JOIN sys_user 解析用户名
   - `getUserIdsByDeptId(UUID)` — 替代 PerformanceAccessScope.deptUserSubquery()
   - `getDeptById(UUID)` — 替代 SysDeptMapper.selectById
2. **Facade 需要提供只读查询接口**：业务域不应通过 Facade 写入用户域数据
3. **迁移 12 处跨域访问需要逐域推进**：建议按 订单域 -> 业绩域 -> 商品域 -> 达人域 -> 寄样域 -> 分析模块 的顺序逐步迁移

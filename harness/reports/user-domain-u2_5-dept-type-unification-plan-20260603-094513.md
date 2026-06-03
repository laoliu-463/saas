# 用户域 U-2.5-A：dept_type 统一与最小修复方案设计

## 1. 任务概述

| 项目 | 内容 |
| --- | --- |
| 任务名称 | 用户域 U-2.5-A：dept_type 统一与最小修复方案设计 |
| 执行时间 | 2026-06-03 09:45:13 |
| 执行环境 | 本地 real-pre，只读盘点 |
| 是否修改 Java 业务代码 | 否 |
| 是否修改 Vue 前端代码 | 否 |
| 是否修改 SQL migration | 否 |
| 是否修改数据库 | 否。仅执行 SELECT / pg_catalog 只读查询 |
| 是否重启容器 | 否 |
| 是否部署远端 | 否 |
| 结论口径 | 阶段性结论，作为 U-2.5-B 输入 |

## 2. Harness 读取情况

### 实际读取文件

| 文件 | 状态 |
| --- | --- |
| `AGENTS.md` | 已读 |
| `CLAUDE.md` | 已读 |
| `docs/README.md` | 已读 |
| `docs/06-数据模型总表.md` | 已读 |
| `docs/07-权限与数据范围.md` | 已读 |
| `docs/08-第三方对接总览.md` | 已读 |
| `docs/10-部署运行总览.md` | 已读 |
| `docs/验收/real-pre联调手册.md` | 已读 |
| `docs/领域/用户域.md` | 已读 |
| `.claude/hooks/real-pre环境守卫.md` | 已读 |
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
| `harness/skills/real-pre-debug.skill.md` | 已读 |

### 前序报告

| 报告 | 路径 | 本次使用情况 |
| --- | --- | --- |
| U-1 | `harness/reports/user-domain-u1-inventory-20260603-120000.md` | 已读 |
| U-2 | `harness/reports/user-domain-u2-model-schema-alignment-20260603-150000.md` | 已读，作为 dept_type P0 的直接来源 |

### U-2 dept_type P0 摘要

U-2 报告指出：`sys_dept.dept_type` 同时存在两套常量类、三类迁移值和当前 seed/canonical 的全 `department` 值，可能导致 `DeptType.isGroup()` 对现有 BIZ / CHANNEL / OPS 种子记录全部返回 false。

本次 U-2.5-A 对该结论做了源码、migration 和 real-pre 只读查询复核。复核结论是：冲突成立；但当前多数业务数据范围过滤仍直接按 `sys_user.dept_id` / 业务表 `dept_id` 工作，并非所有 group 查询都已经直接调用 `DeptType.isGroup()`。当前最明确的直接影响面是组织归属解析、业务组列表/统计、组长角色校验、订单部门筛选元数据，以及 U-3/U-4 后续 DataScopeResolver 的基础语义。

## 3. code-review-graph 使用情况

| 操作 | 结果 |
| --- | --- |
| `get_minimal_context` | 图谱可用：9727 nodes、112000 edges、1181 files |
| `detect_changes` | 当前已有 5 个变更文件，风险 0.00 |
| `semantic_search_nodes` 搜索 `DeptType/DeptTypes/dept_type/isGroup` | 0 命中 |
| `semantic_search_nodes` 搜索 `DataScope/PerformanceAccessScope` | 0 命中 |
| 后续处理 | 图谱无法覆盖具体标识，按 AGENTS 规则回退到 `rg` 和手工读源码 |

## 4. dept_type 常量类盘点

### 4.1 `DeptType.java`

| 项 | 内容 |
| --- | --- |
| 文件路径 | `backend/src/main/java/com/colonel/saas/constant/DeptType.java` |
| 常量 | `DEPARTMENT = "department"`；`RECRUITER_GROUP = "recruiter_group"`；`CHANNEL_GROUP = "channel_group"`；`OPS_GROUP = "ops_group"`；`LEGACY_BUSINESS = "BUSINESS"` |
| 判断方法 | `isAllowed()`、`normalize()`、`isGroup()`、`isDepartment()` |
| `isGroup()` 语义 | 仅 `recruiter_group/channel_group/ops_group` 为 true；`department` 和 `BUSINESS` 均不是 group |
| 使用位置 | `auth.service.SysDeptService`、`auth.service.OrgStructureService`、`OrderController`、`SysDeptVO` 注释、DTO/Entity 注释 |
| DDD 适配度 | 更符合当前领域模型和 U-2 推荐标准 |

### 4.2 `DeptTypes.java`

| 项 | 内容 |
| --- | --- |
| 文件路径 | `backend/src/main/java/com/colonel/saas/constant/DeptTypes.java` |
| 常量 | `RECRUITER = "recruiter"`；`CHANNEL = "channel"`；`DEPT = "dept"` |
| 判断方法 | `isValid()`、`normalize()` |
| `isGroup()` | 无 |
| 使用位置 | `backend/src/main/java/com/colonel/saas/service/SysDeptService.java` |
| 测试残留 | `SysDeptControllerTest`、`SysDeptServiceTest` 使用 `recruiter` |
| DDD 适配度 | 与当前 Entity/DTO 注释和新组织归属服务不一致，应迁移后废弃 |

### 4.3 常量类直接冲突

| 维度 | `DeptType.java` | `DeptTypes.java` | 风险 |
| --- | --- | --- | --- |
| 招商组 | `recruiter_group` | `recruiter` | 同一业务含义两套持久化值 |
| 渠道组 | `channel_group` | `channel` | 查询/校验入口不一致 |
| 运营/部门 | `ops_group` / `department` | `dept` | `dept` 无法表达部门与运营组差异 |
| 合法性校验 | `department/recruiter_group/channel_group/ops_group/BUSINESS` | `recruiter/channel/dept` | 不同 Service 对同一输入给出相反判断 |

## 5. dept_type 数据来源盘点

| 文件路径 | 写入位置 | dept_type 值 | 是否符合推荐标准 | 风险说明 |
| --- | --- | --- | --- | --- |
| `backend/src/main/resources/db/migrate-sys-dept-dept-type.sql` | lines 6-13 | `recruiter`、`channel`、`dept` | 否 | 旧 `DeptTypes` 体系；与 `DeptType.isGroup()` 不兼容 |
| `backend/src/main/resources/db/migrate-all.sql` | lines 597-600 | 默认 `BUSINESS` | 否，兼容旧值 | `DeptType.normalize("BUSINESS")` 归一为 `department`，不会被识别为 group |
| `backend/src/main/resources/db/migrate-all.sql` | lines 1303-1312 | `recruiter_group`、`channel_group`、`ops_group`，默认 `department` | 是 | 推荐值，但后续 canonical 可能覆盖为 `department` |
| `backend/src/main/resources/db/alter-user-domain-v1-acceptance-20260523.sql` | lines 10-19 | `recruiter_group`、`channel_group`、`ops_group`，默认 `department` | 是 | 推荐值，适合作为最小修复参考 |
| `backend/src/main/resources/db/alter-sys-dept-uuid-canonical-20260530.sql` | line 30 | BIZ / CHANNEL / OPS 全部 `department` | 部分符合，业务语义不符合 | 使三条业务组 seed 都不再是 group |
| `backend/src/main/resources/db/init-db.sql` | lines 22-44 | 字段默认 `department` | 是 | 表结构默认值合理 |
| `backend/src/main/resources/db/init-db.sql` | lines 896-900 | BIZ / CHANNEL / OPS 全部 `department` | 部分符合，业务语义不符合 | real-pre 当前实际也是此形态 |
| `backend/src/main/resources/db/alter-sys-dept.sql` | lines 27-31 | BIZ / CHANNEL / OPS 全部 `department` | 部分符合，业务语义不符合 | 与推荐业务组值冲突 |
| `backend/src/test/resources/db/mapper-integration-schema.sql` | line 8 | 默认 `department` | 是 | 测试 schema 默认值合理，但不覆盖业务组 seed |

## 6. real-pre 当前只读对账结果

本次仅执行只读 SELECT / pg_catalog 查询，未执行 INSERT / UPDATE / DELETE / DDL。

### 6.1 Docker 状态

| 容器 | 状态 |
| --- | --- |
| `saas-active-frontend-real-pre-1` | Up 12 hours, healthy |
| `saas-active-backend-real-pre-1` | Up 12 hours, healthy |
| `saas-active-postgres-real-pre-1` | Up 20 hours, healthy |
| `saas-active-redis-real-pre-1` | Up 20 hours, healthy |

### 6.2 实际 dept_type 分布

执行：

```sql
SELECT COALESCE(dept_type, '<NULL>') AS dept_type, COUNT(*) AS cnt
FROM sys_dept
WHERE deleted = 0
GROUP BY dept_type
ORDER BY dept_type;
```

结果：

| dept_type | cnt |
| --- | ---: |
| `department` | 3 |

### 6.3 实际组织记录

执行：

```sql
SELECT dept_code, dept_name, COALESCE(dept_type, '<NULL>') AS dept_type, parent_id, leader_user_id
FROM sys_dept
WHERE deleted = 0
ORDER BY sort_order, dept_code;
```

结果：

| dept_code | dept_name | dept_type | parent_id | leader_user_id |
| --- | --- | --- | --- | --- |
| BIZ | 招商部 | `department` | null | 有值 |
| CHANNEL | 渠道部 | `department` | null | 有值 |
| OPS | 运营部 | `department` | null | null |

### 6.4 角色 data_scope

| role_code | role_name | data_scope | users |
| --- | --- | ---: | ---: |
| admin | 超级管理员 | 3 | 1 |
| biz_leader | 招商组长 | 2 | 1 |
| biz_staff | 招商专员 | 1 | 1 |
| channel_leader | 渠道组长 | 2 | 1 |
| channel_staff | 渠道专员 | 1 | 1 |
| ops_staff | 运营 | 1 | 1 |

### 6.5 组内用户计数

| dept_code | dept_type | active_users |
| --- | --- | ---: |
| BIZ | `department` | 2 |
| CHANNEL | `department` | 2 |
| OPS | `department` | 1 |

### 6.6 约束状态

| 检查 | 结果 |
| --- | --- |
| `sys_user.dept_id` 外键 | 无 |
| `sys_role_menu` 外键 | 无，仅有 `sys_role_menu_pkey` |
| 活跃用户孤儿 dept_id | 0 |

## 7. dept_type 代码使用点盘点

### 7.1 用户域

| 文件 | 使用点 | 影响 |
| --- | --- | --- |
| `constant/DeptType.java` | 唯一推荐常量类 | 新标准定义 |
| `constant/DeptTypes.java` | 旧常量类 | 应迁移后废弃 |
| `entity/SysDept.java` | `deptType` 字段默认 `department`，注释列出新标准值 | 与 `DeptType` 一致 |
| `vo/SysDeptVO.java` | 注释列出新标准值 | 与 `DeptType` 一致 |
| `auth/dto/SysDeptCreateRequest.java` | 注释列出新标准值 | 与 `DeptType` 一致 |
| `auth/dto/SysDeptUpdateRequest.java` | 注释列出新标准值 | 与 `DeptType` 一致 |
| `auth/dto/SysUserCreateRequest.java` | parentDeptId/groupId 注释依赖 `department` 与 group 值 | 与 `DeptType` 一致 |
| `auth/dto/SysUserUpdateRequest.java` | parentDeptId/groupId 注释依赖 `department` 与 group 值 | 与 `DeptType` 一致 |
| `auth/service/SysDeptService.java` | `findGroupsByParent()`、`getStats()`、create/update 校验 | 直接依赖 `DeptType.isGroup()` 和新标准 |
| `auth/service/OrgStructureService.java` | `resolveAssignment()`、`splitAssignment()`、`validateGroupLeader()`、`applyOrgFields()` | 直接依赖 `DeptType.isGroup()` |
| `service/SysDeptService.java` | `listByDeptType()`、create/update 校验 | 仍依赖旧 `DeptTypes`，与当前标准冲突 |
| `mapper/SysDeptMapper.java` | `findByDeptType()`、`countChildGroupsByType()` | 按传入值直接查 `dept_type` |
| `domain/user/event/UserGroupChangedEvent.java` | 注释说明组别通过 `sys_user.dept_id -> sys_dept` | 语义依赖 |

### 7.2 权限 / data_scope

| 文件 | 使用点 | 是否直接依赖 dept_type |
| --- | --- | --- |
| `common/enums/DataScope.java` | PERSONAL=1、DEPT=2、ALL=3 | 否 |
| `service/UserDomainService.java` | `getUserDataScope()` 在 DEPT 下查 `sys_user.dept_id = deptId` | 否 |
| `aspect/DataScopeAspect.java` | DEPT 下固定追加 `dept_id = deptId` | 否 |
| `auth/service/SysUserService.java` | 用户列表 DEPT 下按 `dept_id`；部门筛选可展开 parent_id 子节点 | 间接依赖组织结构，但不读 dept_type |
| `security/JwtAuthenticationFilter.java` | 从 JWT dataScope code 构建 DataScope | 否 |

### 7.3 订单域

| 文件 | 使用点 | 影响 |
| --- | --- | --- |
| `controller/OrderController.java` | `loadDeptOptions(DeptType.RECRUITER_GROUP/CHANNEL_GROUP)` 按 `dept_type` 构建招商/渠道部门下拉 | real-pre 当前全 `department`，这两个下拉会为空 |
| `controller/OrderController.java` | `applyDataScope()` / `applyQueryDataScope()` 在 DEPT 下按 `dept_id` 过滤 | 不直接读 dept_type |
| `service/OrderService.java` | DEPT 下按 `dept_id` 过滤；筛选支持 `channel_dept_id` | 不直接读 dept_type |
| `service/OrderQueryService.java` | 详情鉴权按 `order_dept_id` | 不直接读 dept_type |

### 7.4 业绩域

| 文件 | 使用点 | 影响 |
| --- | --- | --- |
| `service/performance/PerformanceAccessScope.java` | `deptUserSubquery()` 使用 `SELECT id FROM sys_user WHERE dept_id = ?` | 不直接读 dept_type，但重复实现用户域 group 解析 |
| `service/PerformanceQueryService.java` | 调用 `PerformanceAccessScope.appendScopeCondition()` | 受上面逻辑影响 |
| `service/PerformanceSummaryService.java` | 调用 `PerformanceAccessScope.appendScopeCondition()` | 受上面逻辑影响 |
| `service/PerformanceMetricsQueryService.java` | 局部按 `co.dept_id = ?` | 不直接读 dept_type |

### 7.5 寄样域

| 文件 | 使用点 | 影响 |
| --- | --- | --- |
| `mapper/SampleRequestMapper.java` | `@DataScope(userField = "sr.channel_user_id")` | DEPT 仍由切面追加固定 `dept_id` |
| `service/sample/SampleApplicationService.java` | `assertCanAccessSample()` 对 DEPT 比较当前 dept 与样本归属 dept | 不直接读 dept_type |
| `service/SampleFilterOptionsService.java` | dataScope 参数过滤选项 | 不直接读 dept_type |

### 7.6 商品域 / 达人域 / 分析模块

| 领域 | 文件 | 使用点 | 是否直接依赖 dept_type |
| --- | --- | --- | --- |
| 商品域 | `ProductService`、`PickSourceMapping` 等 | dataScope 多为 `dept_id` 过滤 | 否 |
| 达人域 | `TalentService`、`TalentQueryService`、`TalentClaimMapper` | DEPT 下按 claim `dept_id` | 否 |
| 分析模块 | `DashboardService`、`DataApplicationService` | DEPT 下按 `co.dept_id` 或缓存 key deptId | 否 |

### 7.7 测试代码

| 文件 | 使用点 | 风险 |
| --- | --- | --- |
| `SysDeptControllerTest.java` | 使用 `recruiter` | 仍在旧 `DeptTypes` 体系 |
| `SysDeptServiceTest.java` | 使用 `recruiter` | 仍在旧 `DeptTypes` 体系 |
| `SysDeptMapperTest.java` | 使用 `department` | 与新标准默认部门值一致 |
| `DataScopeAspectTest.java` | 验证 DEPT 追加 `dept_id` | 不覆盖 dept_type 语义 |

## 8. group 数据范围影响分析

### 8.1 当前 group 范围依赖哪些字段

当前运行时的 group 范围主要来自：

1. `sys_role.data_scope = 2`，JWT / request attribute 中转换为 `DataScope.DEPT`。
2. 当前用户 `sys_user.dept_id`。
3. 业务表上的 `dept_id`、`channel_dept_id`、`sample_request.dept_id`、`talent_claim.dept_id` 等字段。
4. 用户域 `getUserDataScope()` 在 DEPT 下查询 `sys_user.dept_id = currentDeptId`。

### 8.2 `sys_user.dept_id` 是否足够

短期可以支撑“同一个 dept_id 下的成员集合”。

real-pre 当前证据：

- BIZ 有 2 个 active users。
- CHANNEL 有 2 个 active users。
- OPS 有 1 个 active user。
- 活跃用户孤儿 `dept_id` 为 0。

但仅靠 `sys_user.dept_id` 不能表达：

- 当前 dept_id 是部门还是业务组。
- 招商组、渠道组、运营组的业务语义。
- 未来父部门 + 子业务组结构。
- 组长角色是否与组别类型匹配。

### 8.3 `sys_dept.parent_id` 是否参与

部分参与：

- `OrgStructureService.resolveAssignment()` 需要 `group.parent_id` 校验 group 属于 parent。
- `OrgStructureService.splitAssignment()` 需要 parent_id 回显 parentDept/group。
- `SysDeptMapper.countMembersUnderDept()` 通过 `sys_user.dept_id = deptId OR sys_dept.parent_id = deptId` 做两级成员统计。
- `SysUserService` 部门筛选会把 parentDept 下的 child dept 展开。

real-pre 当前 BIZ / CHANNEL / OPS 的 `parent_id` 均为 null，因此还没有“父部门 + 子业务组”层级结构。

### 8.4 `leader_user_id` 是否参与

参与组织管理和组长约束：

- `OrgStructureService.validateGroupLeader()` 根据 groupType 判断允许的组长角色。
- `auth.service.SysDeptService.create/update()` 会调用 `resolveLeaderName()` 校验 leaderUserId。
- `assertCanModify()` 允许 `channel_leader` 在其是 `leader_user_id` 时修改部门/组别。

real-pre 当前 BIZ 和 CHANNEL 有 `leader_user_id`，OPS 没有。

### 8.5 dept_type 错误如何影响组长看本组数据

必须分成两层看：

1. 当前很多业务列表的 DEPT/group 过滤并不直接调用 `DeptType.isGroup()`，而是直接比较 `dept_id`。在这些路径上，只要 `sys_user.dept_id` 和业务表 `dept_id` 一致，组长不一定立刻空组。
2. 组织归属、组别查询、组长校验、订单筛选下拉和未来统一 DataScopeResolver 会依赖 dept_type。当前 real-pre BIZ/CHANNEL/OPS 全是 `department`，会导致这些路径把业务组识别成普通部门，`DeptType.isGroup()` 返回 false。

因此，不能简单写成“所有 group data_scope 现在必然为空”；更准确的阶段性结论是：

- 当前 `dept_id` 等值过滤路径仍可能返回本组数据。
- 依赖 `DeptType.isGroup()` 的组织/元数据路径已经具备误判条件。
- U-3/U-4 若在此基础上统一 CurrentUser / PermissionContext / DataScopeResolver，会把错误组织语义固化进统一抽象。

### 8.6 招商组长和渠道组长是否会误判为空组

| 路径 | 是否可能为空 | 证据 |
| --- | --- | --- |
| `/users/current/data-scope` 类似用户 ID 列表 | 不一定为空 | `UserDomainService.getUserDataScope()` 只查 `sys_user.dept_id = deptId` |
| 业务列表 DEPT 过滤 | 不一定为空 | 多数路径只按业务表 `dept_id` 或用户 `dept_id` |
| `findGroupsByParent()` | 可能为空 | `auth.service.SysDeptService` 过滤 `DeptType.isGroup()` |
| 组织归属拆分 group 字段 | 会被识别为部门 | `OrgStructureService.splitAssignment()` 对 `department` 走 parentDeptId 分支 |
| 订单筛选中的招商/渠道部门下拉 | 会为空 | `OrderController.loadDeptOptions()` 查 `dept_type = recruiter_group/channel_group`，real-pre 当前没有 |

### 8.7 PerformanceAccessScope 是否受影响

当前 `PerformanceAccessScope` 不读 `dept_type`，但受用户域统一出口缺失影响：

- 渠道组长/招商组长路径使用角色判断 + `sys_user.dept_id` 子查询。
- DEPT 范围使用两个 `sys_user WHERE dept_id = ?` 子查询。
- `matchesDeptMember()` 当前只比较 `targetUserId == currentUserId`，注释也写明是简化版。

结论：当前不是由 dept_type 直接触发，但它是 U-4/U-5 需要迁移到用户域 DataScopeResolver/UserDomainFacade 的独立实现。

### 8.8 DataScopeAspect 是否受影响

当前 `DataScopeAspect` 不读 `dept_type`。DEPT 下固定追加 `dept_id = currentDeptId`，且注解只有 `userField()`，没有 `deptField()`。

结论：当前不被 dept_type 直接影响，但无法表达 `channel_dept_id` 或 recruiter/channel group 差异。

### 8.9 Service 手动过滤是否受影响

手动过滤多为 `dept_id` 等值，当前不直接读 dept_type；但任何需要按“招商组/渠道组”拆分元数据、候选人员或组长合法性的逻辑会受 dept_type 影响。

## 9. 推荐统一标准

### 9.1 唯一推荐标准

采用：

```text
department
recruiter_group
channel_group
ops_group
```

唯一标准类：

```text
backend/src/main/java/com/colonel/saas/constant/DeptType.java
```

`DeptTypes.java` 迁移后废弃或删除。

### 9.2 为什么采用该标准

1. `DeptType.java` 与 `SysDept` Entity、`SysDeptVO`、用户/部门 DTO 注释一致。
2. `auth.service.SysDeptService` 和 `OrgStructureService` 已经使用该标准。
3. `OrderController` 已经用 `DeptType.RECRUITER_GROUP` / `CHANNEL_GROUP` 做筛选元数据。
4. U-2 报告和旧领域主源均把 `department/recruiter_group/channel_group/ops_group` 作为 DDD 目标模型。
5. 旧 `recruiter/channel/dept` 无法区分普通部门和运营组，表达力不足。

### 9.3 旧值迁移映射

| 旧值 | 建议目标值 | 说明 |
| --- | --- | --- |
| `recruiter` | `recruiter_group` | 旧招商组 |
| `channel` | `channel_group` | 旧渠道组 |
| `dept` | `ops_group` 或 `department` | 需按 dept_code / 业务语义判断；OPS 当前更接近 `ops_group` |
| `BUSINESS` | 按 dept_code 判定；无法判定则 `department` | `DeptType.normalize()` 当前会归一为 `department`，但 migration 需要更精细 |
| BIZ 的 `department` | `recruiter_group` | 当前 real-pre BIZ 被用户和组长角色实际当业务组使用 |
| CHANNEL 的 `department` | `channel_group` | 当前 real-pre CHANNEL 被用户和组长角色实际当业务组使用 |
| OPS 的 `department` | `ops_group` 或保持 `department` | 当前 ops_staff 为运营角色；建议 U-2.5-B 前由用户确认 OPS 是运营组还是普通部门 |

### 9.4 是否保留兼容映射

建议 U-2.5-B 代码层短期保留兼容读取映射，但写入只允许新值：

- 读取兼容：`recruiter -> recruiter_group`、`channel -> channel_group`、`dept -> department/ops_group`。
- 写入标准：只写 `department/recruiter_group/channel_group/ops_group`。
- API 校验：返回新值，不再返回旧值。

## 10. 最小修复方案设计

### Step 1：确定 `DeptType.java` 为唯一标准

修改位置：常量类和所有引用点。

验证：`rg -n "DeptTypes|recruiter\"|channel\"|dept\"" backend/src/main/java backend/src/test/java` 不再出现旧常量业务用法。

### Step 2：替换 `DeptTypes.java` 调用点

最小代码范围：

- `backend/src/main/java/com/colonel/saas/service/SysDeptService.java`
- `backend/src/test/java/com/colonel/saas/controller/SysDeptControllerTest.java`
- `backend/src/test/java/com/colonel/saas/service/SysDeptServiceTest.java`

注意：该 `service.SysDeptService` 标注为 `@Service("legacySysDeptService")`，U-2.5-B 需要确认是否仍有 Controller/Service 注入它。若已无生产入口，可先迁移测试和兼容，后续 U-3 清理双 Service。

### Step 3：废弃或删除 `DeptTypes.java`

推荐顺序：

1. 先替换所有调用点。
2. 跑后端相关测试。
3. 删除 `DeptTypes.java` 或标记 Deprecated 后在 U-3 删除。

U-2.5-B 如果要最小风险，可以先 `@Deprecated` 并保留兼容映射；但为了消除 P0 语义冲突，最终应删除。

### Step 4：更新 seed / init-db 中 dept_type 值

涉及：

- `backend/src/main/resources/db/init-db.sql`
- 如仍作为受控入口使用的 `alter-sys-dept.sql`

建议 BIZ / CHANNEL / OPS 的 seed 值按当前 V1 实际用途修正为：

| dept_code | 建议 dept_type |
| --- | --- |
| BIZ | `recruiter_group` |
| CHANNEL | `channel_group` |
| OPS | `ops_group` 或待用户确认后保持 `department` |

### Step 5：新增 migration 修复历史 dept_type 值

U-2.5-A 不创建 migration。U-2.5-B 建议新增独立幂等 migration，至少覆盖：

```sql
-- 仅为设计草案，不在本任务执行
UPDATE sys_dept SET dept_type = 'recruiter_group'
WHERE dept_code = 'BIZ' AND dept_type IN ('department', 'BUSINESS', 'recruiter', '');

UPDATE sys_dept SET dept_type = 'channel_group'
WHERE dept_code = 'CHANNEL' AND dept_type IN ('department', 'BUSINESS', 'channel', '');

UPDATE sys_dept SET dept_type = 'ops_group'
WHERE dept_code = 'OPS' AND dept_type IN ('department', 'BUSINESS', 'dept', '');

UPDATE sys_dept SET dept_type = 'recruiter_group' WHERE dept_type = 'recruiter';
UPDATE sys_dept SET dept_type = 'channel_group' WHERE dept_type = 'channel';
```

OPS 是否从 `dept` / `department` 迁到 `ops_group`，需要用户确认业务语义；当前 evidence 倾向于 OPS 是运营组，因为存在 `ops_staff` 角色和 `DeptType.OPS_GROUP`。

### Step 6：补充测试覆盖

最小测试：

- `DeptType` 新增/补齐单测：`isGroup()`、`normalize()`、旧值兼容策略。
- `OrgStructureService`：BIZ/CHANNEL/OPS group 类型下 `splitAssignment()`、`resolveAssignment()`、`validateGroupLeader()`。
- `SysDeptService`：create/update/listByDeptType 接受新值，拒绝旧值或按兼容策略归一。
- `OrderController` 或相关 service：部门下拉能返回 `recruiter_group/channel_group`。
- data_scope group：`biz_leader/channel_leader` 同账号对比，确认本组数据不空且不越权。

### Step 7：执行只读对账 SQL

U-2.5-B 前必须再次执行：

```sql
SELECT dept_code, dept_name, dept_type, parent_id, leader_user_id
FROM sys_dept
WHERE deleted = 0
ORDER BY sort_order, dept_code;

SELECT dept_type, COUNT(*)
FROM sys_dept
WHERE deleted = 0
GROUP BY dept_type
ORDER BY dept_type;

SELECT d.dept_code, d.dept_type, COUNT(u.id) active_users
FROM sys_dept d
LEFT JOIN sys_user u ON u.dept_id = d.id AND u.deleted = 0 AND u.status = 1
WHERE d.deleted = 0
GROUP BY d.dept_code, d.dept_type
ORDER BY d.dept_code;
```

### Step 8：real-pre 执行前 dry-run 和备份

若 U-2.5-B 包含数据库写操作：

1. 先执行 DB 备份。
2. 输出待变更行 SELECT 对账。
3. 在事务中执行 migration。
4. 执行变更后 SELECT 对账。
5. 如有异常，按备份和事务回滚方案处理。

## 11. 是否需要数据库 migration

### 11.1 结论

需要 migration，但不是 U-2.5-A 执行。本任务只设计，不创建。

原因：

- real-pre 当前实际 `dept_type` 只有 `department`。
- 当前 BIZ / CHANNEL 具备组长和组员角色，但不是 group 类型。
- 旧 migration 可能留下 `recruiter/channel/dept/BUSINESS`。
- 仅改 Java 常量无法修复历史数据库事实。

### 11.2 migration 应更新哪些旧值

必须覆盖：

- `recruiter -> recruiter_group`
- `channel -> channel_group`
- `BIZ department/BUSINESS -> recruiter_group`
- `CHANNEL department/BUSINESS -> channel_group`

待用户确认：

- `dept -> ops_group` 还是 `department`
- `OPS department/BUSINESS -> ops_group` 还是保持 `department`

### 11.3 是否需要 CHECK 约束

建议需要，但可分期。

U-2.5-B 最小可先不加 CHECK，只做数据修正和代码统一；DB hardening 阶段再加：

```sql
CHECK (dept_type IN ('department', 'recruiter_group', 'channel_group', 'ops_group'))
```

前置条件：确认线上没有旧值残留。

### 11.4 是否需要 `sys_user.dept_id` FK

建议需要，但不属于 U-2.5-B 必做。

real-pre 当前孤儿 active users 为 0，但添加 FK 需要确认所有历史 deleted 用户和空 dept_id 情况。建议推迟到独立 DB hardening 或 U-3/U-4 后。

### 11.5 是否需要 `sys_role_menu` FK CASCADE

建议需要，但不属于 U-2.5-B 必做。

real-pre 当前 `sys_role_menu` 只有联合主键，没有 FK；该问题与 dept_type P0 无直接因果。建议推迟到独立 DB hardening。

### 11.6 U-2.5 必做 / 推迟范围

| 项 | 是否 U-2.5 必做 | 说明 |
| --- | --- | --- |
| 统一 `DeptType.java` 标准 | 是 | 消除代码层冲突 |
| 替换 `DeptTypes.java` 调用点 | 是 | 当前仍有生产 service 引用 |
| 新增 dept_type 修复 migration | 是 | 仅改代码不能修复 real-pre 数据 |
| 更新 seed / init-db | 是 | 防止新环境继续生成错误数据 |
| 补 `isGroup()` 和 group 查询测试 | 是 | 防回归 |
| `sys_user.dept_id` FK | 否 | DB hardening |
| `sys_role_menu` FK CASCADE | 否 | DB hardening |
| permissions JSONB CHECK | 否 | U-4 或 DB hardening |

## 12. 风险分级

| 级别 | 风险 | 证据 |
| --- | --- | --- |
| P0 | dept_type 不统一会导致组织归属和 group 元数据错误，并阻塞 U-3/U-4 统一 DataScopeResolver | `DeptType.isGroup()` 与 real-pre 全 `department` 冲突 |
| P0 | 两套常量类对同一字段给出不同合法值，导致不同 Service 的输入校验不一致 | `DeptType.java` vs `DeptTypes.java` |
| P0 | 订单筛选的招商/渠道部门下拉按 `recruiter_group/channel_group` 查询，real-pre 当前会返回空 | `OrderController.loadDeptOptions()` + real-pre SELECT |
| P1 | 当前业务 data_scope 实现分散，许多路径只按 `dept_id` 过滤，缺少统一用户域出口 | `UserDomainService`、`DataScopeAspect`、`PerformanceAccessScope`、多个 Service |
| P1 | `service.SysDeptService` 与 `auth.service.SysDeptService` 双份实现且标准不同 | 源码引用不同常量类 |
| P1 | `PerformanceAccessScope` 在业绩域内重复实现用户域数据范围 | `deptUserSubquery()` 硬编码 `sys_user` |
| P2 | `sys_user.dept_id` 无 FK、`sys_role_menu` 无 FK CASCADE | real-pre pg_constraint 只读查询 |
| P2 | 历史文档仍有 `recruiter/channel/dept` 旧口径 | `docs/V1-用户域现状审计.md` 等历史/旧文档 |

## 13. U-2.5-B 执行建议

### 建议进入 U-2.5-B

建议先执行 U-2.5-B，再进入 U-3。

### 可修改 Java 文件

| 文件 | 修改意图 |
| --- | --- |
| `backend/src/main/java/com/colonel/saas/service/SysDeptService.java` | 从 `DeptTypes` 迁移到 `DeptType` |
| `backend/src/main/java/com/colonel/saas/constant/DeptTypes.java` | 废弃或删除 |
| `backend/src/test/java/com/colonel/saas/controller/SysDeptControllerTest.java` | 旧值改新值 |
| `backend/src/test/java/com/colonel/saas/service/SysDeptServiceTest.java` | 旧值改新值 |
| 可能新增 `DeptTypeTest.java` | 覆盖标准/兼容判断 |

### 可修改 seed / migration 文件

| 文件 | 修改意图 |
| --- | --- |
| `backend/src/main/resources/db/init-db.sql` | 新环境 seed 修正 |
| 新增 migration 文件 | 修复历史数据 |

不建议直接改历史 migration 内容来代表已上线事实；新增幂等 migration 更可审计。

### 是否需要执行测试

需要。

最小测试建议：

```powershell
mvn -f backend/pom.xml "-Dtest=SysDeptServiceTest,SysDeptControllerTest,OrgStructureServiceTest,DataScopeAspectTest" test
```

若涉及 migration，补充 DB 集成或只读对账 SQL。

### 是否需要重启容器

U-2.5-B 如果修改 Java 或 SQL migration，按 AGENTS 规则需要构建、重启对应 Docker 容器、健康检查和业务验证。

### 是否允许 real-pre 写操作

U-2.5-A 不允许写操作。U-2.5-B 是否允许 real-pre 写操作需要用户明确确认。

real-pre 写操作前必须：

1. 备份数据库。
2. 输出 dry-run SELECT 对账。
3. 明确 UPDATE 命中行数预期。
4. migration 在事务中执行。
5. 变更后 SELECT 复核。
6. 保留 evidence report。

## 14. 状态回写建议

### `harness/CURRENT_STATE.md`

建议追加：

- 已完成用户域 U-2.5-A dept_type 统一方案设计。
- 报告路径：`harness/reports/user-domain-u2_5-dept-type-unification-plan-20260603-094513.md`。
- 是否修改代码：否。
- 是否修改数据库：否；仅只读 SELECT。
- 下一步：用户域 U-2.5-B 最小修复，而非直接进入 U-3。

### `harness/state/DOMAIN_STATUS.md`

建议将用户域状态更新为：

- U-2.5-A 已完成。
- P0：dept_type 常量/seed/real-pre 数据冲突已确认。
- 下一步：U-2.5-B 最小修复，修复后再进入 U-3。

### `harness/HARNESS_CHANGELOG.md`

建议新增 v0.2.1：

- 完成 U-2.5-A 只读方案设计。
- 生成报告路径。
- 记录 real-pre 只读查询：当前 `sys_dept.dept_type = department` 共 3 条。
- 未修改 Java/Vue/SQL/数据库，未重启，未部署。

## 15. 验证命令与结果

| 命令 | 结果 |
| --- | --- |
| `mcp__code_review_graph.get_minimal_context_tool` | PASS，图谱可用 |
| `mcp__code_review_graph.detect_changes_tool` | PASS，发现当前已有 5 个变更文件，风险 0.00 |
| `rg -n "\bDeptTypes?\b|\bdept_type\b|\bisGroup\b|..."` | PASS，定位常量、migration、调用点 |
| `docker ps --format ...` | PASS，real-pre backend/frontend/postgres/redis 均 healthy |
| `safety-check.ps1 -Env real-pre -Scope docs -DryRun` | PASS，未执行修改 |
| `SELECT dept_type, COUNT(*) FROM sys_dept ...` | PASS，real-pre 当前仅 `department=3` |
| `SELECT ... FROM pg_constraint WHERE conrelid='sys_user'` | PASS，`dept_id` 无 FK |
| `SELECT ... FROM pg_constraint WHERE conrelid='sys_role_menu'` | PASS，仅联合主键，无 FK |

未执行：

- 未执行 Java/Vue 构建：本任务未修改业务代码。
- 未执行容器重启：任务明确禁止重启。
- 未执行数据库写操作：任务明确禁止。
- 未执行远端部署：任务明确禁止。

## 16. 阶段性结论

现象：

- U-2 已提示 `dept_type` 值和常量类冲突。
- real-pre 当前 BIZ / CHANNEL / OPS 均为 `department`。

证据：

- `DeptType.java` 标准为 `department/recruiter_group/channel_group/ops_group`。
- `DeptTypes.java` 标准为 `recruiter/channel/dept`。
- migration / init-db 同时存在旧值、新值、`BUSINESS` 和全 `department`。
- real-pre 只读查询显示有效 `sys_dept` 只有 3 条，全部 `department`。
- 生产源码仍有 `service.SysDeptService` 引用旧 `DeptTypes`。

推论：

- 当前 dept_type P0 成立。
- 当前并非所有 data_scope group 过滤都会立刻空结果；许多路径仍只按 `dept_id` 等值过滤。
- 但依赖 `DeptType.isGroup()` 的组织/组别元数据和后续统一 DataScopeResolver 已经存在错误基础。

阶段性结论：

应先执行 U-2.5-B，统一为 `DeptType.java` 标准，修复旧 `DeptTypes` 调用点、seed 和历史 dept_type 数据，再进入 U-3 / U-4。U-2.5-B 需要最小 migration，但 FK/CHECK 等 DB hardening 可延后。

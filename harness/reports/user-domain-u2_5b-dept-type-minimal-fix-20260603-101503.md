# 用户域 U-2.5-B：dept_type 最小修复报告

## 1. 任务概述

| 项目 | 内容 |
| --- | --- |
| 任务名称 | 用户域 U-2.5-B：dept_type 最小修复 |
| 执行时间 | 2026-06-03 10:15:03 |
| 执行环境 | 本地 `real-pre` |
| 分支 | `feature/auth-system` |
| 基线 commit | `1ac7796f` |
| Selected Gate | Gate 1 + Gate 3 |
| 修改代码范围 | 用户域 Java 常量与直接调用点、相关单测、dept_type seed/init/既有幂等脚本、Harness 状态与报告 |
| 是否执行数据库写操作 | 否 |
| 是否重启容器 | 否。用户本轮禁止强行重启；本报告只证明构建产物生成和当前运行容器健康，不证明新 jar 已加载 |
| 是否部署远端 | 否 |
| 结论 | `PARTIAL`：本任务相关测试、构建、安全检查通过；后端全量测试仍有 6 个跨任务失败，运行态未重启加载 |

## 2. Harness 读取情况

已读取：

- `AGENTS.md`
- `CLAUDE.md`
- `docs/README.md`
- `docs/06-数据模型总表.md`
- `docs/07-权限与数据范围.md`
- `docs/09-测试验收总览.md`
- `docs/10-部署运行总览.md`
- `docs/领域/用户域.md`
- `harness/AGENT_CONTRACT.md`
- `harness/TASK_ROUTING.md`
- `harness/FORBIDDEN_SCOPE.md`
- `harness/COMPLETION_GATES.md`
- `harness/plans/DDD_OPTIMIZATION_ROADMAP.md`
- `harness/plans/DDD_DOMAIN_TASK_MATRIX.md`
- `harness/instructions/user-domain.md`
- `harness/CURRENT_STATE.md`
- `harness/state/DOMAIN_STATUS.md`
- `harness/state/KNOWN_ISSUES.md`
- `harness/state/DECISIONS.md`
- `harness/HARNESS_CHANGELOG.md`
- `harness/skills/ddd-boundary-check.skill.md`
- `harness/skills/ddd-domain-optimization.skill.md`
- `harness/skills/ddd-post-task-sync.skill.md`
- `harness/reports/user-domain-u1-inventory-20260603-120000.md`
- `harness/reports/user-domain-u2-model-schema-alignment-20260603-150000.md`
- `harness/reports/user-domain-u2_5-dept-type-unification-plan-20260603-094513.md`

code-review-graph 已先行使用：

- `get_minimal_context`：图谱可用，9727 nodes / 112000 edges / 1181 files。
- `semantic_search_nodes`：`DeptType/DeptTypes` 无命中，按 AGENTS 回退到 `rg`。
- `detect_changes`：开始时已有 7 个变更文件，风险 0.00。

## 3. U-2.5-A 结论复述

U-2.5-A 已确认：

- `DeptType.java` 标准为 `department/recruiter_group/channel_group/ops_group`。
- `DeptTypes.java` 使用旧值 `recruiter/channel/dept`。
- real-pre 当前有效 `sys_dept` 共 3 条，`dept_type` 全部为 `department`。
- 当前多数业务 data_scope 仍按 `dept_id` 等值过滤，不能扩大判断为所有 group 过滤必然为空。
- 组织归属、业务组列表/统计、组长校验、订单筛选元数据和后续 `DataScopeResolver` 会受错误 dept_type 基础影响。

## 4. 实际修改清单

### Java 文件

- `backend/src/main/java/com/colonel/saas/constant/DeptType.java`
  - `ALLOWED` 只保留标准值：`department/recruiter_group/channel_group/ops_group`。
  - `BUSINESS` 保留为读取兼容归一输入，不再作为标准允许值。
- `backend/src/main/java/com/colonel/saas/constant/DeptTypes.java`
  - 已删除。
- `backend/src/main/java/com/colonel/saas/service/SysDeptService.java`
  - 从 `DeptTypes` 迁移到 `DeptType`。
  - create/update/listByDeptType 新写入只接受标准值。

### SQL / seed / migration 文件

- `backend/src/main/resources/db/init-db.sql`
  - BIZ -> `recruiter_group`
  - CHANNEL -> `channel_group`
  - OPS -> `ops_group`
- `backend/src/main/resources/db/alter-sys-dept.sql`
  - 同步默认业务组 seed 标准值。
- `backend/src/main/resources/db/alter-sys-dept-uuid-canonical-20260530.sql`
  - canonical 迁移不再把 BIZ/CHANNEL/OPS 全部写为 `department`，改为 V1 标准值。
- `backend/src/main/resources/db/migrate-sys-dept-dept-type.sql`
  - 兼容映射 `recruiter -> recruiter_group`、`channel -> channel_group`、`dept -> department`。
  - 对 BIZ/CHANNEL/OPS 的空值、`BUSINESS`、`department` 做标准值收口。
  - 未执行到 real-pre。

### 测试文件

- `backend/src/test/java/com/colonel/saas/constant/DeptTypeTest.java`
  - 新增，覆盖 `isGroup()` 与旧值非标准。
- `backend/src/test/java/com/colonel/saas/service/SysDeptServiceTest.java`
  - 使用 `DeptType.RECRUITER_GROUP`。
  - 新增旧值 `recruiter` 被拒绝的回归测试。
- `backend/src/test/java/com/colonel/saas/controller/SysDeptControllerTest.java`
  - 测试数据改为 `recruiter_group`。

### Harness 文件

- `harness/CURRENT_STATE.md`
- `harness/state/DOMAIN_STATUS.md`
- `harness/state/DECISIONS.md`
- `harness/HARNESS_CHANGELOG.md`
- `harness/reports/user-domain-u2_5b-dept-type-minimal-fix-20260603-101503.md`

## 5. `DeptTypes.java` 处理结果

处理方式：删除。

原因：

- 全仓直接调用点只有 `backend/src/main/java/com/colonel/saas/service/SysDeptService.java`。
- 调用点已迁移到 `DeptType.java`。
- 删除后 `rg -n "DeptTypes" backend/src/main/java backend/src/test/java backend/src/main/resources/db` 无源码/测试/DB 文件命中。

是否仍保留旧值：否。`recruiter/channel/dept` 不再作为 Java 标准常量或新写入值存在；仅保留在 migration 兼容映射和测试拒绝旧值断言中。

## 6. dept_type 标准值确认

唯一标准：

- `department`
- `recruiter_group`
- `channel_group`
- `ops_group`

唯一 Java 标准类：

- `backend/src/main/java/com/colonel/saas/constant/DeptType.java`

旧值处理：

- `recruiter`：只允许 migration 兼容映射为 `recruiter_group`。
- `channel`：只允许 migration 兼容映射为 `channel_group`。
- `dept`：只允许 migration 兼容映射为 `department`。

## 7. migration 判断

是否新增 migration：否。

原因：

- 当前仓库同时存在 Docker init 聚合脚本和 `backend/src/main/resources/db/migrate/` Flyway 风格文件，但后端依赖中未确认 Flyway 作为唯一受控执行机制。
- 本轮不执行 real-pre 写库，避免把历史数据修复混入代码最小修复。
- 本轮只修正现有 seed/init/既有幂等脚本，防止新环境继续写旧值。

已修改既有幂等脚本：

- `backend/src/main/resources/db/migrate-sys-dept-dept-type.sql`

未执行到数据库：是，未执行。

后续 real-pre DB 修复任务草案：

```sql
BEGIN;

UPDATE sys_dept
SET dept_type = 'recruiter_group'
WHERE dept_type = 'recruiter';

UPDATE sys_dept
SET dept_type = 'channel_group'
WHERE dept_type = 'channel';

UPDATE sys_dept
SET dept_type = 'department'
WHERE dept_type = 'dept';

UPDATE sys_dept
SET dept_type = 'recruiter_group'
WHERE dept_code = 'BIZ' AND dept_type IN ('department', 'BUSINESS');

UPDATE sys_dept
SET dept_type = 'channel_group'
WHERE dept_code = 'CHANNEL' AND dept_type IN ('department', 'BUSINESS');

UPDATE sys_dept
SET dept_type = 'ops_group'
WHERE dept_code = 'OPS' AND dept_type IN ('department', 'BUSINESS');

COMMIT;
```

真实库执行前需要：

- 备份 real-pre 数据库。
- 先执行只读 SELECT dry-run，确认命中行。
- 审批 UPDATE 范围。
- 准备事务回滚或备份恢复方案。
- 执行后再次 SELECT 对账。

## 8. 测试与验证结果

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| TDD RED | PASS | `mvn -f backend/pom.xml "-Dtest=SysDeptServiceTest" test` 失败：新值 `recruiter_group` 被拒绝，旧值 `recruiter` 未被拒绝 |
| 相关单测 | PASS | `mvn -f backend/pom.xml "-Dtest=DeptTypeTest,SysDeptServiceTest,SysDeptControllerTest" test`：12 tests, 0 failures, 0 errors |
| DataScope 相关测试 | PASS | `mvn -f backend/pom.xml "-Dtest=DataScopeAspectTest" test`：4 tests, 0 failures, 0 errors |
| 后端编译/打包 | PASS | `mvn -f backend/pom.xml "-DskipTests" package`：BUILD SUCCESS，生成 `backend/target/colonel-saas.jar` |
| 后端全量测试 | FAIL_OUT_OF_SCOPE | `mvn -f backend/pom.xml test`：1671 tests, 6 failures, 0 errors。失败集中在 `SysUserServiceTest`、`SysUserControllerTest`、`CommissionRuleServiceTest`、`CommissionRuleControllerTest`，未落在本轮 `DeptType/SysDeptService` 修改链路 |
| safety-check code scope | FAIL_BY_SCOPE | `safety-check.ps1 -Env real-pre -Scope code -DryRun` 被 ValidateSet 拒绝，当前脚本只支持 `backend/frontend/full/docs` |
| safety-check backend scope | PASS | `safety-check.ps1 -Env real-pre -Scope backend -DryRun`：Safety check passed |
| verify-local code scope | FAIL_BY_SCOPE | `verify-local.ps1 -Env real-pre -Scope code` 被 ValidateSet 拒绝 |
| verify-local backend scope | PASS | `verify-local.ps1 -Env real-pre -Scope backend`：`/api/system/health` 返回 200，body `{"status":"UP"}` |
| Docker 状态 | PASS | real-pre backend/frontend/postgres/redis 均 `healthy` |
| 后端日志 | PARTIAL | `docker compose ... logs backend --tail=80` 命令成功但无输出；未发现启动异常证据，但也无新增日志证据 |
| 只读 SQL 对账 | PASS_READONLY | `sys_dept` 当前仍为 `department:3`；未执行 UPDATE/DDL |
| diff 检查 | PASS | `git diff --check` 无输出 |
| 容器重启 | SKIP_BY_USER | 用户明确禁止强行重启；新 jar 未加载到当前 real-pre 后端容器 |
| 远端部署 | SKIP_BY_USER | 用户未要求远端部署 |

不存在 `OrgStructureServiceTest`，本轮未伪造该测试结果。

## 9. 风险残留

- 12 处跨域 Mapper 尚未处理。
- `PerformanceAccessScope` 尚未处理。
- `DataScopeResolver` 尚未统一。
- `UserDomainFacade` 尚未抽象。
- `sys_role_menu` FK CASCADE 尚未处理。
- real-pre 历史 `dept_type` 如需修复，必须单独 DB 任务执行。
- 本轮未重启容器，当前运行态未加载新 jar，不能声明 real-pre 运行中业务已使用新代码。
- 后端全量 `mvn test` 仍有 6 个失败，未在本轮修复；因此本轮不提交、不推送。
- 当前工作区在任务开始前已有 Harness 门禁相关未提交变更，Git 提交需要避免混入无关变更。

## 10. 下一步建议

可以进入 U-3，但边界应为：

- U-3：只统一 `CurrentUser / PermissionContext`。
- U-4：再统一 `DataScopeResolver`。
- U-5：再抽 `UserDomainFacade`。
- real-pre 历史 `sys_dept.dept_type` 写库修复：单独 DB hardening / repair 任务，不并入 U-3。

## 11. 阶段性结论

现象：

- `DeptType.java` 与 `DeptTypes.java` 冲突，legacy `SysDeptService` 仍接受旧值。

证据：

- TDD RED 证明 legacy service 拒绝新标准值、接受旧值。
- GREEN 后相关单测、DataScopeAspectTest、后端打包、Harness backend 安全检查和本地后端健康检查通过；后端全量测试仍有 6 个跨任务失败。
- 只读 SQL 对账确认 real-pre 当前数据仍未写库变更。

推论：

- Java 常量体系冲突已在源码层消除。
- 新环境 seed/init 和既有幂等脚本不再把 `recruiter/channel/dept` 作为 dept_type 新写入标准。
- real-pre 历史数据仍需独立 DB 任务，不能由本轮代码修改自动修复。

结论：

U-2.5-B 的代码层和 seed/init 最小修复已完成本任务相关验证；由于后端全量测试失败、未重启容器、未执行 DB 写库，整体完成状态为 `PARTIAL`，不能写成 `DONE`，也不应提交/推送。

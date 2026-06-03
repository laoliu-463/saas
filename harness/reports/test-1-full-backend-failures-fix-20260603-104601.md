# TEST-1：全量后端测试 failures 归因与最小修复

## 1. 任务概述

| 项目 | 内容 |
| --- | --- |
| 任务名称 | TEST-1：全量后端测试 6 failures 归因与最小修复 |
| 执行时间 | 2026-06-03 10:46:01 |
| 初始状态 | U-2.5-B `PARTIAL`，前序全量 `mvn test` 有后端测试失败 |
| 本次目标 | 复现失败、逐项归因、最小修复、让全量后端测试通过 |
| Selected Gate | Bug 修复：复现 -> 修复 -> 回归；后端测试验证 |
| 是否修改 Java 业务代码 | 否 |
| 是否修改后端测试代码 | 是 |
| 是否修改 Vue | 否 |
| 是否修改数据库 | 否 |
| 是否执行 real-pre 写库 | 否 |
| 是否重启容器 | 否 |
| 是否部署远端 | 否 |
| 结论 | DONE：全量后端测试通过，Harness backend 验证通过；提交前仍需拆分任务前 dirty / 来源不确定变更 |

## 2. Harness 读取情况

已读取：

- `AGENTS.md`
- `CLAUDE.md`
- `docs/README.md`
- `docs/05-API契约总表.md`
- `docs/06-数据模型总表.md`
- `docs/07-权限与数据范围.md`
- `docs/09-测试验收总览.md`
- `docs/领域/用户域.md`
- `harness/AGENT_CONTRACT.md`
- `harness/TASK_ROUTING.md`
- `harness/FORBIDDEN_SCOPE.md`
- `harness/COMPLETION_GATES.md`
- `harness/SESSION_EXIT_GATE.md`
- `harness/plans/DDD_OPTIMIZATION_ROADMAP.md`
- `harness/plans/DDD_DOMAIN_TASK_MATRIX.md`
- `harness/instructions/user-domain.md`
- `harness/CURRENT_STATE.md`
- `harness/state/DOMAIN_STATUS.md`
- `harness/state/KNOWN_ISSUES.md`
- `harness/state/DECISIONS.md`
- `harness/HARNESS_CHANGELOG.md`
- `harness/reports/user-domain-u2_5-dept-type-unification-plan-20260603-094513.md`
- `harness/reports/user-domain-u2_5b-dept-type-minimal-fix-20260603-101503.md`
- `harness/reports/evidence-20260603-101503.md`

已使用 code-review-graph：

- `get_minimal_context`：图谱可用，9723 nodes / 112009 edges / 1180 files。
- `detect_changes`：检测 16 个 changed files，风险 0.65，提示 `DeptType` / `SysDeptService` 测试缺口。
- `semantic_search_nodes` 对本次具体测试类/方法无命中，已按 AGENTS 回退到 `rg`、`Select-String` 和手工源码追踪。

## 3. 初始 Git 工作区状态

任务开始 `git status --short` 已显示 U-2.5-B 与 Harness 门禁相关 dirty / untracked 文件。本轮后续又出现 `.gitignore` 修改、2026-06-02 报告删除和 `harness/archive/retired-content/20260603-reports-archive/` 等来源不确定变更；本任务未修改、未回滚、未纳入提交。

### U-2.5-B 已有变更

- `backend/src/main/java/com/colonel/saas/constant/DeptType.java`
- `backend/src/main/java/com/colonel/saas/constant/DeptTypes.java`（删除）
- `backend/src/main/java/com/colonel/saas/service/SysDeptService.java`
- `backend/src/main/resources/db/alter-sys-dept-uuid-canonical-20260530.sql`
- `backend/src/main/resources/db/alter-sys-dept.sql`
- `backend/src/main/resources/db/init-db.sql`
- `backend/src/main/resources/db/migrate-sys-dept-dept-type.sql`
- `backend/src/test/java/com/colonel/saas/constant/DeptTypeTest.java`
- `backend/src/test/java/com/colonel/saas/controller/SysDeptControllerTest.java`
- `backend/src/test/java/com/colonel/saas/service/SysDeptServiceTest.java`
- `harness/reports/user-domain-u2_5b-dept-type-minimal-fix-20260603-101503.md`
- `harness/reports/evidence-20260603-101503.md`

### 本次 TEST-1 变更

- `backend/src/test/java/com/colonel/saas/auth/service/SysUserServiceTest.java`
- `backend/src/test/java/com/colonel/saas/controller/SysUserControllerTest.java`
- `backend/src/test/java/com/colonel/saas/service/CommissionRuleServiceTest.java`
- `backend/src/test/java/com/colonel/saas/controller/CommissionRuleControllerTest.java`
- `harness/CURRENT_STATE.md`
- `harness/state/DOMAIN_STATUS.md`
- `harness/state/KNOWN_ISSUES.md`
- `harness/HARNESS_CHANGELOG.md`
- `harness/reports/test-1-full-backend-failures-fix-20260603-104601.md`
- `harness/reports/evidence-20260603-104601.md`
- `harness/reports/retro-20260603-104601.md`

### 任务前已有 dirty / untracked

- `harness/AGENT_CONTRACT.md`
- `harness/FORBIDDEN_SCOPE.md`
- `harness/TASK_ROUTING.md`
- `harness/COMPLETION_GATES.md`
- `harness/QUALITY_LEDGER.md`
- `harness/SESSION_EXIT_GATE.md`
- `harness/state/DECISIONS.md`
- `harness/reports/completion-gates-update-20260603-100557.md`
- `harness/reports/session-exit-gate-update-20260603-101403.md`
- `harness/reports/content-retire-20260603-103207.md`

### 来源不确定变更

- `.gitignore`
- `harness/reports/content-retire-20260602-*.md` 删除
- `harness/reports/evidence-20260602-*.md` 删除
- `harness/reports/retro-20260602-*.md` 删除
- `harness/reports/retire-*-manifest-20260602-1538.json` 删除
- `harness/archive/retired-content/20260603-reports-archive/`
- `harness/reports/evidence-20260603-104232.md`
- `harness/reports/retro-20260603-104247.md`

## 4. 失败复现结果

复现命令：

```powershell
mvn -f backend/pom.xml "-Dtest=SysUserServiceTest,SysUserControllerTest,CommissionRuleServiceTest,CommissionRuleControllerTest" test
```

实际结果：

| 项 | 结果 |
| --- | --- |
| Tests | 53 |
| Failures | 3 |
| Errors | 7 |
| Skipped | 0 |
| 与前序 6 failures 差异 | 本次指定集合实际为 10 个失败/错误；其中 7 个为 `CommissionRuleServiceTest` 的同类 wrapper SQL 读取错误 |

## 5. 每个失败的根因分析

| 测试类 | 测试方法 | expected | actual | 根因 | 与 dept_type 相关 | 与 U-2.5-B 相关 | 修复方式 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `SysUserServiceTest` | `findPage_shouldBuildWrapperWithDataScopeAndDelegateToMapper` | ALL 数据范围不追加 dataScope 的 `id/dept_id` 条件 | SQL 含 `dept_id = ...` 和 `parent_id = ...`，且断言 `id =` 被 `parent_id` / `sur.user_id` 误伤 | 测试 fixture 自己传了 `deptId`，业务代码正确追加请求筛选；断言过宽 | 否 | 否 | 将 request 的 `deptId` 置空，只验证 ALL 不追加 dataScope；`id` 断言改为不包含当前用户 ID |
| `SysUserControllerTest` | `page_returnsUserList` | JSON `code=200` | JSON `code=500`，日志为 Mockito `PotentialStubbingProblem` | Controller 已调用 4 参数 `findPage(userId, deptId, dataScope, request)`，测试仍 stub/verify 3 参数重载 | 否 | 否 | 测试 mock 改为 4 参数 `findPage` |
| `CommissionRuleControllerTest` | `page_returnsBadRequestWhenEffectiveEndBeforeStart` | HTTP 4xx | HTTP 200 | 全局 `GlobalExceptionHandler` 对 `BusinessException` 默认返回 HTTP 200，通过 JSON `code` 区分业务错误；测试期望与全局语义不一致 | 否 | 否 | 断言改为 HTTP 200、`code=400`、错误消息匹配 |
| `CommissionRuleServiceTest` | `findPage_shouldCombineDimensionTypeAndCommissionType` | 可读取 wrapper SQL 并验证维度/类型/删除条件 | `can not find lambda cache for this entity [CommissionRule]` | Mockito 单测未初始化 MyBatis-Plus `TableInfo`，`LambdaQueryWrapper` 无法解析实体列缓存 | 否 | 否 | 按 `OrderControllerTest` 既有模式初始化 `TableInfoHelper`；断言列条件和参数值 |
| `CommissionRuleServiceTest` | `findPage_shouldApplyStatusFilterWhenProvided` | SQL 含 `status = 1` | lambda cache 错误；初始化后 SQL 为占位符 | 同上，且 Lambda SQL 不内联参数值 | 否 | 否 | 验证 `status =` 列条件，参数 map 含 `1` |
| `CommissionRuleServiceTest` | `findPage_shouldIgnoreInvalidStatusValuesInsteadOfThrowing` | 非法 status 不追加 `status =` | lambda cache 错误 | 同上 | 否 | 否 | 初始化表元数据后保留“不包含 status 条件”断言 |
| `CommissionRuleServiceTest` | `findPage_shouldOverlapQueryRangeWithRuleEffectiveWindow` | SQL 含 effective 区间重叠条件 | lambda cache 错误 | 同上 | 否 | 否 | 验证区间列条件和 queryStart/queryEnd 参数 |
| `CommissionRuleServiceTest` | `findPage_shouldOnlyApplyLowerBoundWhenOnlyStartProvided` | 只追加 lower bound | lambda cache 错误 | 同上 | 否 | 否 | 验证 `effective_end >=` 和 start 参数 |
| `CommissionRuleServiceTest` | `findPage_shouldOnlyApplyUpperBoundWhenOnlyEndProvided` | 只追加 upper bound | lambda cache 错误 | 同上 | 否 | 否 | 验证 `effective_start <=` 和 end 参数 |
| `CommissionRuleServiceTest` | `findPage_shouldCombineAllFiltersWithAndSemantics` | 所有筛选 AND 组合 | lambda cache 错误；初始化后 SQL 为占位符 | 同上，且断言不能要求值内联 | 否 | 否 | 验证列条件和参数 map 同时包含 `activity/channel/status/time` |

## 6. 实际修改清单

### 测试代码

- `SysUserServiceTest`：修正 ALL 数据范围用例 fixture，避免把请求部门筛选误判为 dataScope 注入；收紧 `id` 断言。
- `SysUserControllerTest`：适配当前 Controller 的 4 参数 `findPage` 调用。
- `CommissionRuleServiceTest`：初始化 MyBatis-Plus `TableInfo`；SQL 断言改为列条件 + 参数 map。
- `CommissionRuleControllerTest`：按全局异常处理语义断言 HTTP 200 + JSON `code=400`。

### 业务代码

- 未修改。

### Harness 报告 / state

- `harness/CURRENT_STATE.md`
- `harness/state/DOMAIN_STATUS.md`
- `harness/state/KNOWN_ISSUES.md`
- `harness/HARNESS_CHANGELOG.md`
- `harness/reports/test-1-full-backend-failures-fix-20260603-104601.md`
- `harness/reports/evidence-20260603-104601.md`
- `harness/reports/retro-20260603-104601.md`

### 未修改项

- 未修改 Vue 前端。
- 未修改 Java 业务代码。
- 未修改 Docker / Compose / 部署配置。
- 未执行数据库写操作。
- 未重启容器。
- 未部署远端。
- 未删除、跳过或禁用任何测试。

## 7. 修复原则说明

| 检查 | 结果 |
| --- | --- |
| 删除测试 | 否 |
| 跳过测试 | 否 |
| 使用 `@Disabled` / `@Ignore` | 否 |
| 降低断言质量 | 否。断言从错误的字符串内联值改为列条件 + 参数值验证，保留业务含义 |
| 修改 V1 业务口径 | 否 |
| 启用 V2 功能 | 否 |
| 修改提成规则业务逻辑 | 否 |
| 修改用户域业务逻辑 | 否 |

## 8. 验证结果

| 验证项 | 命令 | 结果 |
| --- | --- | --- |
| `SysUserServiceTest` 单跑 | `mvn -f backend/pom.xml "-Dtest=SysUserServiceTest" test` | PASS：20 tests / 0 failures / 0 errors |
| `SysUserControllerTest` 单跑 | `mvn -f backend/pom.xml "-Dtest=SysUserControllerTest" test` | PASS：9 tests / 0 failures / 0 errors |
| `CommissionRuleServiceTest` 单跑 | `mvn -f backend/pom.xml "-Dtest=CommissionRuleServiceTest" test` | PASS：17 tests / 0 failures / 0 errors |
| `CommissionRuleControllerTest` 单跑 | `mvn -f backend/pom.xml "-Dtest=CommissionRuleControllerTest" test` | PASS：7 tests / 0 failures / 0 errors |
| 失败集合复跑 | `mvn -f backend/pom.xml "-Dtest=SysUserServiceTest,SysUserControllerTest,CommissionRuleServiceTest,CommissionRuleControllerTest" test` | PASS：53 tests / 0 failures / 0 errors |
| U-2.5-B 定向测试 | `mvn -f backend/pom.xml "-Dtest=DeptTypeTest,SysDeptServiceTest,SysDeptControllerTest,DataScopeAspectTest" test` | PASS：16 tests / 0 failures / 0 errors |
| 全量后端测试 | `mvn -f backend/pom.xml test` | PASS：1671 tests / 0 failures / 0 errors |
| 后端 package | `mvn -f backend/pom.xml "-DskipTests" package` | PASS：BUILD SUCCESS |
| diff 检查 | `git diff --check` | PASS：无输出 |
| safety-check code scope | `safety-check.ps1 -Env real-pre -Scope code -DryRun` | FAIL_BY_SCOPE：脚本 ValidateSet 不支持 `code` |
| verify-local code scope | `verify-local.ps1 -Env real-pre -Scope code` | FAIL_BY_SCOPE：脚本 ValidateSet 不支持 `code` |
| safety-check backend scope | `safety-check.ps1 -Env real-pre -Scope backend -DryRun` | PASS：Safety check passed |
| verify-local backend scope | `verify-local.ps1 -Env real-pre -Scope backend` | PASS：Backend statusCode=200，body `{"status":"UP"}` |
| Docker 状态 | `docker compose -f docker-compose.real-pre.yml ps` | PASS：backend/frontend/postgres/redis 均 healthy |

## 9. 残留风险

- U-2.5-B 尚未重启容器。
- 新 jar 尚未加载到 real-pre 运行态。
- 未执行 real-pre 写库。
- real-pre 历史 `sys_dept.dept_type` 仍需独立 DB 任务处理。
- 12 处跨域 Mapper 尚未处理。
- `PerformanceAccessScope` 尚未处理。
- `DataScopeResolver` 尚未统一。
- `UserDomainFacade` 尚未抽象。
- 工作区存在任务前 dirty 和来源不确定变更，提交前必须拆分，不能混入 TEST-1。

## 10. 结论

DONE。

证据链：

- 指定失败集合已复现，实际为 53 tests / 3 failures / 7 errors。
- 每个失败均已从 surefire 报告和源码定位到测试夹具/断言问题，未发现与 `DeptType` / `dept_type` / `DeptTypes` / `SysDeptService` 的直接因果。
- 最小测试修改后，单类、组合、U-2.5-B 定向、全量后端测试、package、diff check、Harness backend safety-check 和 verify-local 均通过。

## 11. 下一步建议

- 建议执行 U-2.5-D：安全拆分提交与状态收口。
- 若提交 U-2.5-B + TEST-1 组合修复，必须先排除 `.gitignore`、旧报告删除、archive 等来源不确定变更。
- 提交后再进入 U-3 CurrentUser / PermissionContext 统一。
- real-pre 容器重启加载新 jar 和历史 `dept_type` 写库修复应作为独立任务处理。

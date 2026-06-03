# B3-SCOPE-001：Batch 3 后端候选范围隔离报告

## 1. 任务概述

| 项 | 内容 |
| --- | --- |
| 任务编号 | B3-SCOPE-001 |
| 任务名称 | 隔离 Batch 3 后端候选文件并排除混入项 |
| 执行时间 | 2026-06-03 14:31:31 |
| 仓库 | `D:\Projects\SAAS` |
| 分支 | `feature/auth-system` |
| HEAD | `5fe6ba23 feat(product-ui): product card hover expand and library load-more pagination` |
| 执行模式 | 只做 Git 范围隔离和 staged 候选清单确认 |
| 是否修改业务代码 | 否 |
| 是否执行数据库操作 | 否 |
| 是否重启容器 | 否 |
| 是否部署远端 | 否 |
| 是否提交 / 推送 | 否 |

## 2. B3-VERIFY-001 结论引用

引用证据路径：

- `harness/reports/evidence-20260603-142506.md`

B3-VERIFY-001 已证明：

- U-2.5-B 定向测试：PASS，16 tests / 0 failures / 0 errors
- TEST-1 失败集合：PASS，53 tests / 0 failures / 0 errors
- 全量 `mvn -f backend/pom.xml test`：PASS，1675 tests / 0 failures / 0 errors
- `mvn -f backend/pom.xml "-DskipTests" package`：PASS
- `git diff --check`：PASS
- `safety-check -Scope backend -DryRun`：PASS
- 未执行数据库写操作
- migration 文件未执行
- 未 commit / push / deploy

B3-VERIFY-001 阻塞点：`backend/src/main/resources/application-real-pre.yml` 混入 P-FIX-002 商品同步配置；工作区存在 harness 状态/报告残留。

## 3. 当前 Git 工作区状态

前置确认命令：

- `git status --short`
- `git diff --name-only`
- `git diff --stat`
- `git log -1 --oneline`

HEAD 与预期一致：

```text
5fe6ba23 feat(product-ui): product card hover expand and library load-more pagination
```

工作区事实：

- backend 候选文件存在。
- `backend/src/main/resources/application-real-pre.yml` 仍为 unstaged。
- harness 状态文件和报告文件仍为 unstaged / untracked。
- frontend / docs 无 dirty 文件。

## 4. backend dirty 分类表

| 文件路径 | 状态 | 归属 | 是否允许 Batch 3 stage | 理由 |
| --- | --- | --- | --- | --- |
| `backend/src/main/java/com/colonel/saas/constant/DeptType.java` | M | U-2.5-B | 是 | dept_type 标准常量修复 |
| `backend/src/main/java/com/colonel/saas/constant/DeptTypes.java` | D | U-2.5-B | 是 | 删除旧 dept_type 常量类 |
| `backend/src/main/java/com/colonel/saas/service/SysDeptService.java` | M | U-2.5-B | 是 | 迁移到 `DeptType` 标准校验 |
| `backend/src/main/resources/application-real-pre.yml` | M | P-FIX-002 | 否 | 只包含 `product.activity.sync` 商品同步配置，非 Batch 3 |
| `backend/src/main/resources/db/alter-sys-dept-uuid-canonical-20260530.sql` | M | U-2.5-B | 是 | dept_type 标准值 seed / 幂等脚本修正 |
| `backend/src/main/resources/db/alter-sys-dept.sql` | M | U-2.5-B | 是 | dept_type 默认业务组标准值修正 |
| `backend/src/main/resources/db/init-db.sql` | M | U-2.5-B | 是 | dept_type 初始化 seed 标准值修正 |
| `backend/src/main/resources/db/migrate-sys-dept-dept-type.sql` | M | U-2.5-B | 是 | 旧 dept_type 值兼容映射脚本修正；本轮未执行 |
| `backend/src/test/java/com/colonel/saas/auth/service/SysUserServiceTest.java` | M | TEST-1 | 是 | 修复全量测试失败集合中的用户服务测试 fixture / 断言 |
| `backend/src/test/java/com/colonel/saas/constant/DeptTypeTest.java` | ?? / A | U-2.5-B | 是 | 新增 dept_type 标准值回归测试 |
| `backend/src/test/java/com/colonel/saas/controller/CommissionRuleControllerTest.java` | M | TEST-1 | 是 | 修复全量测试失败集合中的 Controller 异常响应断言 |
| `backend/src/test/java/com/colonel/saas/controller/SysDeptControllerTest.java` | M | U-2.5-B | 是 | dept_type 测试数据改为标准值 |
| `backend/src/test/java/com/colonel/saas/controller/SysUserControllerTest.java` | M | TEST-1 | 是 | 适配当前 `findPage` 参数签名 |
| `backend/src/test/java/com/colonel/saas/service/CommissionRuleServiceTest.java` | M | TEST-1 | 是 | 初始化 MyBatis-Plus TableInfo，并修正 wrapper 参数断言 |
| `backend/src/test/java/com/colonel/saas/service/SysDeptServiceTest.java` | M | U-2.5-B | 是 | dept_type 标准值和旧值拒绝回归测试 |

## 5. `application-real-pre.yml` 排除原因

检查命令：

```powershell
git diff -- backend/src/main/resources/application-real-pre.yml
```

diff 仅包含：

```yaml
product:
  activity:
    sync:
      enabled: ${PRODUCT_ACTIVITY_SYNC_ENABLED:true}
      cron: "${PRODUCT_ACTIVITY_SYNC_CRON:0 */5 * * * ?}"
```

结论：

- 该变更属于 P-FIX-002 商品活动同步配置残留。
- 不属于 U-2.5-B / TEST-1。
- 已保持 unstaged。
- 后续应另起 P-FIX-002 状态收口任务处理。

## 6. staged 文件清单

执行方式：逐文件 `git add -- <file>`，未使用 `git add .`，未使用 `git add backend/`。

`git diff --cached --name-only`：

```text
backend/src/main/java/com/colonel/saas/constant/DeptType.java
backend/src/main/java/com/colonel/saas/constant/DeptTypes.java
backend/src/main/java/com/colonel/saas/service/SysDeptService.java
backend/src/main/resources/db/alter-sys-dept-uuid-canonical-20260530.sql
backend/src/main/resources/db/alter-sys-dept.sql
backend/src/main/resources/db/init-db.sql
backend/src/main/resources/db/migrate-sys-dept-dept-type.sql
backend/src/test/java/com/colonel/saas/auth/service/SysUserServiceTest.java
backend/src/test/java/com/colonel/saas/constant/DeptTypeTest.java
backend/src/test/java/com/colonel/saas/controller/CommissionRuleControllerTest.java
backend/src/test/java/com/colonel/saas/controller/SysDeptControllerTest.java
backend/src/test/java/com/colonel/saas/controller/SysUserControllerTest.java
backend/src/test/java/com/colonel/saas/service/CommissionRuleServiceTest.java
backend/src/test/java/com/colonel/saas/service/SysDeptServiceTest.java
```

`git diff --cached --stat`：

```text
14 files changed, 156 insertions(+), 80 deletions(-)
```

## 7. staged 范围审查结论

检查项：

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| staged 只包含 Batch 3 后端文件 | PASS | `git diff --cached --name-only` 仅 14 个 backend 文件 |
| 不包含 `application-real-pre.yml` | PASS | staged 清单无该文件 |
| 不包含 frontend | PASS | staged 清单无 `frontend/` |
| 不包含 harness | PASS | staged 清单无 `harness/` |
| 不包含 Docker / compose | PASS | staged 清单无 Docker / compose 文件 |
| 不包含 P-FIX-002 商品同步代码 | PASS | staged 清单无 ProductActivitySync / ProductDisplayRule / 商品同步配置 |
| staged whitespace check | PASS | `git diff --cached --check` 无输出 |

## 8. 轻量复核命令与结果

| 命令 | 结果 |
| --- | --- |
| `mvn -f backend/pom.xml "-Dtest=DeptTypeTest,SysDeptServiceTest,SysDeptControllerTest,DataScopeAspectTest" test` | PASS：16 tests / 0 failures / 0 errors |
| `mvn -f backend/pom.xml "-Dtest=SysUserServiceTest,SysUserControllerTest,CommissionRuleServiceTest,CommissionRuleControllerTest" test` | PASS：53 tests / 0 failures / 0 errors |
| `git diff --cached --check` | PASS：无输出 |
| `powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\safety-check.ps1 -Env real-pre -Scope backend -DryRun` | PASS：Safety check passed |

未重新执行全量 `mvn test`，原因：B3-VERIFY-001 已在 `harness/reports/evidence-20260603-142506.md` 记录全量 1675 tests / 0 failures / 0 errors。

## 9. 是否允许进入 GIT-BATCH-3

结论：允许进入 GIT-BATCH-3 的提交前复核阶段。

约束：

- 仅允许提交当前 staged 的 14 个 Batch 3 后端文件。
- 不允许提交 unstaged 的 `backend/src/main/resources/application-real-pre.yml`。
- 不允许提交任何 harness 状态 / 报告文件。
- 不允许混入 frontend、Docker / compose、P-FIX-002、Batch 2、Batch 4、Batch 5 文件。
- 本报告本身不应纳入 GIT-BATCH-3 commit。

## 10. 禁止行为确认

| 禁止项 | 是否发生 |
| --- | --- |
| 修改 Java 代码 | 否 |
| 修改 Vue 代码 | 否 |
| 修改 SQL 内容 | 否 |
| 执行数据库操作 | 否 |
| 重启容器 | 否 |
| 部署远端 | 否 |
| 提交 | 否 |
| 推送 | 否 |
| `git add .` | 否 |
| `git add backend/` | 否 |
| stage `application-real-pre.yml` | 否 |
| stage harness 报告/状态文件 | 否 |
| 混入 P-FIX-002 / Batch 2 / Batch 4 / Batch 5 文件 | 否 |

## 11. 阶段性结论

现象：

- B3-VERIFY-001 后工作区存在 Batch 3 后端候选文件、P-FIX-002 配置残留和 harness 报告/状态残留。

证据：

- HEAD 为预期 `5fe6ba23`。
- `application-real-pre.yml` diff 只包含 `product.activity.sync`。
- staged 清单只有 14 个 U-2.5-B / TEST-1 后端文件。
- 两组轻量 Maven 复核、staged diff check 和 backend safety-check 均通过。

推论：

- 当前 staged 范围已经隔离出 Batch 3 后端候选。
- 未 staged 的 P-FIX-002 和 harness 文件仍留在工作区，但不会进入 GIT-BATCH-3，前提是后续提交只使用当前 staged 内容。

结论：

`B3-SCOPE-001` 达成范围隔离目标。当前 staged 内容允许进入 GIT-BATCH-3 的提交前复核阶段；本任务未提交、未推送、未部署、未执行数据库操作。

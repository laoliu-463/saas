# Phase 2 Task 7B 授权事实写入口接线 Evidence

- 生成时间：2026-07-14 19:47:32 +08:00（Asia/Shanghai）
- 环境：本地工作树，仅 Maven 单元测试与构建；未启动运行环境
- Scope：backend / 用户域 Authorization
- 分支：`codex/rbac-shadow-runtime-plan`
- 功能 commit：`e0165922`
- 工作区：功能 commit 后 clean；生成报告时仅本 evidence 待提交
- ReportKey：`rbac-shadow-runtime-task-07b`

## 结论

`PARTIAL`

Task7B 的 5 个 writer 已按精确合同完成 TDD 接线，已执行的 200 个测试与 Maven package 均通过。由于用户明确禁止 Docker、migration、运行模式激活、远端部署和 push，Task7A 两个 Testcontainers 测试、容器 reload、health、API/E2E 均未执行；不得扩大为运行时闭环 PASS。

## 变更

- 用户角色全量替换后递增目标用户授权版本。
- 角色更新后递增该角色关联用户授权版本；create/delete 不递增。
- 角色菜单权限哈希实际变化且关系写成功后递增；无变化不递增。
- 用户 status/dept 实际变化后递增；纯资料更新不递增。
- 重置密码成功后递增；删除用户不递增。
- 组成员真实 dept 变化后递增；同组和非成员不递增。
- 版本服务异常不捕获，保持调用方事务回滚语义。

## RED 证据

5 个服务均先修改测试，再运行单类测试确认生产构造器尚未接入版本服务：

| 测试类 | 结果 | 耗时 | 预期失败点 |
| --- | --- | ---: | --- |
| `SysUserRoleAssignmentApplicationServiceTest` | RED | 20.4s | 构造器缺版本服务参数 |
| `SysRoleApplicationTest` | RED | 20.0s | 构造器缺版本服务参数 |
| `SysMenuServiceTest` | RED | 19.4s | 构造器缺版本服务参数 |
| `SysUserCRUDApplicationBTest` | RED | 19.8s | 构造器缺版本服务参数 |
| `SysUserGroupMembershipApplicationTest` | RED | 20.4s | 构造器缺版本服务参数 |

## GREEN 与构建证据

| 检查 | 结果 | Surefire / 说明 |
| --- | --- | --- |
| 5 个 writer 合并测试 | PASS | 44 tests / 0 failures / 0 errors / 0 skipped |
| 直接受影响与授权回归 7 类 | PASS | 156 tests / 0 failures / 0 errors / 0 skipped |
| 已执行测试合计 | PASS | 200 tests / 0 failures / 0 errors / 0 skipped |
| `mvn -q -DskipTests package` | PASS | exit 0 |
| `git diff --check` / cached check | PASS | 无输出 |
| Harness limits（`-NoReport`） | PASS | `TASK_GATE=PASS`；仓库历史 reports 数量债务使 `REPOSITORY_HEALTH=PARTIAL` |

授权回归 7 类：`AuthorizationApplicationServiceTest`、`AuthorizationPrincipalApplicationServiceTest`、`AuthorizationRuntimeServiceTest`、`AuthServiceTest`、`JwtAuthenticationFilterTest`、`SysRoleServiceTest`、`SysUserServiceAssignableBoundaryTest`。

## 未执行 / 阻塞

- `AuthorizationVersionStoreIntegrationTest`、`AuthorizationVersionApplicationServiceTest`：未执行；两者使用集成测试基座，执行可能启动 Testcontainers，与本任务“不得执行 Docker”约束冲突。
- backend safety-check：`BLOCKED`；worktree 缺 `.env.real-pre`，脚本在读取阶段退出，未执行 Docker 或写操作。
- Docker reload、容器状态、health、API smoke、授权 E2E：未执行；用户明确禁止 Docker 和运行模式激活。
- migration、远端部署、push：未执行；用户明确禁止。

## 风险与边界

- writer 单测证明精确 cause/actor/target、调用顺序、恰好一次/零次、异常传播和 update/reset 事务注解；未伪造数据库真实回滚或 AFTER_COMMIT 缓存驱逐证据。
- Task7A 集成测试需在允许 Testcontainers/Docker 的后续窗口补跑，才能形成完整 7 类目标测试证据。
- code-review-graph 在任务开始时未索引 5 个 writer，semantic search/影响半径返回空；已回退到源码和测试证据，未把图谱 0 结果当作无影响结论。

## Retro

- 有效做法：逐服务执行 RED → 最小接线 → 单类 GREEN，使构造注入、顺序和零调用条件可独立定位。
- 改进点：任务开始阶段文档与图谱探索耗时偏长；收到反馈后已停止扩大检索并切换为逐服务小步 TDD。
- 独立 retro：不生成；当前没有需要单独责任人、动作和验证方式的新 Harness 改进项。

# 业绩权限真实账号 API/SQL 只读对账设计

**日期：** 2026-07-16

**状态：** 待用户复核

**范围：** Y-17 业绩查询权限与数据范围的 real-pre 真实账号 API/SQL 证据

## 背景与证据

Y-17 当前为 `PARTIAL`。已有后端测试证明：

- `PerformanceController` 会把 `userId`、`deptId`、`dataScope` 和 `roleCodes` 组装为 `PerformanceAccessContext` 并传入列表、汇总和导出服务。
- `PerformanceAccessScope` 会按角色把渠道人员限制到 `final_channel_user_id`，把招商人员限制到 `final_recruiter_user_id`，组长按本部门成员过滤。
- staff 导出被拒绝，leader/admin 导出和 admin 月度重算的本地正反例已有测试。

现有运行态证据仍不完整：

- `tests/e2e/performance-domain-v1-closure.spec.ts` 只断言真实接口成功和返回结构存在，没有把不同账号的 API 结果与 SQL 事实核对。
- `tests/e2e/35-real-pre-rbac-scope.spec.ts` 只统计通用业务接口是否有样本，没有验证业绩域的最终渠道/招商归属字段。
- `runtime/qa/real-pre-dashboard-reconcile.cjs` 按订单表的 `user_id/dept_id` 对账 dashboard，不能替代业绩域按 `performance_records.final_channel_user_id/final_recruiter_user_id` 的权限口径。

2026-07-16 对本地 real-pre 做只读数据体检得到：有效业绩记录 515,052 条，渠道最终归属人 0 个，招商最终归属人 2 个；现有 `biz_leader` 和 `biz_staff` 账号分别存在 15,345 和 2,277 条招商归属记录。该事实足以验证招商侧真实差异，但不足以证明渠道侧正向可见性。

## 目标

1. 用真实 admin/leader/staff 账号证明 `/api/performance` 的数据范围与 PostgreSQL 当前事实一致。
2. 对每个账号同时核对 API `total`、当前页记录归属和跨范围访问拒绝，避免只比较一个聚合数字。
3. 保持 real-pre 业务数据只读，不创建用户、不补写业绩、不修改最终归属。
4. 将缺少渠道正向样本明确记录为 `PARTIAL`，不把空结果相等误报为完整闭环。
5. 本轮只补运行态验收资产和矩阵证据，不修改生产业务规则、API、schema 或前端。

## 方案决定

新增业绩权限专用的只读对账探针及其单元测试：

- `runtime/qa/real-pre-performance-access-reconcile.cjs`
  - 复用 `real-pre-env.cjs` 的 real-pre 环境守卫、数据库容器解析、证据目录与脱敏能力。
  - 从现有 QA 角色配置读取账号，登录后调用 `/api/users/current` 获取服务端实际身份、部门、数据范围和角色。
  - 调用 `/api/performance?page=1&pageSize=100&sortBy=calculatedAt&sortOrder=desc` 获取当前账号的业绩列表。
  - 根据实际角色构造独立 SQL 期望范围，并核对 API `total`。
  - 对 API 当前页每条记录按 `order_id` 回查最终渠道/招商归属，验证不存在范围泄漏。
  - 选择范围外样本调用只读详情或带越权筛选的列表接口，验证 staff 的跨范围请求被拒绝。
  - 不调用导出、月度重算等可能产生审计日志或业务写入的接口；导出权限继续由既有后端正反例证明。
  - 生成脱敏后的 `summary.json` 与 `report.md`，不落盘 token、密码或请求头。
- `runtime/qa/real-pre-performance-access-reconcile.test.cjs`
  - 覆盖角色到 SQL 范围的映射、缺少 user/dept 时 fail-closed、API/SQL 结果比较、样本充分性分级和 real-pre 环境守卫。
- 根 `package.json`
  - 增加独立命令，便于 Harness 和人工复验稳定调用。

本轮不会复用 dashboard 对账 SQL，也不会给现有通用 RBAC 测试添加业绩域特殊逻辑。

## 未采用的方案

### 扩展 dashboard 对账脚本

dashboard 的范围事实来自订单表 `co.user_id/co.dept_id`，业绩列表的范围事实来自最终归属字段。合并会把两个领域口径混在一起，即使测试通过也不能证明 Y-17。

### 直接扩展现有业绩 Playwright 闭环

现有 spec 在 `beforeAll` 调用 `seedTestData`，主要面向 test profile。把 real-pre 只读验收塞入该文件会引入写入风险和环境语义混淆，也不利于独立生成 API/SQL evidence。

### 在 real-pre 创建确定性渠道样本

这可以一次补齐渠道正向证据，但需要决定订单、归属、提成和历史数据处理规则，并修改真实业务表。当前用户没有授权写入，且本轮目标是权限验收，不应借测试绕过业务入口修改事实。

## 角色与 SQL 口径

探针以服务端 `/api/users/current` 返回的身份和角色为准：

| 角色 | SQL 期望范围 |
| --- | --- |
| admin / ops_staff / ALL | `pr.is_valid = TRUE` |
| channel_staff | `pr.is_valid = TRUE AND pr.final_channel_user_id = :userId` |
| biz_staff | `pr.is_valid = TRUE AND pr.final_recruiter_user_id = :userId` |
| channel_leader | `pr.is_valid = TRUE AND pr.final_channel_user_id IN (SELECT id FROM sys_user WHERE dept_id = :deptId AND deleted = 0)` |
| biz_leader | `pr.is_valid = TRUE AND pr.final_recruiter_user_id IN (SELECT id FROM sys_user WHERE dept_id = :deptId AND deleted = 0)` |
| 无明确角色的 PERSONAL | 渠道或招商最终归属人等于当前用户 |
| 无明确角色的 DEPT | 渠道或招商最终归属人属于当前部门 |

SQL 全部参数化或经过 UUID 格式校验；缺少受限账号的 `userId/deptId` 时直接失败，不退化为全量查询。

## 数据流与证据分级

```text
real-pre 环境守卫
  -> 真实账号登录
  -> /api/users/current 获取实际权限上下文
  -> /api/performance 获取 scoped total/items
  -> PostgreSQL 只读 SQL 计算 expected total/ownership
  -> 越权只读请求验证
  -> 脱敏 summary.json/report.md
```

单角色状态：

- `PASS`：登录成功、API total 与 SQL 一致、当前页每条归属均合法、越权负向验证通过，并且该角色有正向样本。
- `PARTIAL_NO_POSITIVE_SAMPLE`：API 与 SQL 均为 0 且负向验证通过，但没有属于该角色的正向记录。
- `BLOCKED_AUTH`：配置中的真实账号无法登录，当前角色证据未采集；该状态不是权限行为通过或失败。
- `FAIL`：环境不符、已认证角色 API/SQL 不一致、出现范围泄漏、越权请求未被拒绝或 SQL 上下文缺失。

总体状态：

- 任一角色 `FAIL` 则总体 `FAIL`。
- 无失败但存在 `PARTIAL_NO_POSITIVE_SAMPLE` 或 `BLOCKED_AUTH`，总体为 `PARTIAL`。
- 所有纳入验收的角色都有正向样本且全部通过，才为 `PASS`。

因此，按当前 real-pre 数据，本轮预期招商角色可形成真实 `PASS`；渠道账号若可认证但没有归属样本则为 `PARTIAL_NO_POSITIVE_SAMPLE`，若凭证已漂移则为 `BLOCKED_AUTH`；Y-17 均保持 `PARTIAL`。

## 安全与错误处理

- 探针启动时必须确认 health 为 `UP`、环境标签为 `REAL-PRE`、active profile 包含 `real-pre`，且测试/mock 开关关闭；否则拒绝执行。
- 除鉴权所需的登录 `POST` 外，业绩业务接口只允许 `GET` 列表、详情和当前用户；不调用导出、seed、backfill、重算或任何可能写入审计/业务数据的接口。
- SQL 只使用 `SELECT`；脚本中不出现 `INSERT`、`UPDATE`、`DELETE`、`TRUNCATE` 或 DDL。
- token、密码、Authorization 和 secret-like 字段在写 evidence 前统一脱敏。
- HTTP 状态成功但业务响应码非 `0/200` 时按接口失败处理，不能把空 `data` 误报为 0 条记录。
- 单个角色认证阻塞会记录为 `BLOCKED_AUTH` 并继续采集其他角色；已认证角色的接口或口径问题仍如实记为 `FAIL`。

## TDD 实施顺序

1. 先新增脚本单元测试，固化角色范围、fail-closed 和 `PARTIAL_NO_POSITIVE_SAMPLE` 语义；测试应因实现不存在而失败。
2. 实现最小的纯函数和只读运行器，使单元测试转绿。
3. 增加 package 命令并运行 Node 单元测试。
4. 在当前健康的本地 real-pre 上执行一次真实探针，核对输出不含凭证且状态与数据体检一致。
5. 更新 Y-17 矩阵和领域状态快照，只记录本轮真实证据；渠道正向样本、前端菜单/导出按钮 E2E 继续保留为未完成项。
6. 通过项目统一 Harness 执行构建、容器重启、健康检查、业务验证、安全检查和 evidence 生成。

## 验收标准

- 独立 Node 测试覆盖范围 SQL、环境守卫、结果比较、样本分级和脱敏边界。
- real-pre 探针没有业务表写入，也不调用业务写接口。
- `biz_leader`、`biz_staff` 和 admin 的 API total 与 SQL 期望一致，且当前页记录不存在跨范围泄漏。
- staff 的跨范围只读访问被拒绝；导出权限继续由既有后端正反例覆盖。
- 渠道账号无正向样本时明确输出 `PARTIAL_NO_POSITIVE_SAMPLE`，认证阻塞时输出 `BLOCKED_AUTH`；Y-17 不得因此标记 `DONE`。
- evidence 包含环境、角色结果、API/SQL 比较、样本充分性、结论和剩余风险，且不包含 token 或密码。
- Harness 报告如实记录构建、容器、健康、业务验证和仓库健康；未验证项不得标记为 `PASS`。

## 风险与回滚

主要风险是独立 SQL 与生产过滤逻辑出现漂移。通过单元测试固化角色映射，并同时核对 API total 与逐行归属降低误判风险；后续生产权限口径变化时，探针必须同步更新并接受代码审查。

回滚仅需删除新增 QA 脚本、测试、package 命令和文档证据，不涉及数据库、API、容器数据或历史记录回滚。

## 非目标

- 不修改业绩归属、提成、退款、冲正或双轨金额规则。
- 不创建或修复 real-pre 渠道归属数据。
- 不执行月度重算、backfill 或 seed。
- 不调整 `PerformanceAccessScope` 生产实现。
- 不完成前端菜单、导出按钮或页面级 E2E；这些作为 Y-17 后续小步。
- 不执行远端 real-pre 部署。

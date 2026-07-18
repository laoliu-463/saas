# Remote real-pre 日志诊断 Evidence

## 结论

- 状态：`PARTIAL`
- 诊断目标：确认远端 real-pre 是否出现 `skills for real engineers`，并定位同一运行窗口的真实故障。
- 阶段性结论：当前可访问的远端日志、主机日志、systemd journal 和远端源码中均未检出该精确字样；由于 backend/frontend 容器于 2026-07-17 17:49 重建、旧容器已不存在，不能据此否定更早历史日志曾出现过。
- 已定位的高可信运行故障：`OrderRefundFactSynced` 业绩调整链路写入 `performance_adjustment_ledger.input_snapshot` 时，将字符串绑定到 PostgreSQL `jsonb` 列，导致退款业绩调整持续失败。
- 独立性能问题：`domain_event_outbox` 存在约 28 万条 `PENDING` 事件，`lockPendingEvents` 的执行计划为并行顺序扫描和排序，导致每次约 1.8–2.0 秒的慢查询。

## 环境与范围

- 时间：2026-07-17 20:44（Asia/Shanghai）
- 环境：远端 real-pre，SSH 主机别名 `saas`，主机 `VM-0-12-ubuntu`
- 分支：`feature/auth-system`，远端工作区 clean，commit `1ed7dd2abef5bcce86221da06ad9db4d21c81446`
- 远端容器：backend、frontend、PostgreSQL、Redis 均 `running/healthy`
- 远端应用目录：`/opt/saas/app`
- 远端应用日志目录：`/opt/saas/logs` 当前为空

## 字样检索证据

精确、不区分大小写检索 `skills for real engineers`：

| 来源 | 结果 |
|---|---:|
| backend 容器日志 | 0 |
| frontend 容器日志 | 0 |
| PostgreSQL 容器日志 | 0 |
| Redis 容器日志 | 0 |
| `/opt/saas/logs` 与 `/var/log` | 0 |
| systemd journal（自 2026-06-01） | 0 |
| `/opt/saas/app` 远端源码 | 0 |

日志窗口限制：当前 backend/frontend 容器创建于 2026-07-17 17:49:23/33，backend 最早日志为 17:49:36；`docker ps -a` 未发现旧容器。因此只能确认当前保留窗口未出现，历史旧容器日志未保留在本机可访问范围内。

## 真实故障证据

### 1. 退款业绩调整写库失败

- PostgreSQL 重复报错：`column "input_snapshot" is of type jsonb but expression is of type character varying`。
- 数据库列事实：`public.performance_adjustment_ledger.input_snapshot` 类型为 `jsonb`。
- 失败执行台账：`OrderRefundFactSynced|FAILED|150`，另有 `OrderSynced|RUNNING|4`、`OrderSynced|SUCCEEDED|146725`。
- 150 条失败记录的 `last_error` 均指向 `PerformanceAdjustmentLedgerMapper.insert-Inline`。
- `performance_adjustment_ledger` 当前记录数为 0。
- `PerformanceRecalculateFailedJob` 每 10 分钟运行，但日志持续出现 `eventSucceeded=0`、`eventFailed>0`。

源码依赖链（远端当前 commit）：

`PerformanceRecordSyncListener.onOrderRefundFactSynced` → `PerformanceRefundAdjustmentService.recordRefund` → `PerformanceAdjustmentLedgerMapper.insert`（继承 MyBatis-Plus `BaseMapper`）→ `input_snapshot`。

对照证据：同仓库 `PerformanceRecordMapper.xml` 对另一个 JSONB 字段使用了 `CAST(#{... JacksonTypeHandler} AS JSONB)`；`PerformanceAdjustmentLedger` 仅声明 `JacksonTypeHandler`，其 Mapper 没有自定义 insert/cast。结合 PostgreSQL 错误和失败台账，当前最可信根因是通用 BaseMapper 绑定为 VARCHAR，未按 JSONB 类型发送。

### 2. Outbox 积压与慢查询

- 当前 `domain_event_outbox`：`PENDING=282124`、`PUBLISHED=334233`、`DEAD=153`。
- `PENDING` 中 `OrderSynced=281840`，`OrderRefundFactSynced=322`。
- 20 秒采样前 `PENDING=282233`，说明消费者仍在推进，但积压规模很大。
- `lockPendingEvents` 的只读 `EXPLAIN` 为 `Parallel Seq Scan` → `Sort occurred_at` → `Gather Merge`，不是有效的索引扫描；现有 `(status, occurred_at)` 索引未避免该计划。
- 该问题与 `skills for real engineers` 无直接证据关联，也不是 `input_snapshot` 类型错误的直接触发点。

### 3. 其他日志项

- 有 `role "postgres" does not exist`、SQL 类型/语法错误和少量订单同步 `failed=2`；日志内容更像人工/探针查询或数据竞争，需要单独按 request/log ID 继续定位，当前不能并入主根因。
- 订单同步日志同时出现大量 `unattributed/noMapping`，应以真实 `pick_source`、mapping 和订单事实核验，不能仅凭日志字段宣判归因代码错误。

## 运行态验证

- `GET http://127.0.0.1:8081/api/system/health`：`{"status":"UP"}`
- `HEAD http://127.0.0.1:3001/login`：`HTTP/1.1 200 OK`
- 未执行远端部署、重启、数据库写入或业务数据修复。
- 未执行构建、E2E 或真实业务闭环回归；因此不能声明问题已修复。

## 修复建议（未执行）

- 根因修复：为 `PerformanceAdjustmentLedgerMapper` 增加显式 insert/update SQL 并将 `input_snapshot` CAST 为 JSONB，或统一使用能发送 PostgreSQL JSONB 类型的项目级 TypeHandler；补充真实 PostgreSQL Mapper 集成测试和失败台账回归测试。
- 历史恢复：修复并部署后，通过现有受控 `PerformanceCalculationRetryService` 重放失败事件，验证幂等键、ledger 记录数和失败台账状态；禁止直接裸 SQL 批量改业务事实。
- Outbox 治理：基于线上 `EXPLAIN ANALYZE`、事件保留策略和消费吞吐设计合适的部分/复合索引与批处理策略，先做只读基线和回归验证，禁止直接清理 PENDING/DEAD 事件。
- 日志治理：部署前归档旧容器日志；为该精确字样、`eventFailed`、`PENDING` backlog 和 JSONB 写入异常增加可检索告警及保留期。

## 验证清单

- [ ] 后端构建通过
- [ ] JSONB Mapper 集成测试通过
- [ ] 修复后容器重启并健康检查通过
- [ ] 失败退款事件受控重试成功
- [ ] `performance_adjustment_ledger` 产生预期记录且幂等
- [ ] `performance_calculation_execution` 的失败数停止增长并可下降
- [ ] Outbox backlog、锁定查询耗时和消费吞吐达到可接受基线
- [ ] real-pre 订单/退款/业绩相关业务验证通过

## Retro

可执行改进：为 PostgreSQL JSONB 字段建立统一 Mapper/TypeHandler 约束；为远端容器重建建立日志归档步骤；增加 Outbox backlog 与失败事件的运行态指标。上述改进尚未实施，本报告只记录诊断证据。

## Git 与状态

- 本轮未修改业务代码、配置、数据库或远端服务。
- 本轮仅新增本地 evidence report；未执行 commit/push。
- 本地工作区在任务开始前已有多个 `current_task`/历史报告/临时目录 dirty 项；本轮未触碰或清理。
- `DOMAIN_STATUS.md`、项目状态快照和 Harness changelog：未更新，原因是本轮为只读运行诊断。

## Harness 门禁

- `safety-check.ps1 -Env real-pre -Scope docs`：PASS；仅检查配置项存在性，未输出密钥值。
- `check-harness-limits.ps1 -BaselineRef HEAD`：`TASK_GATE=FAIL`、`REPOSITORY_HEALTH=PARTIAL`。
- 失败项来自 `harness/reports` 的既有目录/文件数量、历史时间戳报告和超行数报告；本轮未删除或重排这些用户既有证据，故不将该门禁结果包装为 PASS。

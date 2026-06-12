# DDD-ORDER-001 — OrderSyncApplicationService

**时间**: 2026-06-10  
**环境**: local / `mvn test`  
**分支**: `feature/auth-system`  
**基线 commit**: `0498b08e`（提交前）

## 目标

为订单同步建立应用层入口，当前委派旧 `OrderSyncService`，不改变同步窗口、分页、checkpoint、锁、重试与落库行为。

## 交付物

| 类型 | 路径 |
|------|------|
| 应用服务 | `domain/order/application/OrderSyncApplicationService.java` |
| 命令 | `domain/order/application/OrderSyncCommand.java` |
| 结果 | `domain/order/application/OrderSyncResult.java` |
| 模式 | `domain/order/application/OrderSyncMode.java` |
| 时间类型 | `domain/order/application/OrderSyncTimeType.java`（`UPDATE` / `SETTLE`，与网关一致） |
| 执行上下文 | `domain/order/application/OrderSyncExecutionContext.java` |
| 定时任务接入 | `job/OrderSyncJob.java` — 受 `ddd.refactor.order-application.enabled` 控制 |
| 单元测试 | `test/.../OrderSyncApplicationServiceTest.java` |
| Job 测试 | `test/.../OrderSyncJobTest.java`（含开关 on/off） |

## Command / Result 字段

**OrderSyncCommand**: `mode`, `timeType`, `startTime`, `endTime`, `maxPages`, `maxOrders`, `operatorId`, `dryRun`

**OrderSyncResult**: `fetched`, `inserted`, `updated`, `skipped`, `failed`, `checkpointBefore`, `checkpointAfter`, `errors`, `durationMs`（另含 `toLegacySyncResult()` 供 Job 日志兼容）

## 行为说明

1. **默认路径（开关关闭）**：`OrderSyncJob` 仍直接调用 `OrderSyncService`；`OrderController` 仍调用 `syncByTimeRange`，API 不变。
2. **开关开启**：`ddd.refactor.enabled=true` 且 `ddd.refactor.order-application.enabled=true` 时，定时任务经 `OrderSyncApplicationService.execute` 委派。
3. **委派映射**：
   - `SCHEDULED` + `ExecutionContext.scheduledTask` → 对应 `syncLatestWindow` / `syncPayRecentWindow` / `syncInstituteOrdersHotRecent` 等
   - `HISTORICAL` → `syncByTimeRange(start, end)`
   - `MANUAL` → `triggerManualSync()`
   - `DRY_RUN` → 不委派旧服务，checkpoint 前后一致（旧 `OrderSyncService` 无 dry-run 路径）
4. **未改动**：分页、`time_type`、checkpoint 推进逻辑、金额映射、订单归因、`maxPages`/`maxOrders`（仍由旧服务 `@Value` 配置）。

## 入口梳理

| 入口 | 当前行为 |
|------|----------|
| `OrderSyncJob`（6 个 `@Scheduled`） | 开关 off → 旧服务；on → ApplicationService |
| `OrderController` `POST /orders/sync` | 仍直连 `OrderSyncService.syncByTimeRange` |
| `OrderSyncService` | 实际同步实现（未改） |
| `RealDouyinOrderGateway` | 网关拉单（未改） |

## 构建与测试

### 订单同步定向测试

```text
mvn test -Dtest=OrderSyncApplicationServiceTest,OrderSyncJobTest,OrderSyncServiceTest,OrderSyncControllerTest
```

| 套件 | 结果 |
|------|------|
| `OrderSyncApplicationServiceTest` | PASS (5/5) |
| `OrderSyncJobTest` | PASS (21/21) |
| `OrderSyncServiceTest` | PASS |
| `OrderSyncControllerTest` | PASS |

覆盖项：

- 历史/手动时间范围命令委派 `syncByTimeRange`
- `dryRun` 不调用旧服务、checkpoint 不变
- `scheduledPayRecent` / `scheduledSettle` 参数透传到正确 legacy 方法
- Job 开关关闭时仍 verify 旧 `OrderSyncService`
- Job 开关开启时走 `OrderSyncApplicationService`

### 全量后端（`mvn test`）

- **结论**: PARTIAL（本任务范围外既有/WIP 失败）
- 失败主因：工作区未提交的 `SampleController` / `LegacySampleQueryService` 循环依赖导致 Spring 上下文加载失败；另有 `DddConfig003ConfigRoutingTest` 2 项失败（CONFIG-003 并行 WIP）。
- 订单同步相关定向套件全部 PASS。

## 未改动（按任务约束）

- 分页逻辑、`time_type`、checkpoint、金额映射、订单归因
- `OrderSyncService` 内部实现
- 手动同步 API 响应结构（`OrderSyncService.SyncResult`）

## 剩余风险

1. `OrderController` 尚未接入 ApplicationService；后续可在同一开关下切换，需保持 `SyncResult` 兼容。
2. `maxPages` / `maxOrders` 命令字段预留，尚未透传至 legacy（避免改变运行时配置语义）。
3. `DRY_RUN` 为应用层跳过；与 `Order6468PaginationDryRunService`（6468 分页专项）无关。

## 结论

**PASS（任务范围内）** — 应用层入口与 Command/Result 已落地，定时任务可开关路由，委派行为与测试覆盖满足 DDD-ORDER-001；全量套件受仓库并行 WIP 影响记为 PARTIAL。

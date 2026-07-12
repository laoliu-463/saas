# 活动与商品同步可靠性修复设计

**日期：** 2026-07-12

## 目标

修复“活动商品同步失败”和“活动状态无法同步”两条链路。实施顺序固定为：先完成并验证全部后端修改，再修改前端；本设计不授权远端部署。

## 已确认的证据链

### 商品同步

- 最近一小时内，任务 `activity-product-sync-9e2...` 拉取 10,800 行后，在 PostgreSQL `product_operation_state` 写入阶段发生 `deadlock detected` 并失败。
- 随后的 `activity-product-sync-8829...` 在等待全局锁后成功，拉取 10,911 行并完成新增、更新，证明同一上游链路可用。
- 同期 `alliance.colonelActivityProduct` 成功 1,384 次，相关上游错误为 0。
- `ProductActivityManualSyncService` 只读取全局锁当前值，随后仅持有活动锁；检查与加锁不是原子操作。
- `ProductActivitySyncJob` 按全局锁、定时同步锁、活动锁运行，因此可以在手动任务检查后插入并与其并发。
- 两个并发活动存在 101 个重叠商品，活动级锁无法保护共享的 `product_operation_state` 行。
- `ProductService.refreshActivitySnapshots*` 的外层事务覆盖上游分页、等待、数据库写入和展示重算，扩大数据库持锁窗口。

结论：本次已观测失败由本地锁竞态和长事务共同触发，不是商品上游同步故障。

### 活动状态同步

- 页面“同步最新活动列表”当前只再次调用本地 `GET /colonel/activities`。
- 后端该 GET 接口明确只查本地数据库，不在线调用抖音上游。
- 前端声明的单活动 POST 接口既未被页面调用，后端也没有对应映射；单活动刷新即使实现，也不能发现新增活动。
- 当前 `ActivityListResult` 没有可靠的分页完成信号；真实网关只返回 `total + activityList`，且上游缺少 total 时会回退成当前页大小，可能把首屏误判成全量完成。
- `colonel_activity.last_sync_at` 同时会被商品同步更新，因此该值不能证明活动状态同步成功。
- 同期 `alliance.instituteColonelActivityList` 成功 2 次，相关上游错误为 0。

结论：活动状态问题是本地同步入口、分页和同步时间语义缺口，不是当前证据下的上游故障。

## 方案决定

采用已批准的方案 2：分阶段根因修复。

1. 后端阶段 A：先统一商品写任务的锁协议，封闭当前并发竞态。
2. 后端阶段 B：新增活动列表/状态异步同步任务和独立同步时间。
3. 后端阶段 C：缩短商品同步事务并增加数据库批次级有限重试。
4. 后端构建、容器重启、健康检查和业务验证通过后，才进入前端。
5. 前端接入异步任务、展示真实状态并刷新本地列表。

不采用同步 HTTP 全量同步，因为上游分页耗时会重新引入客户端断连和 504 风险。不在本轮建设通用同步任务平台，因为会扩大到与本故障无关的领域。

## 后端阶段 A：商品同步锁协议

### 统一锁层级

所有会写活动商品共享表的任务遵循固定顺序：

```text
PRODUCT_BACKFILL_GLOBAL
  -> PRODUCT_ACTIVITY_SYNC（仅定时任务）
  -> PRODUCT_BACKFILL_ACTIVITY:{activityId}
```

- 手动任务必须直接以唯一 owner 原子获取全局锁，不能先读取锁值再决定是否获取。
- 全局锁获取失败时，任务保持 `QUEUED`，不得继续获取活动锁或访问上游；现有 drain 机制后续重试。
- 获得全局锁后才能获取活动锁；第二把锁失败时必须按 owner 释放已获得的全局锁。
- 定时、手动、backfill、`ProductDisplayRuleJob` 和 `StaleProductSyncJobReconcileJob` 等所有全局锁持有者都使用唯一 owner；按 `activity -> sync -> global` 逆序校验 owner 后释放，避免旧任务在 TTL 过期后删除新任务持有的锁。
- `DistributedJobLockService` 的本地 fallback 也必须存储 owner；错误 owner 不得释放本地锁，测试环境不能使用比 Redis 更弱的语义。
- `GET /colonel/activities/{activityId}/products?refresh=true` 当前会绕过锁直接写库。本轮废止该 GET 的写行为：返回明确的弃用/数据未就绪错误并引导调用已有异步 POST，不允许保留未加锁写入口。
- 本轮不引入 Redis 多 key Lua 或商品级海量锁；全局入口和固定顺序已能覆盖跨活动共享商品。

### 租约边界

仅扩大固定 TTL 不能形成严格互斥。阶段 A 增加 owner-checked `renewWithOwner`，由持锁任务按租约比例续租；续租失败时停止开始新的上游页/数据库 batch，按已有提交量收口状态。锁协调器负责启动、停止 heartbeat 和逆序释放。启动配置校验保证续租间隔小于 TTL，并为续租失败写结构化日志和测试。

## 后端阶段 B：活动列表与状态异步同步

### 接口

- 对外路径 `POST /api/colonel/activities/sync`（Controller 映射 `/colonel/activities/sync`）：创建任务；存在同 scope 的 `QUEUED/RUNNING` 任务时返回该任务，不重复执行。
- 对外路径 `GET /api/colonel/activities/sync-jobs/{jobId}`（Controller 映射 `/colonel/activities/sync-jobs/{jobId}`）：查询任务状态与摘要。
- POST 正常接收返回 HTTP 202，响应至少包含 `jobId`、`syncStatus`、`reused`。
- GET 固定返回 `jobId`、`syncStatus`、`pagesFetched`、可空的 `upstreamTotal`、`fetchedRows`、`distinctActivityIds`、`invalidCount`、`upsertedCount`、`failedCount`、`startedAt`、`finishedAt` 和脱敏后的 `errorCode/errorMessage`。
- 计数不变量为 `upsertedCount + failedCount = distinctActivityIds`；原始重复项和无有效 ID 的条目不计入 distinct，后者计入 `invalidCount`。
- 权限沿用活动列表已有的 `ADMIN/BIZ_LEADER/BIZ_STAFF` 后端校验；前端不传业务状态规则或上游分页参数。
- 任务是当前配置机构的全局同步，canonical scope 固定为 `ALL`。允许访问活动列表的角色可以查询该全局任务摘要，但响应不得包含 token、请求头或未经脱敏的上游响应。

### 任务与持久化

新增专用 `colonel_activity_sync_job_log`，不复用语义明确属于商品域的 `product_sync_job_log`。字段覆盖 job id、scope、状态、请求人、时间、分页和计数摘要、错误信息；通过数据库部分唯一索引约束 `scope='ALL'` 只能存在一个活跃任务，Redis/内存检查只做快速路径。唯一索引竞争时重新查询并返回数据库中的活跃任务。

任务使用专用的单工作线程、有界队列 `TaskExecutor` 和拒绝策略，不能复用当前带 `CallerRunsPolicy` 的共享执行器，避免队列满时退化为 HTTP 同步执行。新建或复用活跃任务均返回 202；入队拒绝时先把任务落为 FAILED，再返回带 job id 和错误码的 503，不能只打印日志或返回成功。

Worker 开始执行前必须通过条件更新 `QUEUED -> RUNNING` 原子 claim，只有更新成功者可访问上游，防止多实例重复执行同一行。定时 drain 重投陈旧 QUEUED，reconcile 将无法恢复的陈旧 QUEUED/RUNNING 按已提交计数收口，覆盖“插入成功但提交线程池前进程退出”的窗口。

### 全量分页

1. 后端使用固定、受配置约束的 page size 调用 `DouyinActivityGateway.listActivities`，请求不接受前端传入 appId、状态或分页参数。
2. 扩展 Gateway 结果以保留“上游是否真的返回 total”，不能再把缺失的 total 回退成当前页大小后冒充上游总数。
3. total 存在时，以它和累计拉取数决定是否继续；当前契约没有可靠的 `hasMore`，不得在实现中自行假定首屏即末页。
4. total 缺失时按页继续到短页/空页，并设置最大页数和最大行数安全上限；首个满页绝不能被判为全量 SUCCESS，触发上限时标记 PARTIAL/FAILED。
5. 对 activity id 去重；每页上游请求在数据库事务之外执行。
6. 每页在独立短事务中 upsert 活动名称、时间窗口、状态码和状态文案，并返回真实 commit 计数。
7. 在累计拉取数达到 total 前遇到空页、页码不前进或结构异常，任务不得标记 SUCCESS。

### 状态语义

- `SUCCESS`：分页协议完整结束且所有有效 distinct activity 已处理，包括上游可靠返回 `total=0` 的合法空结果。
- `PARTIAL`：发生错误或分页不完整，且数据库 `upsertedCount > 0`。
- `FAILED`：完成前发生错误或分页不完整，且数据库 `upsertedCount = 0`。
- `QUEUED/RUNNING`：非终态；reconcile 陈旧任务时按 `upsertedCount > 0 ? PARTIAL : FAILED` 收口，并写入 `STALE_RECONCILED/PROCESS_RESTARTED` 原因。

`upsertedCount` 表示事务成功提交并完成处理的有效活动数，包含新增、更新和内容未变化三类；数据库 no-op 不能被误算为失败。

### 同步时间语义

- 为实际表 `colonel_activity` 增加可空列 `activity_status_synced_at`，同步更新实体、Mapper、列表 DTO/映射，`GET /colonel/activities` 的每行响应使用 `activityStatusSyncedAt`。
- 新增活动状态专用 upsert 方法，只有全量活动列表任务可以调用并更新 `activity_status_synced_at`；它不得写 `last_sync_at`。
- 商品同步现有的单活动摘要刷新保留原 mapper 路径，只维护 `last_sync_at` 和商品 sync state，绝不调用活动状态专用 upsert，避免两类调度时间继续相互污染。
- 现有 `last_sync_at` 暂时保留以兼容商品同步和已有查询，但不再作为“活动状态已同步”的 UI 或验收证据；即使它比新字段更新，前端也不得 fallback。
- 历史记录不回填新字段，避免把无法证明的旧时间包装成成功；第一次真实活动状态同步后自然补齐。

数据库变更均为新增表、可空列和索引，不改写历史业务数据。

## 后端阶段 C：商品同步事务与死锁治理

- 移除所有 `refreshActivitySnapshots*` 和 `refreshActivitySnapshotsByStatusPartitions*` 重载的外层长事务，上游 fetch 和分页等待全部在事务外。
- 每页数据按稳定的 `productId` 顺序处理，并按约 100 条拆成数据库 batch。
- 每个 batch 通过独立 Spring Bean 或显式 `REQUIRES_NEW TransactionTemplate` 使用新事务，禁止在 `ProductService` 内部自调用导致事务代理失效；复用 backfill 已验证的批次写入和异常分类能力。
- 仅对 `DeadlockLoserDataAccessException`、`CannotAcquireLockException`、SQLSTATE `40P01/55P03` 最多重试 3 次并加入 jitter。
- 每次重试必须开启新事务，且只重放当前数据库 batch，绝不重新请求已成功返回的上游页面。
- 手动商品任务已提交部分后再失败时，在 `product_sync_job_log` 标记 `PARTIAL` 并保留准确计数；定时 `ProductActivitySyncJob` 当前没有任务行，只记录结构化的逐活动和聚合 PARTIAL 日志，本轮不伪造可查询状态。
- 后处理矩阵固定为：完整 SUCCESS 才允许全量 stale delete；上游 PARTIAL 或 DB PARTIAL 禁止 stale delete，只对已提交 product id 做 repair/展示重算并发布 PARTIAL 事件；零提交 FAILED 不做 repair、展示重算或 stale delete，只发布失败证据。测试保护每类调用条件和次数。

该阶段影响半径高，必须与锁修复分开提交和验证，以便独立回滚。

## 前端设计

- 保留“刷新数据”为纯本地 GET，并把“同步最新活动列表”改为提交异步任务。
- `activity.ts` 提供有类型的 trigger/getJob API，删除或重定义当前无后端契约的死接口。
- 页面使用独立 `syncingActivities` 和 job state，不复用列表 loading；重复点击时禁止再次提交。
- 轮询展示 QUEUED/RUNNING；SUCCESS 后回到第一页并刷新本地列表；PARTIAL 显示警告并刷新已落库数据；FAILED 明确失败，不能 toast 成功。
- 轮询复用现有商品任务节奏：前 10 次每秒、接下来 20 次每 3 秒、之后每 10 秒，最多 89 次；连续 3 次网络/5xx 错误或达到次数上限后进入前端 `BACKGROUND` 展示态，提示后台可能仍在运行，不能篡改后端状态。
- 401/403 立即停止并提示权限失败，404 立即停止并提示任务不存在；SUCCESS/PARTIAL 都保留筛选、回到第一页并刷新，FAILED 不刷新。同步成功但列表 GET 失败时单独提示，不能反向修改 job 状态。
- 页面提供可测试的内联 job 状态、进度和错误摘要，toast 只作辅助。`reused=true` 时直接用返回的 job id 恢复轮询；若 POST 已返回终态则走同一终态处理。
- 组件卸载时清理 timer，并使用 disposed/generation token 防止在途 GET 返回后再次续约或刷新。页面的“活动状态同步时间”只读取 `activityStatusSyncedAt`。
- 第一版不要求跨浏览器刷新恢复轮询；后端返回活跃任务保证用户重新点击时可恢复查询。

## TDD 与验收证据

### 后端先写失败测试

- 手动任务按 `global -> activity` 获取锁；全局锁失败时保持 QUEUED 且不碰活动锁。
- 第二把锁失败或同步异常时，仅按相同 owner 释放已持有锁。
- 定时任务以同一 owner 按固定层级获取、续租和逆序释放；Redis 与本地 fallback 均拒绝错误 owner 释放/续租。
- legacy `GET .../products?refresh=true` 不再直接写库，并返回异步 POST 指引。
- 活动同步重复提交只产生一个活跃任务；多实例只允许一个 worker 原子 claim；线程池拒绝和陈旧 QUEUED 不产生假成功。
- `total` 跨多页时完整翻页；提前空页产生 PARTIAL/FAILED；缺失 total 且首屏满页时继续翻页而非 SUCCESS。
- 合法 `total=0` 为 SUCCESS；SUCCESS、PARTIAL、FAILED 计数和错误摘要准确。
- 商品同步不会更新 `activity_status_synced_at`。
- 数据库死锁只重放当前 batch，使用新事务且不重新拉取上游。

### 前端后写失败测试

- 点击同步按钮真实 POST，而不是仅执行列表 GET。
- 覆盖 QUEUED、RUNNING、SUCCESS、PARTIAL、FAILED、重复点击和卸载清理。
- SUCCESS/PARTIAL 后刷新列表，FAILED 不显示成功。
- 展示使用 `activityStatusSyncedAt`，不能回退到商品 `lastSyncAt` 冒充状态同步时间。
- 覆盖 reused 恢复、轮询间隔/上限、连续网络错误、401/403/404/5xx，以及卸载时仍有请求在途的场景。

### 工程验证

每个代码阶段执行定向测试；后端 A/B/C 完成后先执行项目规定的 `agent-do.ps1 -Env real-pre -Scope backend`，实际构建、重启后端容器、健康检查和后端业务验证，通过后才修改前端。前端完成后再执行 `Scope full`。业务验证至少保留：锁排队/释放/续租证据、活动任务 POST 与轮询证据、分页/计数、数据库时间字段、容器日志中无新增死锁。

真实上游或凭证不可用时必须标记 BLOCKED/PARTIAL，不能用 mock 证明 real-pre 闭环。最终生成 evidence report、retro summary、Harness 限制检查，并进行代码审查后提交和推送当前分支。

## 风险与回滚

- 全局互斥会降低不同活动商品同步的并发吞吐，但优先保证共享商品写入正确性。
- 分批事务把“整活动回滚”改为“已提交批次保留并标 PARTIAL”；前后端和审计均必须接受并展示该事实。
- 新 job 表和同步时间列是向后兼容的增量迁移。应用回滚时先回滚前端，再逐阶段回滚后端提交；新增空表/可空列可暂留，禁止为回滚删除 real-pre 数据。
- 锁修复、活动异步任务、事务重构、前端接入分别提交，任一阶段失败可独立 revert 并重启验证。
- 工作区已有与本任务无关的用户修改；所有提交只包含本任务文件，不覆盖或夹带现有改动。

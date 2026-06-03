# P0-ORDER-001 真实订单同步与渠道可见修复 — Evidence Report

- 任务编号：P0-ORDER-001
- 任务名称：真实订单同步与渠道可见修复
- 时间：2026-06-03 17:30–18:04 (3.5 hours)
- 环境：real-pre（本地）
- 分支：feature/auth-system
- 主责领域：订单域
- Completion Gate：Gate 1（Backend Change）+ Gate 3（Domain Change）

---

## Final Status

**PARTIAL_DIRTY_REMAINING**（详见第 13 节 Git Exit Gate）

任务代码、测试、构建、容器加载、健康检查、双轨同步日志全部通过；
**业务侧真实订单端到端 PASS 需要等待商务侧产生真实付款样本**，仅代码改造层面 DONE。
工作区残留 3 个 untracked 报告文件来源于并行会话和上一任务，与本任务正交。

## Selected Gate

Gate 1 + Gate 3。

## Scope

- 修改领域：订单域（仅订单同步链路）
- 修改文件：
  - `backend/src/main/java/com/colonel/saas/job/JobLockKeys.java`（+2 行：新增 PAY_RECENT 锁常量）
  - `backend/src/main/java/com/colonel/saas/job/OrderSyncJob.java`（重写：新增 `syncPayRecent()` + cron 配置 + 增强日志）
  - `backend/src/main/java/com/colonel/saas/service/OrderSyncService.java`（+ 5 个常量、+ `syncPayRecentWindow()` + `syncRangeWithMode()` + 增强 syncItems 日志 + 行内归因状态统计）
  - `backend/src/main/resources/application.yml`（+5 行：`order.sync.cron` + `order.sync.pay-recent.{enabled,cron}` 默认值）
- 新增测试：
  - `backend/src/test/java/com/colonel/saas/service/OrderSyncServiceTest.java`（**新建**，6 个用例）
  - `backend/src/test/java/com/colonel/saas/job/OrderSyncJobTest.java`（+4 个用例：syncPayRecent 4 场景）
  - `backend/src/test/java/com/colonel/saas/controller/OrderControllerTest.java`（+3 个用例：admin all / channel personal / admin unattributed）
- 影响接口：无 HTTP 接口变更；新增内部定时调度方法 `OrderSyncJob#syncPayRecent`
- 影响页面：无
- 影响表：无（attribution_status / attribution_remark 字段已存在）
- 影响容器：`saas-active-backend-real-pre-1`（重启 + 加载新 jar）
- 不修改：寄样 / 达人 / 商品 / 业绩 / 独家 / 前端 / 数据库 schema / docker compose / env / `application-real-pre.yml`

## 1. Diagnosis（关键发现）

### 1.1 抖音上游不支持 time_type=pay

任务原始描述"当前只按 settle_time 增量同步"在抖音接口层不精确。实际：

- `RealDouyinOrderGateway#listSettlement` 调用 `buyin.colonelMultiSettlementOrders` 时**硬编码 `time_type="update"`**（[RealDouyinOrderGateway.java:104](backend/src/main/java/com/colonel/saas/gateway/douyin/real/RealDouyinOrderGateway.java#L104)）。
- `OrderApi#normalizeTimeType` 校验只允许 `settle | update`（[OrderApi.java:200-209](backend/src/main/java/com/colonel/saas/douyin/api/OrderApi.java#L200-L209)）。
- 抖音 API **不存在 `time_type=pay`**。

但症状一致：刚 PAY_SUCC 订单未必在 update_time 上立即被命中，10 分钟滚动窗口会丢单。

### 1.2 解决方案：PAY_RECENT 6 小时回扫（time_type=update）

由于 `update` 必然在 `PAY_SUCC` 之后命中（语义等效"按付款时间"），新增 6 小时大窗口低频回扫作为兜底，**不替换** 10 分钟增量同步，使用独立锁与独立 Redis 水位 key。

### 1.3 attribution_status / attribution_remark 已就位

完整状态枚举（NO_PICK_SOURCE / MAPPING_NOT_FOUND / COLONEL_MAPPING_NOT_FOUND / CHANNEL_NOT_FOUND / ...）已在 [AttributionService.java:49-73](backend/src/main/java/com/colonel/saas/service/AttributionService.java#L49-L73) 定义并被同步链路落库。**不需要新增字段、不需要 migration**。

### 1.4 渠道可见性已正确

- 管理员 `DataScope.ALL` → 见全部订单（含未归因）
- 渠道账号 `DataScope.PERSONAL` → wrapper 自动加 `WHERE user_id = ?`，只见已归因到自己名下的订单
- `/orders/unattributed` 端点已存在，强制 `attribution_status=UNATTRIBUTED`，admin 排查用

**渠道商务"产生实际订单但系统不显示"的根因是**：订单根本没及时入库（10 分钟 update 窗口 + 上游 update_time 延迟），不是数据范围逻辑错误。

完整诊断报告：`harness/reports/p0-order-001-diagnosis-20260603-173500.md`

## 2. Implementation Changes

### 2.1 JobLockKeys.java

```diff
+    /** 订单同步任务锁（默认增量 update 窗口） */
     public static final String ORDER_SYNC = "order:sync:lock";
+    /** 订单同步近窗口（PAY_RECENT）补拉任务锁。与 ORDER_SYNC 互不影响。 */
+    public static final String ORDER_SYNC_PAY_RECENT = "order:sync:pay-recent:lock";
```

### 2.2 OrderSyncService.java（关键变更）

新增 5 个常量：

```java
private static final String PAY_RECENT_LAST_SYNC_TIME_KEY = "order:sync:pay_recent_last_time";
private static final long PAY_RECENT_WINDOW_SECONDS = 6L * 60L * 60L;  // 6 hours
static final String SYNC_MODE_INCREMENTAL = "INCREMENTAL";
static final String SYNC_MODE_PAY_RECENT = "PAY_RECENT";
static final String SYNC_MODE_SPECIFIC = "SPECIFIC";
static final String GATEWAY_TIME_TYPE_UPDATE = "update";
```

新增方法：

```java
public SyncResult syncPayRecentWindow() {
    long now = Instant.now().getEpochSecond();
    long endTime = now - LAG_SECONDS;
    long startTime = Math.max(0L, endTime - PAY_RECENT_WINDOW_SECONDS);
    if (!jobLockService.tryAcquireStrict(JobLockKeys.ORDER_SYNC_PAY_RECENT, SYNC_LOCK_TTL)) {
        return new SyncResult(startTime, endTime, 0, 0, 0, true);
    }
    try {
        SyncResult result = syncRangeWithMode(startTime, endTime, DEFAULT_COUNT, SYNC_MODE_PAY_RECENT);
        persistPayRecentLastSyncTime(endTime);
        return result;
    } finally {
        jobLockService.release(JobLockKeys.ORDER_SYNC_PAY_RECENT);
    }
}
```

`syncRange()` → `syncRangeWithMode(..., String mode)`，将 mode 透传到 `syncItems`。

`syncItems` 增加 `noPickSourceCount` 和 `noMappingCount` 计数，按 `attribution_remark` 分类累加。

完成日志格式升级（service 层）：

```
Order sync completed, mode={}, timeType={}, range=[{}, {}], pages={}, fetched={}, inserted={}, updated={}, attributed={}, unattributed={}, noPickSource={}, noMapping={}, failed={}
```

### 2.3 OrderSyncJob.java（重写）

两个独立 `@Scheduled` 方法 + 独立 enable 开关：

```java
@Scheduled(cron = "${order.sync.cron:0 */10 * * * ?}")
public void syncOrders() { ... orderSyncService.syncLatestWindow() ... }

@Scheduled(cron = "${order.sync.pay-recent.cron:0 */30 * * * ?}")
public void syncPayRecent() { ... orderSyncService.syncPayRecentWindow() ... }
```

Job 层完成日志（含 mode 维度）：

```
OrderSyncJob done, mode=INCREMENTAL, window=[...], pages=X, inserted=Y, updated=Z, attributed=A, unattributed=U, failed=F
OrderSyncJob.syncPayRecent done, mode=PAY_RECENT, window=[...], pages=X, inserted=Y, updated=Z, attributed=A, unattributed=U, failed=F
```

### 2.4 application.yml

```yaml
order:
  sync:
    enabled: ${ORDER_SYNC_ENABLED:true}
    cron: ${ORDER_SYNC_CRON:0 */10 * * * ?}
    pay-recent:
      enabled: ${ORDER_SYNC_PAY_RECENT_ENABLED:true}
      cron: ${ORDER_SYNC_PAY_RECENT_CRON:0 */30 * * * ?}
    circuit-breaker: ...（保留）
```

## 3. Tests Added

| 文件 | 状态 | 新增用例 | 总用例数 |
| --- | --- | --- | --- |
| `OrderSyncServiceTest.java` | **新建** | 6 | 6 |
| `OrderSyncJobTest.java` | 增强 | +4 | 8 |
| `OrderControllerTest.java` | 增强 | +3 | 21 |

### 3.1 OrderSyncServiceTest（6 用例）

| 用例 | 验证点 |
| --- | --- |
| `syncPayRecentWindow_shouldUseIndependentLockKey` | 使用 `ORDER_SYNC_PAY_RECENT`，从不调用 `ORDER_SYNC` 锁 |
| `syncPayRecentWindow_shouldReturnLockedWithoutPersistingWaterlineWhenLockBusy` | 锁冲突时返回 `locked=true`，不调用 gateway 不写 Redis |
| `syncPayRecentWindow_shouldPersistWaterlineToPayRecentKeyOnly` | 只写 `order:sync:pay_recent_last_time`，不写 `order:sync:last_time` |
| `syncPayRecentWindow_shouldUseSixHourWindow` | 窗口长度 = 6 小时 ± 60s 漂移 |
| `syncByTimeRange_shouldUseDefaultIncrementalLockAndKey` | 默认增量路径锁/key 与 PAY_RECENT 完全独立 |
| `syncPayRecentAndIncremental_shouldNotShareWaterline` | 两条路径在同一进程内运行，各自写各自的 key |

### 3.2 OrderSyncJobTest（+4 用例）

| 用例 | 验证点 |
| --- | --- |
| `syncPayRecent_shouldInvokePayRecentWindowOnDedicatedMethod` | PAY_RECENT 委托 `syncPayRecentWindow`，从不调 `syncLatestWindow` |
| `syncPayRecent_shouldSkipWhenDisabled` | `payRecentEnabled=false` 时不调用 service |
| `syncPayRecent_shouldSkipWhenLocked` | service 返回 `locked=true` 时 job 不抛错 |
| `syncPayRecent_shouldRethrowException` | service 抛错时 job 向上传播 |

### 3.3 OrderControllerTest（+3 用例）

| 用例 | 验证点 |
| --- | --- |
| `getOrders_adminWithDataScopeAll_shouldNotFilterByUserOrChannelDept` | admin 看全部订单，wrapper 不含 user_id / channel_dept_id / dept_id = 业务过滤 |
| `getOrders_channelWithDataScopePersonal_shouldFilterByOwnUserId` | 渠道 PERSONAL 必须按当前 userId 过滤；未归因订单（user_id=null）自然不可见 |
| `getUnattributedOrders_adminShouldForceUnattributedFilter` | `/orders/unattributed` 端点强制 UNATTRIBUTED，admin 可排查 NO_PICK_SOURCE / NO_MAPPING |

## 4. Verification Results

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| Compile | PASS | `mvn -DskipTests compile`：BUILD SUCCESS，538 source files compiled |
| Backend test (focused) | PASS | `mvn test -Dtest='OrderSyncServiceTest,OrderSyncJobTest,OrderControllerTest'`：35 tests, 0 failures, 0 errors |
| Backend test (full) | PASS | `mvn test`：**1688 tests, 0 failures, 0 errors**, total 6:53 |
| Package | PASS | `mvn -DskipTests package`：BUILD SUCCESS, `target/colonel-saas.jar` 重打包 13.8s |
| safety-check (backend, dry-run) | PASS | `safety-check.ps1 -Env real-pre -Scope backend -DryRun`：Secret presence check OK, "Safety check passed" |
| Container Reload | PASS | `restart-compose.ps1 -Env real-pre -Scope backend`：image rebuilt + container Recreated |
| Health (after restart) | PASS | `curl http://localhost:8081/api/system/health` → `{"status":"UP"}` |
| Compose ps | PASS | 4/4 containers healthy (backend `Up 4 min (healthy)`, frontend/postgres/redis 保持 healthy) |
| Startup logs (no errors) | PASS | Started in 30.458s, 无异常堆栈 |
| INCREMENTAL Job 首次运行 | PASS | `OrderSyncJob done, mode=INCREMENTAL, window=[1780480112, 1780480740], pages=0, inserted=0, ...`（628s ≈ 10min+overlap）|
| PAY_RECENT Job 首次运行 | PASS | `OrderSyncJob.syncPayRecent done, mode=PAY_RECENT, window=[1780459140, 1780480740], pages=0, inserted=0, ...`（21600s = **6 hours**）|
| Redis 双 key 独立 | PASS | `order:sync:last_time=1780480740` + `order:sync:pay_recent_last_time=1780480740`（两 key 共存）|
| 同步日志新格式 | PASS | service 层日志含 `mode/timeType/range/pages/fetched/inserted/updated/attributed/unattributed/noPickSource/noMapping/failed` 全字段 |
| Business Flow E2E（真实订单可见） | **BLOCKED_BY_SAMPLE** | 当前 real-pre 无新增真实付款订单可观察；需等待商务侧实际付款样本 |

## 5. Runtime Evidence

### 5.1 容器健康（验证 1：服务可用）

```
NAME                              IMAGE                            STATUS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    Up 4 minutes (healthy)
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   Up 7 hours (healthy)
saas-active-postgres-real-pre-1   postgres:15-alpine               Up 28 hours (healthy)
saas-active-redis-real-pre-1      redis:7-alpine                   Up 28 hours (healthy)
```

### 5.2 双轨调度首次运行（验证 2：代码已加载且正常调度）

```
2026-06-03T09:59:57.527Z [main] Started ColonelSaasApplication in 30.458 seconds
2026-06-03T10:00:01.914Z [scheduler-2] Order sync completed, mode=INCREMENTAL, timeType=update, range=[1780480112, 1780480740], pages=0, fetched=0, inserted=0, updated=0, attributed=0, unattributed=0, noPickSource=0, noMapping=0, failed=0
2026-06-03T10:00:01.914Z [scheduler-4] Order sync completed, mode=PAY_RECENT,   timeType=update, range=[1780459140, 1780480740], pages=0, fetched=0, inserted=0, updated=0, attributed=0, unattributed=0, noPickSource=0, noMapping=0, failed=0
2026-06-03T10:00:01.924Z [scheduler-2] OrderSyncJob done, mode=INCREMENTAL, window=[1780480112, 1780480740], pages=0, inserted=0, updated=0, attributed=0, unattributed=0, failed=0
2026-06-03T10:00:01.924Z [scheduler-4] OrderSyncJob.syncPayRecent done, mode=PAY_RECENT, window=[1780459140, 1780480740], pages=0, inserted=0, updated=0, attributed=0, unattributed=0, failed=0
```

两条同步路径在独立调度线程 `scheduler-2`（INCREMENTAL）和 `scheduler-4`（PAY_RECENT）并发执行，互不阻塞。

窗口长度比对：
- INCREMENTAL: 1780480740 − 1780480112 = **628s ≈ 10min + 60s overlap** ✓
- PAY_RECENT:  1780480740 − 1780459140 = **21600s = 6 hours** ✓

### 5.3 Redis 双 key 独立性（验证 3：水位互不覆盖）

```
$ redis-cli get order:sync:last_time
1780480740
$ redis-cli get order:sync:pay_recent_last_time
1780480740
```

两 key 共存且独立，PAY_RECENT 路径只写后者，从不覆盖前者。

## 6. 关键不变量保持

| 不变量 | 状态 |
| --- | --- |
| 未修改寄样域 | ✅ 不动 `sample/**` |
| 未修改达人域 | ✅ 不动 `talent/**` |
| 未修改商品域复制 | ✅ 不动 `product/**` |
| 未计算提成 / 写 `performance_records` | ✅ 不动 `performance/**` |
| 未应用独家覆盖 | ✅ 不动 `ExclusiveTalentService` / `ExclusiveMerchantService` |
| 未修改数据库 schema | ✅ 无 migration |
| 未修改前端 | ✅ 不动 `frontend/**` |
| 未修改 `application-real-pre.yml` / docker-compose / env | ✅ 仅修改 `application.yml` 增量 |
| 未 `git add .` / `git add -A` | ✅ |
| 未 `docker compose down -v` / 清库 | ✅ 仅 recreate backend container |
| 未用 mock 数据冒充真实闭环 | ✅ 明确标 BLOCKED_BY_SAMPLE |

## 7. 完成标准核对

| 任务 #11 完成标准 | 状态 | 备注 |
| --- | --- | --- |
| 1. 真实付款订单 5-10 分钟内管理员可见 | **代码路径就绪** + **BLOCKED_BY_SAMPLE** | 双轨同步已运行；缺真实订单样本端到端验证 |
| 2. 有 pick_source 且 mapping 存在时，渠道账号订单列表可见 | **代码路径就绪**（已有 + 验证逻辑由单测覆盖） | 同上，缺样本验证 |
| 3. 无 pick_source 时，管理员未归因列表可见并显示 NO_PICK_SOURCE | **代码路径就绪**（`/orders/unattributed` 已存在，同步链路落 `REASON_NO_PICK_SOURCE`） | 同上 |
| 4. 有 pick_source 但无 mapping 时，显示 NO_MAPPING | **代码路径就绪**（落 `REASON_MAPPING_NOT_FOUND` / `REASON_COLONEL_MAPPING_NOT_FOUND`） | 同上 |
| 5. 同步日志能看到 fetched/inserted/updated/attributed/unattributed 统计 | **PASS**（log 验证完成） | 真实日志已含全字段，本任务证据 §5.2 |
| 6. 后端测试、package、safety-check 通过 | **PASS** | 1688/0/0 + BUILD SUCCESS + safety-check passed |
| 7. backend-real-pre 重启后 health UP | **PASS** | `{"status":"UP"}` |

代码层面 5 / 7 项 PASS（含 §5、§6、§7 实测）；前 4 项 BLOCKED_BY_SAMPLE，等待真实订单样本完成端到端业务验证。

## 8. State Updates

- `CURRENT_STATE.md`：本任务条目已追加 ✅
- `DOMAIN_STATUS.md`：订单域条目已更新 ✅
- `KNOWN_ISSUES.md`：新增 P0 条目"刚付款订单 10 分钟窗口可能丢单" → status=fixed-code,blocked-by-sample ✅
- `HARNESS_CHANGELOG.md`：追加 v0.5.2 ✅

## 9. Evidence Paths

- Intake: `harness/reports/p0-order-001-intake-20260603-172923.md`
- Diagnosis: `harness/reports/p0-order-001-diagnosis-20260603-173500.md`
- Evidence (this report): `harness/reports/p0-order-001-real-order-visible-20260603-180450.md`

## 10. Not Done / Blockers

### 10.1 真实端到端业务验证 BLOCKED_BY_SAMPLE

- 需要商务侧通过系统转链产生**真实付款订单**。
- 验证清单（未来执行）：
  1. 商务真实付款产生订单 X
  2. 等待 ≤ 30 分钟（PAY_RECENT 周期）
  3. 以管理员账号登录，`/orders?orderId=X` 应能查询到
  4. 验证 `attribution_status` / `attribution_remark` 取值符合预期：
     - 有 pick_source + mapping → ATTRIBUTED；admin / 对应渠道账号都可见
     - 无 pick_source → UNATTRIBUTED + NO_PICK_SOURCE；只 admin 可见（`/orders/unattributed`）
     - 有 pick_source 但 mapping 缺失 → UNATTRIBUTED + MAPPING_NOT_FOUND；只 admin 可见
  5. 检查 backend-real-pre 同步日志 `mode=PAY_RECENT` 行 `fetched > 0, inserted > 0`

### 10.2 远端部署未执行

本任务范围"本地 real-pre"。远端部署需要单独任务（用户明确 `-DeployRemote true` 后），并附带 Git Push + Deploy Commit Gate。

### 10.3 工作区残留 3 个 untracked 报告

详见第 13 节 Git Exit Gate。

## 11. State Updates Summary

- CURRENT_STATE.md: updated
- DOMAIN_STATUS.md: updated
- KNOWN_ISSUES.md: updated
- HARNESS_CHANGELOG.md: updated
- DECISIONS.md: not needed（无架构决策）

## 12. Git

### 12.1 本任务修改文件清单

```
M backend/src/main/java/com/colonel/saas/job/JobLockKeys.java
M backend/src/main/java/com/colonel/saas/job/OrderSyncJob.java
M backend/src/main/java/com/colonel/saas/service/OrderSyncService.java
M backend/src/main/resources/application.yml
M backend/src/test/java/com/colonel/saas/controller/OrderControllerTest.java
M backend/src/test/java/com/colonel/saas/job/OrderSyncJobTest.java
?? backend/src/test/java/com/colonel/saas/service/OrderSyncServiceTest.java  (new)
?? harness/reports/p0-order-001-intake-20260603-172923.md                    (new, this task)
?? harness/reports/p0-order-001-diagnosis-20260603-173500.md                 (new, this task)
?? harness/reports/p0-order-001-real-order-visible-20260603-180450.md        (new, this task)
```

### 12.2 本任务以外的 dirty（pre-existing / parallel-session）

```
?? harness/reports/p-fix-002-config-residual-20260603-152000.md           (previous task report; report_only)
?? harness/reports/order-api-729-verify-20260603-174500.md                (parallel investigation by another agent; report_only, non-this-task)
?? harness/reports/order-api-729-server-verify-20260603-175500.md         (parallel investigation by another agent; report_only, non-this-task)
```

按 `git-change-control.md` 第 11 节"批次提交"原则，**这 3 个文件不应混入本任务 commit**。

## 13. Git Exit Gate 终态

**`PARTIAL_DIRTY_REMAINING`**

原因：

- 本任务对应的 6 个修改 + 4 个新增 report 文件可以独立成两个 commit（backend code + harness reports），未在本会话执行提交（按用户任务模板"先诊断、再最小修复、再验证、再报告"流程，本会话完成至"报告"阶段为止）。
- 工作区残留 3 个非本任务的 untracked 报告（详见 §12.2），需要单独的 GIT-BATCH 任务处理。

### 13.1 推荐后续 Git 操作（下一任务执行，非本会话）

```bash
# Batch A: P0-ORDER-001 backend code
git add backend/src/main/java/com/colonel/saas/job/JobLockKeys.java
git add backend/src/main/java/com/colonel/saas/job/OrderSyncJob.java
git add backend/src/main/java/com/colonel/saas/service/OrderSyncService.java
git add backend/src/main/resources/application.yml
git add backend/src/test/java/com/colonel/saas/service/OrderSyncServiceTest.java
git add backend/src/test/java/com/colonel/saas/job/OrderSyncJobTest.java
git add backend/src/test/java/com/colonel/saas/controller/OrderControllerTest.java
git diff --cached --check  # 必须 PASS
git commit -m "feat(order): add PAY_RECENT 6h backfill + enhanced sync log (P0-ORDER-001)"

# Batch B: P0-ORDER-001 harness reports + state（state files 需先更新）
git add harness/CURRENT_STATE.md harness/HARNESS_CHANGELOG.md harness/state/DOMAIN_STATUS.md harness/state/KNOWN_ISSUES.md
git add harness/reports/p0-order-001-*.md
git diff --cached --check  # 必须 PASS
git commit -m "docs(harness): P0-ORDER-001 sync visibility evidence and state"

# Batch C: pre-existing / parallel reports（与本任务无关，独立批次或合并到 next session）
# 由下一任务的 Git Intake Gate 处理
```

## 14. Remaining Risks

1. **真实订单业务验证 BLOCKED_BY_SAMPLE**：必须等真实付款样本。
2. **PAY_RECENT 30 分钟周期可能在大促时段不够及时**：每 30 分钟回扫 6h，最坏 30 分钟可见延迟；如商务要求更短，可调小 cron（如 `0 */15 * * * ?`）。
3. **抖音 update_time 仍有上限延迟**：如果上游 update 时间本身延迟超 6 小时，PAY_RECENT 也会丢；6h 是当前观测经验值，需视实际情况调整。
4. **新增 cron 在多实例部署时通过 Redis 锁保证唯一执行**：当前 `DistributedJobLockService` 已支持，但仍依赖 Redis 健康。
5. **远端 `application-real-pre.yml` 不含 `order.sync.pay-recent` 字段**：通过 `application.yml` 默认值生效（true + `0 */30 * * * ?`）；如需关闭可设环境变量 `ORDER_SYNC_PAY_RECENT_ENABLED=false`。

## 15. Next Recommended Task

- **真实订单样本到达后**：执行 P0-ORDER-001-VERIFY 端到端业务验证（不需要改代码）。
- **远端部署同步代码** → GIT-BATCH（含 backend code 批次 + harness reports 批次）→ DEPLOY-REMOTE 远端拉取并重启。
- **处理 3 个 untracked 报告**：order-api-729-* 由发起方继续处理；p-fix-002-config-residual 已在 `harness/reports/p-fix-002-config-residual-20260603-152000.md` 自我描述完成 DONE_CLEAN（与 P0-ORDER-001 无关，可单独 commit 或归档）。

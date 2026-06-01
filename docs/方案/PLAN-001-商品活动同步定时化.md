# PLAN-001 商品活动同步定时化

> 范围标记：`V1 必做`、`V1 简化`、`V1 不做`、`V2 预留`、`历史归档`。
> 状态：**待评审**（方案稿，本轮不实施——CLAUDE.md L58「本轮只允许文档和 `.claude/` 工程化文档变更」）。

---

## 一、背景

- [V1 必做] 商品域 6/1 重审（`docs/V1-商品域现状审计.md`）明确：商品**入库条件已自动化**（`ProductService.applyUpstreamProductLibraryDecision:1419-1468`），但**上游拉取（活动商品同步）无定时任务**。
- [V1 必做] 当前触发上游拉取的路径仅两条：
  - 用户在活动商品列表点「刷新」按钮 → `ColonelActivityController.listProducts:197` `refresh=true` → `refreshActivitySnapshots`
  - 用户查询活动商品列表 + 本地无快照 → `ColonelActivityController.listProducts:208` lazy `upsertSnapshots`
- [V1 必做] 全仓 18 个 `@Scheduled` job（grep `backend/src/main/java/.../job/*.java`）中**没有**定时拉活动商品的任务；最近邻的 `ProductDisplayRuleJob`（每小时 15 分）只做展示对账，不拉上游；`ColonelPartnerSyncJob`（每小时 30 分）只同步合作方。
- [V1 必做] 后果：抖店后台改商品状态（推广中 ↔ 终止 ↔ 待审核）系统感知不到；`selected_to_library=true` 长期停留；商品库筛选（`getSelectedLibraryPage`）不按上游 status 过滤，会展示已"终止"的商品。
- [V1 简化] 现有 V1 范围表 `docs/01-V1交付范围与边界.md` 把 P-12「商品详情定时同步」明确标为 V1 简化/波 2；本方案与 P-12 不完全等同（针对活动商品列表级同步，不针对商品详情价格/佣金），但 V1 范围内同样以"简化接受"姿态处理。
- [V2 预留] 本方案若 V1 评审通过作为 V1 必做的兜底，V2 范围可演化为 webhook + MQ 化事件。

---

## 二、问题

[V1 必做] **实时性 vs 抖店 QPS 限制 vs V1 范围裁剪** 三角难平衡：

1. **实时性诉求**：商品状态变更后系统应在合理时间窗口（建议 ≤ 30 分钟）内反映到本地。
2. **抖店 buyin 接口限流**：未公开文档但社区经验 QPS ≤ 10；real-pre 必须遵守。
3. **V1 范围裁剪**：CLAUDE.md L58 当前轮次不允许改业务代码；本方案即使评审通过，最早也只能在下轮次实施。

---

## 三、推荐路径

[V1 必做] **新增一个 `ProductActivitySyncJob`**，按活动粒度周期拉上游，触发已有的 `applyUpstreamProductLibraryDecision` 自动入库/下架决策。**不引入新业务逻辑**，仅复用现有链路。

### 3.1 改动清单

| # | 类型 | 路径 | 改动 |
|---|------|------|------|
| 1 | 新增 | `backend/src/main/java/com/colonel/saas/job/ProductActivitySyncJob.java` | 新建 job 类，抄 `ProductPinCleanupJob.java` 模板：cron + Redis 分布式锁 + try/finally release |
| 2 | 修改 | `backend/src/main/java/com/colonel/saas/job/JobLockKeys.java` | 新增常量 `PRODUCT_ACTIVITY_SYNC = "product:activity:sync"` |
| 3 | 修改 | `backend/src/main/java/com/colonel/saas/mapper/ColonelsettlementActivityMapper.java` + XML | 新增 `selectActiveActivityIds(@Param("limit") int limit, @Param("lastSyncedBefore") LocalDateTime threshold)` 和 `touchLastSyncAt(@Param("activityId") String, @Param("syncedAt") LocalDateTime)` 两个方法 |
| 4 | 修改 | `backend/src/main/resources/application.yml` | 新增 `product.activity.sync.{enabled,cron,batch-size,whitelist-activities}` 四个配置项（**默认 enabled=false**，让运维按需开） |
| 5 | 新增 | `backend/src/test/java/com/colonel/saas/job/ProductActivitySyncJobTest.java` | mock ProductService + DistributedJobLockService，验证：锁、灰度白名单、单活动异常隔离、disabled 跳过 |
| 6 | 修改 | `docs/V1-商品域现状审计.md` | 在 P-10 / 修复任务清单中追加"定时同步 job 已实装"；更新 6/1 重审的 §5 P-10 描述 |
| 7 | 新增 | `docs/接口/活动商品定时同步-API契约.md`（可选） | 仅当 job 暴露管理端点（启用/禁用/触发）时；默认不开 |

### 3.2 关键代码骨架

> **仅方案稿，本轮不实施**。仅作为评审参考。

```java
// ProductActivitySyncJob.java
@Slf4j
@Component
public class ProductActivitySyncJob {

    private static final Duration LOCK_TTL = Duration.ofMinutes(30);
    private static final int QPS_GUARD_SLEEP_MS = 2000; // 单活动间隔，简易 QPS 限流

    private final ProductService productService;
    private final DistributedJobLockService jobLockService;
    private final ColonelsettlementActivityMapper activityMapper;

    @Value("${product.activity.sync.enabled:false}")
    private boolean enabled;
    @Value("${product.activity.sync.batch-size:20}")
    private int batchSize;
    @Value("${product.activity.sync.whitelist-activities:}")
    private String whitelist;

    @Scheduled(cron = "${product.activity.sync.cron:0 0 */2 * * ?}")
    public void syncAll() {
        if (!enabled) {
            log.info("ProductActivitySyncJob skipped (disabled by config)");
            return;
        }
        if (!jobLockService.tryAcquire(JobLockKeys.PRODUCT_ACTIVITY_SYNC, LOCK_TTL)) {
            log.info("ProductActivitySyncJob skipped, lock held by another node");
            return;
        }
        try {
            List<String> activities = resolveActivityIds();
            int ok = 0, fail = 0;
            for (String activityId : activities) {
                try {
                    var req = new DouyinProductGateway.ActivityProductQueryRequest(
                        null, activityId, 0L, 1L, batchSize, null, 0, null, null, 1L, null, null);
                    var result = productService.refreshActivitySnapshots(req);
                    activityMapper.touchLastSyncAt(activityId, LocalDateTime.now());
                    ok++;
                    log.info("activity sync ok: id={} synced={} library={}",
                        activityId, result.syncedProductCount(), result.libraryEntryCount());
                } catch (Exception ex) {
                    fail++;
                    log.warn("activity sync failed: id={}", activityId, ex);
                }
                try { Thread.sleep(QPS_GUARD_SLEEP_MS); }
                catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
            log.info("ProductActivitySyncJob finished, ok={}, fail={}", ok, fail);
        } finally {
            jobLockService.release(JobLockKeys.PRODUCT_ACTIVITY_SYNC);
        }
    }

    private List<String> resolveActivityIds() {
        if (StringUtils.hasText(whitelist)) {
            return Arrays.stream(whitelist.split(","))
                .map(String::trim).filter(StringUtils::hasText).toList();
        }
        return activityMapper.selectActiveActivityIds(
            batchSize, LocalDateTime.now().minusMinutes(30));
    }
}
```

### 3.3 关键设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 触发粒度 | 按**活动**扫（非按商品） | 复用 `refreshActivitySnapshots` 现有签名；保证与 controller 行为一致；触发 `applyForActivityId` 全活动展示对账 |
| 跑批频率 | 默认 2 小时（`0 0 */2 * * ?`） | 抖店 buyin 接口 QPS 限制 + 30 分钟级实时性诉求折中；real-pre 灰度期间调高到 30 分钟 |
| 灰度策略 | `whitelist-activities` 配置项 | real-pre 阶段必先放 1~2 个活动跑 1 周，观察 QPS + 异常率 |
| QPS 限流 | `Thread.sleep(2000)` 串行 | 简单可控；后续可改 token bucket |
| `last_sync_at` 字段 | 用 `colonelsettlement_activity` 现存 `last_sync_at`（已存在） | 避免加列；通过 `WHERE last_sync_at < now() - 30min` 控制扫到"最近没扫过"的活动 |
| `touchLastSyncAt` 是否与 sync 同一事务 | **否** | sync 失败时不应 touch，否则下次扫不到；审计字段独立 |
| `display_status` 联动 | 不在本 job 直接改 | `refreshActivitySnapshots` 内部已经会调 `productDisplayRuleService.applyForActivityId`，避免重复 |
| admin 强制展示/隐藏 | 不影响 | `AdminProductDisplayService` 是写 `display_status` 的人工通道，与本 job 互不干扰 |

### 3.4 配置项默认

```yaml
product:
  activity:
    sync:
      enabled: false               # 默认关，运维按需开
      cron: "0 0 */2 * * ?"
      batch-size: 20
      whitelist-activities: ""     # 留空 = 全量；填活动 ID 逗号分隔 = 灰度
```

---

## 四、关键 trade-off

| 方案 | 优点 | 缺点 | 选不选 |
|------|------|------|--------|
| **A. 新增 ProductActivitySyncJob**（本方案） | 改动量小（1 新 + 4 改）；零业务逻辑新增；灰度可控 | 仍走轮询，非实时；QPS 占用 | ✅ 选 |
| B. 监听抖店 webhook 推送 | 实时 | 抖店 buyin webhook 文档与配额未确认；接入成本高；real-pre 暂无 webhook 通道 | ❌ V2 |
| C. MQ 化事件驱动 | 实时 + 解耦 | V1 简化口径明确不强制引入 MQ；改动面广 | ❌ V2 |
| D. 维持现状（手动 refresh） | 零改动 | 商品陈旧、selected_to_library 长期挂 true | ❌ 不解决根本问题 |

---

## 五、边界与假设

- [V1 必做] 假设抖店 buyin `queryActivityProducts` 接口 QPS ≤ 10；`Thread.sleep(2000)` + `batch-size=20` 给出 ≤ 0.5 QPS 的保守限流。
- [V1 必做] 假设 `colonelsettlement_activity.last_sync_at` 字段已存在（commit `7d538a3` 已加，见 `db/alter-colonel-activity-recruiter-assignment.sql`）；若未存在则需先 alter。
- [V1 必做] 假设现有 `DistributedJobLockService.tryAcquire` / `release` 已稳定（`ProductPinCleanupJob` 已在生产用）。
- [V1 简化] 单 job 串行扫所有活动；若活动数 > 100，应改为分片并行 + 每片独立锁；本期简化不做。
- [V1 不做] 商品**详情**级同步（价格、佣金、SKU 库存）属于 P-12「商品详情定时同步」，本方案不覆盖。
- [V2 预留] 抖店 webhook 监听 + MQ 事件 + 商品详情级同步 全部进 V2。

---

## 六、验证方法

### 6.1 单元 / 集成测试

- `ProductActivitySyncJobTest`：
  - 禁用开关时直接 return
  - 锁被占用时 return
  - 白名单生效：仅扫白名单内活动
  - 单活动异常不影响后续活动
  - `enabled=true` + 锁空闲 + 2 个活动 → 调 2 次 `refreshActivitySnapshots`

### 6.2 test 环境验证

```bash
# 1. 启动 test 环境
docker compose -f docker-compose.test.yml up -d

# 2. 注入种子数据（已存在）：test 下通过 Gateway mock 注入活动 + 商品
# 3. 启用 job（环境变量）
PRODUCT_ACTIVITY_SYNC_ENABLED=true \
PRODUCT_ACTIVITY_SYNC_WHITELIST_ACTIVITIES=TEST_ACTIVITY_001 \
PRODUCT_ACTIVITY_SYNC_CRON="0 */5 * * * *" \
  docker compose -f docker-compose.test.yml restart backend

# 4. 观察日志：每 5 分钟一次 ProductActivitySyncJob finished, ok=1, fail=0
# 5. 验证数据库 product_operation_state 的 last_sync_at 已更新
docker compose -f docker-compose.test.yml exec postgres \
  psql -U saas -d saas -c "SELECT activity_id, last_sync_at FROM colonel_activity_activity WHERE activity_id='TEST_ACTIVITY_001';"
```

### 6.3 real-pre 灰度验证

```bash
# 1. real-pre 启用（仅白名单 1 个活动）
PRODUCT_ACTIVITY_SYNC_ENABLED=true \
PRODUCT_ACTIVITY_SYNC_WHITELIST_ACTIVITIES=REAL_ACTIVITY_DEMO_01 \
PRODUCT_ACTIVITY_SYNC_CRON="0 */30 * * * *" \
  # 通过 .env.real-pre 注入或运维后台修改

# 2. 观察 1 周：
#    - 抖店 QPS 监控
#    - product_operation_state.display_status 切换频率
#    - selected_to_library 与上游 status 不一致告警

# 3. 灰度通过后，扩到 3~5 个活动，再观察 1 周
# 4. 全量开启（whitelist 留空）
```

### 6.4 验收判定（V1 必做子集 = P-12 保底实现）

> 范围基线：§七-1 评审结论——P-12 拆分为 V1 必做子集（6 条）+ 波 2 增强（6 条）。本节只列 V1 必做子集的验收规则，波 2 验收口径待 V2 启动时另立。

**功能验收**（对应 §七-1 V1 必做 6 条）：

- [V1 必做] **同步最新活动列表** — `colonelsettlement_activity` 与上游活动 ID 集合一致（允许 last_sync_at ≤ 30 分钟的延迟）
- [V1 必做] **一键同步/更新活动商品** — `refresh=true` 触发后，`product_snapshot` 全量刷新（created + updated + skipped 数 ≥ 上游本次返回条数）
- [V1 必做] **上游「推广中」→ 自动入商品库并展示** — `selected_to_library=true` + `displayStatus=DISPLAYING` 在 30 分钟内命中
- [V1 必做] **上游「非推广中」→ 自动隐藏 / 标记不可推广** — `selected_to_library=false` + `displayStatus=HIDDEN` + `hiddenReason` 含上游状态原因
- [V1 必做] **不覆盖本地补录字段**（见 ⑤ 边界）
- [V1 必做] **手动同步 / 单商品刷新 / 批量状态检测**（见 ⑥ 边界）

**⑤ 边界：不覆盖本地补录字段（已拍板 2026-06-01，选 A）**

- [V1 必做] **fillSnapshot 覆盖 `product_snapshot` 上游镜像字段**：允许（`title/cover/price/shop_id/status/sales/category_name/promotion_start_time/promotion_end_time/activity_cos_ratio/cos_type/...`，见 `ProductService.fillSnapshot:3262-3286`）
- [V1 必做] **`product_operation_state` 本地补录字段不被覆盖**：必须保证。V1 口径下"本地补录"包括但不限于：
  - `audit_status` / `audit_remark` / `audit_payload`
  - `assignee_id`
  - `selected_to_library` / `selected_at` / `selected_by`
  - `display_status` / `hidden_reason` / `first_displayed_at` / `last_displayed_at`
  - `biz_status`（业务状态机字段）
  - `promote_link` / `short_link`（转链结果）
  - `pinned_at` / `pinned_until` / `pinned_by`（招商置顶）
  - 本地标签 / 操作态 / 展示态等运营字段
- [V1 必做] 验收用例：对一条已审核 + 已入库 + 已置顶 + 已转链的本地运营态记录，调一次 `upsertSnapshots`（同 productId 上游返回），上述 11 个字段**逐字段**断言与 sync 前一致
- [V1 不做] 灰色地带：V1 不支持在 `product_snapshot` 表里手改价格 / 标题 / 图片等上游字段并保护不被覆盖。若未来业务需要"本地改写商品标题 / 价格展示"，应新增**本地 override 字段**（如 `product_local_override` 表），而不是保护 snapshot 原字段

**⑥ 边界：批量状态检测（已拍板 2026-06-01，选 A）**

- [V1 必做] **V1 批量状态检测 = 一键同步 / 更新商品触发 `refreshActivitySnapshots`，按上游 `official_status` 修正 `display_status`**。核心路径：

  ```
  同步活动 → 一键同步/更新商品 → refreshActivitySnapshots
    → applyUpstreamProductLibraryDecision（按上游推广状态决策）
    → 上游 PROMOTING → 自动入商品库（selected_to_library=true）
    → 上游非 PROMOTING → 自动隐藏（display_status=HIDDEN）
  ```

- [V1 必做] 验收用例：在已 sync 过的活动上重跑 `refreshActivitySnapshots`，统计 `applyUpstreamProductLibraryDecision` 决策变更数（`stateChanged=true` 的行数）应 > 0；抽样核对 `display_status` 切换
- [V1 必做] 不单独新增接口：`POST /colonel/activities/products/batch-status-check`（理由：扩大范围；real-pre 规模 + buyin QPS + applyForActivityId 耗时三项均未实测，暂不引入新接口 / 权限 / DTO / 前端入口 / 测试面）
- [V1 必做] 触发条件收敛：仅 ① `POST /colonel/activities/{aid}/products?refresh=true`（用户主动）② `ProductActivitySyncJob`（本方案新增的定时 job） 两种入口会触发批量状态检测
- [V2 预留] 后续升级为"独立 batch-status-check 接口"的触发条件（任一满足时再议）：
  1. real-pre 活动数 / 商品数很大，整活动 refresh 太慢
  2. 业务需要只检测选中的几十个商品
  3. 前端明确需要「检测上游状态」独立按钮
  4. buyin QPS 实测允许小批量并发
  5. `applyForActivityId` 耗时超过可接受范围，需要拆出状态对账接口

**质量验收**：

- [V1 必做] real-pre 阶段无 QPS 超限告警
- [V1 必做] 单活动失败不影响其他活动（job 内 try/catch 隔离）
- [V1 必做] job 锁被占用时本节点跳过（不并发执行）
- [V1 简化] 商品**详情**级同步不在本期验收（P-12 波 2 增强）

---

## 七、开放问题（评审需拍）

### 1. P-12 与本方案的关系（**已评审 2026-06-01**）

> 用户已给明确结论，本节记录为口径基线。

**结论**：**不是完整升级 P-12，而是把 P-12 拆成 V1 必做子集 + 波 2 增强。本方案 = P-12 的 V1 保底实现。**

**V1 必做子集**（即本方案的验收范围）：

- [V1 必做] 同步最新活动列表
- [V1 必做] 一键同步/更新活动商品
- [V1 必做] 上游商品状态为「推广中」时自动同步进商品库并展示
- [V1 必做] 上游状态变为非推广中时自动从商品库隐藏或标记不可推广
- [V1 必做] 不覆盖本地补录字段
- [V1 必做] 支持手动同步 / 单商品刷新 / 批量状态检测

**仍维持 P-12 简化 / 波 2**（本期不做）：

- [V2 预留] 全量商品详情定时同步
- [V2 预留] 大规模分片并行同步
- [V2 预留] 更细的商品详情字段定时补全
- [V2 预留] QPS 动态限流
- [V2 预留] 失败补偿队列
- [V2 预留] 全量同步任务监控面板

**对本方案的反向影响**：

- §六 验收判定（6.4）已收敛为 V1 必做子集 6 条 + ⑤ ⑥ 边界澄清 + 质量验收（详见 §6.4）
- §三-3.4 配置项里**不需要**新增监控/补偿相关开关（已挪到波 2）
- 建议另立 **ADR-007「P-12 拆分为 V1 必做子集 + 波 2 增强」**，落 `docs/决策/ADR-007-...md`，遵循 CLAUDE.md L56 范围决策归档原则

### 1.5 问题 2-4：待 real-pre 数据 + 压测（Mavis 查不了，等用户/运维跑）

> 我作为 host session **没有数据库连接**也没法直连 real-pre 抖店接口。下面是 runbook，你 / 运维在 real-pre 上跑一下贴结果给我，再回来收口 §七-2 / §七-3 / §七-4。

**问题 2：活动数规模**（决定要不要分片并行）

```bash
# 进入 real-pre postgres
docker compose -f docker-compose.real-pre.yml exec postgres \
  psql -U saas -d saas_real_pre

-- 活动总数（含历史）
SELECT count(*) FROM colonelsettlement_activity;

-- 当前有效/推广中活动
SELECT count(*) FROM colonelsettlement_activity
WHERE status IN (1, 2)  -- 1=推广中, 2=进行中，按实际枚举值替换
   OR status_text LIKE '%推广%'
   OR status_text LIKE '%进行%';

-- 活动-商品快照关联总数
SELECT count(*) FROM product_snapshot;

-- 每个活动商品数分布 top 20（决定单 job 跑完耗时）
SELECT activity_id, count(*) AS product_count
FROM product_snapshot
WHERE deleted = 0
GROUP BY activity_id
ORDER BY product_count DESC
LIMIT 20;
```

**问题 3：抖店 buyin 接口 QPS 实测**（决定 `Thread.sleep(2000)` 够不够）

仓库历史里见过 `jmeter.log`（commit 列表里），沿用 JMeter 跑：
```bash
# 复用现有 jmeter 计划
jmeter -n -t tests/jmeter/buyin-qps-test.jmx \
  -Jhost=open-platform-real-pre.douyin.com \
  -Jthreads=5 -Jrampup=10 -Jduration=60 \
  -l /tmp/buyin-qps.jtl
```
观察：错误率、p99 延迟。**real-pre 上跑前请先与抖店对接人确认接口配额**。

**问题 4：`applyForActivityId` 单活动耗时**（决定 sleep 间隔）

test 环境跑：
```bash
# 在 test 注入种子活动后
cd backend && mvn test -Dtest=ProductDisplayRuleServiceTest#applyForActivityId_perfSmoke
# 自行追加的 perf smoke 测试：模拟 1 个活动 200 条快照，统计 applyForActivityId 耗时
```

**问题 2-4 跑完后回到本 plan**，更新 §七-2、§七-3、§七-4 三个开放问题。

---

2. **活动数规模假设**：real-pre 当前活动数是 < 50、50~200、还是 > 200？
   - 决定是否需要分片并行
3. **抖店 QPS 实测**：real-pre 联调阶段是否做过 buyin 接口 QPS 压测？
   - 若未做，灰度前应补一次
4. **`refreshActivitySnapshots` 内置 `applyForActivityId` 全展示对账**：单活动跑下来耗时多大？
   - 决定 `Thread.sleep(2000)` 是否够，是否要更长间隔

---

## 八、与现有 ADR 关系

- 遵循 `docs/决策/ADR-002-V1范围优先级.md`：
  - 本方案是当前事实（代码现状）触发的设计决策
  - 评审通过后建议落 ADR-007「商品活动同步定时化纳入 V1 必做」或保留 V1 简化口径
- 与 `docs/01-V1交付范围与边界.md` P-12 的关系：见 §七-1

---

## 九、变更影响

- **代码影响**：5 个文件（1 新 + 4 改）
- **文档影响**：2 个 md 文档（重审 + 接口契约，可选）
- **运维影响**：新增 4 个环境变量；real-pre 灰度流程需运维配合
- **下游影响**：商品域 `selected_to_library` 状态会更频繁更新；商品库列表 UI 应保留"上游已终止但仍展示"时的视觉提示（**前端**改动，不在本方案范围）
- **性能影响**：每 2 小时 1 次，每次扫 ≤ 20 个活动，每个活动平均 5 秒（sleep 2s + 接口 3s），单次总耗时 ≤ 100 秒；DB 写入主要是 `last_sync_at` 1 行/活动

---

**起草人**：Mavis（AI）
**起草日期**：2026-06-01
**下一步**：评审通过后：(a) 落 ADR；(b) 排到下轮 sprint 实施；(c) test 环境先验，再 real-pre 灰度。

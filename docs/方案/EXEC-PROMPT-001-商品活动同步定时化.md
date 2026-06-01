# EXEC-PROMPT-001 商品活动同步定时化（执行提示词）

> 本文件是 PLAN-001 的可执行版提示词，下一轮 sprint 解除 CLAUDE.md L58「本轮只允许文档和 `.claude/` 工程化文档变更」限制后，可直接喂给执行 AI / 开发者。
> **本文件不是 plan 本身**，plan 在 `PLAN-001-商品活动同步定时化.md`。

---

## 0. 使用说明

- **使用前必读**：`docs/方案/PLAN-001-商品活动同步定时化.md`（plan 主体）、`docs/01-V1交付范围与边界.md`、`docs/决策/ADR-002-V1范围优先级.md`
- **执行前必跑**：`docs/方案/PLAN-001-商品活动同步定时化.md` §1.5 runbook（real-pre 活动数 / buyin QPS / applyForActivityId 耗时三项）
- **本文约束强于 plan 任何章节**：以下 §1 - §11 均为已拍板口径，**不得自行调整**

---

## 1. 任务

[V1 必做] 新增 `ProductActivitySyncJob`，按活动粒度周期拉上游抖店活动商品，触发现有的 `ProductService.refreshActivitySnapshots` → `applyUpstreamProductLibraryDecision` 自动入库/下架决策。

**核心目标**：商品库持续反映上游抖店活动商品状态，"上游推广中 → 商品库展示；上游非推广中 → 商品库隐藏"无需人工介入。

---

## 2. 范围边界（强约束）

### 2.1 V1 必做（**做**）

- 同步最新活动列表
- 一键同步/更新活动商品
- 上游商品状态为「推广中」时自动同步进商品库并展示
- 上游状态变为非推广中时自动从商品库隐藏或标记不可推广
- 不覆盖本地补录字段
- 支持手动同步 / 单商品刷新 / 批量状态检测

### 2.2 V1 不做（**不要碰**）

- 不新增 `POST /colonel/activities/products/batch-status-check` 接口
- 不在 `product_snapshot` 表里手改价格/标题/图片等上游字段并保护不被覆盖
- 不引入 MQ、不引入 webhook 监听、不引入分片并行框架
- 不做监控面板 / 失败补偿队列 / QPS 动态限流
- 不动 `ProductDisplayRuleService` / `applyUpstreamProductLibraryDecision` 业务逻辑

### 2.3 边界 ⑤（已拍板，选 A）

[V1 必做] `fillSnapshot`（`ProductService.java:3262-3286`）覆盖 `product_snapshot` 上游镜像字段是**允许的**。
[V1 必做] `product_operation_state` 表的 11 类本地补录字段**必须不被覆盖**：
- `audit_status` / `audit_remark` / `audit_payload`
- `assignee_id`
- `selected_to_library` / `selected_at` / `selected_by`
- `display_status` / `hidden_reason` / `first_displayed_at` / `last_displayed_at`
- `biz_status`
- `promote_link` / `short_link`
- `pinned_at` / `pinned_until` / `pinned_by`
- 本地标签 / 操作态 / 展示态等运营字段

[V1 必做] 单测必须对一条已审核+已入库+已置顶+已转链的运营态记录，调一次 `upsertSnapshots` 后**逐字段**断言上述 11 类不变。

### 2.4 边界 ⑥（已拍板，选 A）

[V1 必做] V1 批量状态检测 = 一键同步/更新商品触发 `refreshActivitySnapshots`，按上游 `official_status` 修正 `display_status`。
[V1 必做] 触发入口仅两种：① `POST /colonel/activities/{aid}/products?refresh=true` ② `ProductActivitySyncJob`（本次新增）
[V2 预留] 升级为独立 batch-status-check 接口的 5 条触发条件详见 `PLAN-001 §6.4⑥`——本轮不实现

---

## 3. 必须先读的文档

按顺序读完再动笔：

1. `docs/01-V1交付范围与边界.md`（V1 范围基线）
2. `docs/决策/ADR-002-V1范围优先级.md`（V1 > 旧 V2.2 文档）
3. `docs/方案/PLAN-001-商品活动同步定时化.md`（本任务 plan 主体）
4. `docs/领域/商品域.md`（商品域合同）
5. `docs/V1-商品域现状审计.md`（6/1 重审，含代码事实）
6. `CLAUDE.md`（再次确认本轮是否解禁）
7. **如未解禁**——立刻停止，仅更新 plan 文档，不写代码

---

## 4. 前置数据收集（执行前必跑）

> 数据从 §1.5 runbook 跑出来，决定 §5 配置项的合理默认值。**没有数据不允许拍默认**。

| 数据 | 跑法 | 用途 |
|------|------|------|
| real-pre 活动数 | `docker exec postgres psql` 跑 `SELECT count(*) FROM colonelsettlement_activity` | 决定是否分片并行（V1 串行，>100 标 P2 升级） |
| 推广中活动数 | 同上，WHERE status IN (1,2) 或 status_text 模糊匹配 | 决定 `batch-size` 默认值 |
| 活动-商品关联分布 top 20 | `SELECT activity_id, count(*) FROM product_snapshot GROUP BY activity_id ORDER BY 2 DESC LIMIT 20` | 决定单活动 sync 耗时上限 |
| buyin QPS 实测 | JMeter 压测（仓库有 `tests/jmeter/buyin-qps-test.jmx` 模板） | 决定 `Thread.sleep(N)` 间隔 |
| `applyForActivityId` 单活动耗时 | test 环境 perf smoke 测试 | 决定 sleep 间隔 + 灰度 cron |

---

## 5. 改动清单

### 5.1 新增（1 个文件）

**`backend/src/main/java/com/colonel/saas/job/ProductActivitySyncJob.java`**

抄 `ProductPinCleanupJob.java` 模板：
- `@Slf4j @Component`
- 构造注入：`ProductService`、`DistributedJobLockService`、`ColonelsettlementActivityMapper`
- `@Value` 注入 4 个配置项（见 §6）
- `@Scheduled(cron = "${product.activity.sync.cron:0 0 */2 * * ?}")`
- 主体方法 `syncAll()`：取锁 → 遍历活动 → 调 `refreshActivitySnapshots` → 异常隔离 → 释放锁
- 写 INFO / WARN 日志，统计 `ok / fail` 计数

骨架见 `PLAN-001 §3.2`。

### 5.2 修改（4 个文件）

| 文件 | 改动 |
|------|------|
| `backend/src/main/java/com/colonel/saas/job/JobLockKeys.java` | 新增常量 `PRODUCT_ACTIVITY_SYNC = "product:activity:sync"` |
| `backend/src/main/java/com/colonel/saas/mapper/ColonelsettlementActivityMapper.java` + XML | 新增 `selectActiveActivityIds(int limit, LocalDateTime lastSyncedBefore)` 和 `touchLastSyncAt(String activityId, LocalDateTime syncedAt)` |
| `backend/src/main/resources/application.yml` | 新增 `product.activity.sync.{enabled,cron,batch-size,whitelist-activities}` 四个配置项（**默认 enabled=false**） |
| `docs/V1-商品域现状审计.md` | §1 表格 P-10 状态追加"定时同步 job 已实装"；§五 修复任务清单追加"✅ TASK-XXX 商品活动同步定时化" |

### 5.3 新增测试（1 个文件）

**`backend/src/test/java/com/colonel/saas/job/ProductActivitySyncJobTest.java`**

- mock `ProductService`、`DistributedJobLockService`、`ColonelsettlementActivityMapper`
- 用例：
  - `enabled=false` → 直接 return
  - 锁被占用 → 直接 return
  - 白名单生效 → 仅扫白名单活动
  - 单活动异常 → 后续活动继续
  - `enabled=true` + 锁空闲 + 2 个活动 → 调 2 次 `refreshActivitySnapshots` + 2 次 `touchLastSyncAt`
- 边界 ⑤ 验收用例：**对一条已审核+已入库+已置顶+已转链的运营态记录，调一次 `upsertSnapshots` 后逐字段断言 11 类不变**（这是必须的单测）

---

## 6. 配置项（YAML 模板）

添加到 `backend/src/main/resources/application.yml`：

```yaml
product:
  activity:
    sync:
      enabled: false                       # 默认关，运维按需开
      cron: "0 0 */2 * * ?"
      batch-size: 20
      whitelist-activities: ""             # 留空 = 全量；填活动 ID 逗号分隔 = 灰度
```

默认值**根据 §4 数据收集结果调整**：
- 若推广中活动 < 50：`batch-size=20` + `cron=0 0 */2 * * ?` 即可
- 若推广中活动 50~200：`batch-size=10` + `cron=0 0 */1 * * ?` 收紧
- 若 buyin QPS 实测 < 5：`Thread.sleep` 提到 5000ms（代码内常量）
- 若 `applyForActivityId` 耗时 > 10s：拉长 sleep 到 15000ms

---

## 7. 设计决策（强约束）

| 决策 | 选 | 不选 |
|------|----|------|
| 触发粒度 | 按**活动**扫 | 按商品（量级太大）/ 按商家（不在商品域） |
| 跑批频率 | cron，默认 2 小时 | 实时（V1 不用 webhook / MQ） |
| 灰度策略 | `whitelist-activities` 配置项 | 全量一次开（风险大） |
| QPS 限流 | `Thread.sleep(2000)` 串行（**默认值，需根据 §4 数据调整**） | token bucket / semaphore（V1 简化不做） |
| `last_sync_at` 复用 | `colonelsettlement_activity.last_sync_at`（commit `7d538a3` 已加） | 新加列 |
| `touchLastSyncAt` 事务 | **独立**（sync 失败时不 touch） | 同一事务（会丢扫描机会） |
| `display_status` 联动 | 不在本 job 直接改 | 直接改（重复 `applyForActivityId`） |
| admin 强制展示/隐藏 | 不影响 | 需兼容（V1 已有 `AdminProductDisplayService`） |

---

## 8. 验收判定（PLAN-001 §6.4 完整版）

### 8.1 功能验收

- [V1 必做] 同步最新活动列表 — `colonelsettlement_activity` 行集合与上游活动 ID 一致（last_sync_at ≤ 30 分钟延迟）
- [V1 必做] 一键同步/更新活动商品 — `refresh=true` 触发后 `product_snapshot` 全量刷新，created+updated+skipped ≥ 上游返回条数
- [V1 必做] 上游「推广中」→ 自动入商品库并展示（30 分钟内）
- [V1 必做] 上游「非推广中」→ 自动隐藏 + `hiddenReason` 含上游状态原因
- [V1 必做] 不覆盖本地补录字段（边界 ⑤ 单测通过）
- [V1 必做] 手动同步/单商品刷新/批量状态检测（边界 ⑥ 触发入口仅 2 种）

### 8.2 质量验收

- [V1 必做] real-pre 无 QPS 超限告警
- [V1 必做] 单活动失败不影响其他活动
- [V1 必做] job 锁被占用时本节点跳过（不并发）
- [V1 必做] 边界 ⑤ 11 类运营字段单测断言全通过
- [V1 简化] 商品**详情**级同步不在本期

---

## 9. 验证步骤

### 9.1 test 环境

```bash
# 启动 test 环境
docker compose -f docker-compose.test.yml up -d

# 启用 job（环境变量，覆盖默认值）
PRODUCT_ACTIVITY_SYNC_ENABLED=true \
PRODUCT_ACTIVITY_SYNC_WHITELIST_ACTIVITIES=TEST_ACTIVITY_001 \
PRODUCT_ACTIVITY_SYNC_CRON="0 */5 * * * *" \
  docker compose -f docker-compose.test.yml restart backend

# 观察日志
docker compose -f docker-compose.test.yml logs -f backend | grep "ProductActivitySyncJob"
# 期望：每 5 分钟一次 "ProductActivitySyncJob finished, ok=1, fail=0"

# 跑回归
cd backend && mvn test -Dtest=ProductActivitySyncJobTest
cd backend && mvn test  # 完整回归
```

### 9.2 real-pre 灰度

```bash
# 1. 灰度 1 个活动观察 1 周
PRODUCT_ACTIVITY_SYNC_ENABLED=true \
PRODUCT_ACTIVITY_SYNC_WHITELIST_ACTIVITIES=REAL_ACTIVITY_DEMO_01 \
PRODUCT_ACTIVITY_SYNC_CRON="0 */30 * * * *" \
  # 注入 .env.real-pre 或运维后台

# 2. 观察 1 周：抖店 QPS 监控 / product_operation_state.display_status 切换频率 / selected_to_library 一致性

# 3. 通过后扩到 3~5 个活动再观察 1 周
# 4. 通过后全量开启（whitelist 留空）
```

### 9.3 边界 ⑤ 单测（必须）

```java
@Test
void upsertSnapshots_preservesLocalOperationStateFields() {
    // 1. 准备：插入一条 product_snapshot + product_operation_state
    //    （已审核=2，已入库=true，已置顶=pinned_at+pinned_until+pinned_by，已转链=promote_link+short_link）
    // 2. 调 upsertSnapshots，传入上游同 productId 但 title/cover/price 等已变更的 item
    // 3. 重新读 product_operation_state
    // 4. 断言 11 类字段逐字段不变：
    //    audit_status / audit_remark / audit_payload / assignee_id
    //    selected_to_library / selected_at / selected_by
    //    display_status / hidden_reason / first_displayed_at / last_displayed_at
    //    biz_status / promote_link / short_link
    //    pinned_at / pinned_until / pinned_by
}
```

---

## 10. 交付物清单

执行完成后，PR/MR 应包含：

- [ ] 代码：1 新 + 4 改 + 1 新测试（见 §5）
- [ ] 单测：`ProductActivitySyncJobTest` 全绿（含边界 ⑤ 11 类字段断言）
- [ ] 回归：`cd backend && mvn test` 全绿
- [ ] 文档：`docs/V1-商品域现状审计.md` 同步更新（P-10 状态 + 修复任务清单）
- [ ] Commit message 风格遵循仓库约定（参考最近 12 个 commit）
- [ ] **不要碰**：`docs/01-V1交付范围与边界.md`（P-12 范围裁定待 ADR-007 落档后由维护者改）
- [ ] **不要新建**：`docs/方案/PLAN-001` 已存在；本任务不另立 plan 文档

---

## 11. 红线（违反任意一条直接拒绝合入）

- [ ] 改了 `ProductService.fillSnapshot` 业务逻辑（除非边界 ⑤ 5.3 单测发现真实问题，由 ADR 走）
- [ ] 新增 `batch-status-check` 专用接口
- [ ] 引入 MQ / webhook / 分片并行框架
- [ ] 把 `enabled` 默认改成 `true`
- [ ] 在 `ProductService.refreshActivitySnapshots` 里加新业务逻辑
- [ ] 修改 `applyUpstreamProductLibraryDecision` / `applyForActivityId` 行为
- [ ] 触动其他域代码（订单 / 业绩 / 用户 / 寄样 / 配置 / 达人 / 分析）
- [ ] 删除 / 改写既有 6/1 重审结论
- [ ] 把 P-08 修复（assignee 归因冲突）顺手"一起做"——那个是独立 task
- [ ] 提交时未同步更新 `docs/V1-商品域现状审计.md`

---

## 12. 一句话自检（执行完成后回填）

> 本次改动只新增一个 job 类 + 4 个小改 + 1 个测试；**没动商品域任何业务逻辑**；**V1 必做 6 条全部可测**；**V1 不做 7 条全没碰**；**单测全绿**；**test 环境跑过**。✅

---

**起草人**：Mavis（AI）
**起草日期**：2026-06-01
**配套 plan**：`docs/方案/PLAN-001-商品活动同步定时化.md`
**配套 ADR（待建）**：`docs/决策/ADR-007-P12拆分为V1必做子集与波2增强.md`

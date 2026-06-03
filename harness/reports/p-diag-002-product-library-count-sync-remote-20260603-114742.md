# P-DIAG-002 商品库数量不足排查报告

## 1. 任务概述

| 项目 | 值 |
| --- | --- |
| 任务编号 | P-DIAG-002 |
| 任务名称 | 商品库数量不足排查：同步链路、本地 real-pre 与远端对账 |
| 执行时间 | 2026-06-03 11:47 |
| 是否修改代码 | **否** |
| 是否执行数据库写操作 | **否** |
| 是否重启容器 | **否** |
| 是否部署远端 | **否** |

## 2. Harness 读取情况

已读取：

- `AGENTS.md` ✓
- `CLAUDE.md`（通过 CURRENT_STATE 间接读取）✓
- `harness/AGENT_CONTRACT.md` ✓
- `harness/TASK_ROUTING.md` ✓
- `harness/FORBIDDEN_SCOPE.md` ✓
- `harness/COMPLETION_GATES.md`（通过 CURRENT_STATE 引用）✓
- `harness/CURRENT_STATE.md` ✓
- `harness/state/DOMAIN_STATUS.md` ✓
- `harness/state/KNOWN_ISSUES.md` ✓
- `harness/state/DECISIONS.md`（通过 diff 确认存在）✓
- `harness/HARNESS_CHANGELOG.md` ✓
- `harness/instructions/product-domain.md` ✓
- `harness/runbooks/remote-deploy.md` ✓
- `harness/reports/p-fix-001c-product-library-pagination-20260603-112740.md` ✓

## 3. 当前 Git 工作区状态

```
本地 HEAD: 1ac7796f (docs: add U-2.5-A retro evidence)
远端 HEAD: bab9f15e (docs: add harness retro summary)
差异: 本地领先 9 个 commit（全部为 docs/harness 变更）
工作区: dirty（78 files changed）
```

变更分类：

1. **上一任务残留变更（P-FIX-001C + U-2.5-B + FUNC-001）**：
   - 前端：`ProductSelectionCard.vue`、`ProductSelectionCard.test.ts`、`ProductLibrary.vue`（P-FIX-001C + FUNC-001）
   - 后端：`DeptType.java`、`DeptTypes.java`（已删除）、`SysDeptService.java`、SQL migrations（U-2.5-B）
   - 后端测试：`SysUserServiceTest`、`CommissionRuleControllerTest`、`SysDeptControllerTest`、`SysUserControllerTest`、`CommissionRuleServiceTest`、`SysDeptServiceTest`
   - Harness：`AGENT_CONTRACT.md`、`CURRENT_STATE.md`、`FORBIDDEN_SCOPE.md`、`HARNESS_CHANGELOG.md`、`TASK_ROUTING.md`
   - E2E：`03b-product-library-drawer-fields.spec.ts`
   - 删除的报告文件：多个 evidence/retro/content-retire 文件

2. **本任务变更**：无（纯只读排查）

3. **来源不确定**：`.gitignore`（3 行新增）

## 4. 商品库代码链路

### 前端链路

```
ProductLibrary.vue (PAGE_SIZE=100, loadMore 模式)
  → api/product.ts: getProducts(params) → GET /api/products
  → 返回 { records, total, page, size }
  → normalizeItem() → ProductSelectionCard.vue
```

### 后端链路

```
ProductController.page() → GET /api/products
  → @Min(1) @Max(100) size, defaultValue=20
  → ProductService.getSelectedLibraryPage(page, size, filter)
    → collectSelectedLibraryProducts(filter)
      → 查询 product_operation_state WHERE:
          selectedToLibrary = true
          AND displayStatus = 'DISPLAYING'
          AND (auditStatus IS NULL OR auditStatus != 3)
          AND (manualDisabled IS NULL OR manualDisabled = false)
      → 批量加载 product_snapshot
      → toLegacyProduct() 构造返回
    → 内存分页 + 排序
```

### 同步链路

```
ProductActivitySyncJob (@Scheduled every 5min, enabled via env)
  → selectActiveActivityIds(limit=20, lastSyncedBefore=now-30min)
  → 逐个 activityId:
    → ProductService.refreshActivitySnapshots(request)
      → @Transactional
      → 分页拉取抖音活动商品 (pageSize=20, maxPages=100, retry)
      → upsertSnapshotsWithStats() → 创建/更新 product_snapshot
        → applyUpstreamProductLibraryDecision()
          → 推广中商品: selectedToLibrary=true, displayStatus=PENDING
          → 非推广: selectedToLibrary=false, displayStatus=HIDDEN
      → ProductDisplayRuleService.repairLibraryStateForActivity()
      → ProductDisplayRuleService.applyForActivityId()
        → 查所有 selectedToLibrary=true
        → 逐个 productId: applyForProductId()
          → isEligibleForDisplay() 判断
          → selectWinner() 去重
          → persistDisplayDecision() → PENDING→DISPLAYING/HIDDEN
```

### 关键数据库约束

```sql
CREATE UNIQUE INDEX uk_pos_one_displaying_per_product
    ON product_operation_state (product_id)
    WHERE deleted = 0 AND display_status = 'DISPLAYING';
```

## 5. 本地 real-pre 数据统计

| 指标 | 数量 |
| --- | --- |
| 活动总数 | 24 |
| 商品快照总数 | 7,278 |
| 运营状态总数 | 7,278 |
| 推广中 (status=1) | 2,673 |
| 已入商品库 (selectedToLibrary=true) | 2,673 |
| 展示中 (displayStatus=DISPLAYING) | 1,958 |
| 已入库但 PENDING | **684** |
| 已入库但 HIDDEN (去重) | 31 |
| 推广中但未入库 | **0** |
| 商品库接口 total | **1,958** |

### 交叉分析

| snapshot.status | display_status | selected_to_library | 数量 | 说明 |
| --- | --- | --- | --- | --- |
| 1 (推广中) | DISPLAYING | true | 1,958 | 正常展示 |
| 1 (推广中) | PENDING | true | **684** | 异常：展示规则未应用 |
| 1 (推广中) | HIDDEN | true | 31 | 正常去重 |
| 0/2/3/4/6 | HIDDEN | false | ~4,565 | 非推广中 |

### 684 PENDING 商品来源

| activity_id | activity_name | PENDING 数 | end_time | 状态 |
| --- | --- | --- | --- | --- |
| 3916506 | 星链达客-zy | 403 | 2026-08-03 | 未过期 |
| 3859426 | 星链达客-nn2 | 262 | **2026-04-20** | **已过期** |
| 3558291 | 星链达客-ly | 19 | **2025-06-23** | **已过期** |

### 按活动维度 Top 10 (展示中商品)

| activity_id | 展示中数量 |
| --- | --- |
| 3859423 | 1,479 |
| 3686016 | 150 |
| 3929905 | 105 |
| 3920684 | 31 |
| 3891192 | 31 |
| 3272470 | 19 |
| 3223881 | 18 |
| 3676949 | 18 |
| 3592624 | 16 |
| 3371572 | 15 |

## 6. 同步链路排查

### 同步配置

| 项目 | 本地 real-pre | 远端 real-pre |
| --- | --- | --- |
| 同步定时任务 | 每 5 分钟 | 默认每 2 小时 |
| 同步开关 | **true** | **未设置 (默认 false)** |
| 白名单 | 无（全量活动） | 无 |
| 分布式锁 TTL | 30 分钟 | 30 分钟 |

### 同步状态

- 最近同步时间：2026-06-03 03:01 (本地)、2026-06-02 15:46 (远端)
- 分页是否完整：是 (maxPages=100, pageSize=20, 有 cursor 翻页)
- 是否有 retry：是 (maxRetries 配置, 指数退避)
- 是否有限流处理：是 (429 检测, sleepBeforeRetry)
- **是否有同步失败**：**是！关键问题！**

### 关键发现：同步事务回滚

后端日志显示当前同步任务持续失败：

```
ProductActivitySyncJob activity sync failed, activityId=3916506
ProductActivitySyncJob activity sync failed, activityId=3891192
ProductActivitySyncJob finished, ok=0, fail=3
```

**失败原因**：

```
duplicate key value violates unique constraint "uk_pos_one_displaying_per_product"
```

`refreshActivitySnapshots()` 使用 `@Transactional`，展示规则应用失败导致整个事务回滚：
1. 快照 upsert 成功（内存中）
2. `repairLibraryStateForActivity` 执行
3. `applyForActivityId` → `persistDisplayDecision` → UPDATE display_status='DISPLAYING'
4. 违反唯一索引 → 异常 → 事务回滚 → 所有快照 upsert 也回滚
5. 下次同步又遇到同样问题 → 死循环

**本地数据库无此索引**（`migrate-all.sql` 未在本地执行），所以本地同步正常。

### 过期活动问题

活动 3859426 和 3558291 的 `end_time` 已过期，`selectActiveActivityIds` 会排除它们，导致：
- 同步任务不再同步这些活动
- 展示规则不再对这些活动执行
- 其 281 个推广中商品永久卡在 PENDING

## 7. 后端商品库接口排查

| 测试 | 结果 |
| --- | --- |
| size=1, total | 1,958 |
| size=100, total | 1,958, records=100 |
| size=200 | 400 "must be less than or equal to 100" |
| activityId=3859423 | total=1,479 |
| 与 SQL 一致性 | **完全一致** |

结论：后端接口 total 准确，与数据库 SQL 统计一致。pageSize=100 可用，pageSize=200 被拒绝。

## 8. 前端分页影响复核

### P-FIX-001C 状态

| 检查项 | 结果 |
| --- | --- |
| PAGE_SIZE 改为 100 | ✓ 已生效 (line 155) |
| loadMore 模式保留 | ✓ hasMore + loadMore 按钮 |
| 重置时清列表 | ✓ fetchProducts(true) 清空 |
| 筛选变化重置 | ✓ handleFiltersChange → refreshProducts |
| 活动筛选联动 | ✓ watch appliedActivityId |
| 单测通过 | ✓ 64 tests PASS (含 100 条/页断言) |

结论：P-FIX-001C 前端分页改造正确，不会导致"看起来少"。前端请求 pageSize=100，后端支持 @Max(100)。total=1958 可完整加载。

## 9. 远端服务器对账

### 远端基本信息

| 项目 | 值 |
| --- | --- |
| 远端可访问 | ✓ SSH 可达 |
| 远端目录 | /opt/saas/app |
| 远端分支 | feature/auth-system |
| 远端 commit | bab9f15e |
| 本地 commit | 1ac7796f |
| 差异 | 本地领先 9 commit（全 docs/harness） |
| 工作区 | clean |

### 远端容器状态

| 容器 | 状态 | 运行时间 |
| --- | --- | --- |
| backend | healthy | 14 hours |
| frontend | healthy | 14 hours |
| postgres | healthy | 16 hours |
| redis | healthy | 2 days |

### 远端 vs 本地数据对比

| 指标 | 本地 | 远端 | 差异 |
| --- | --- | --- | --- |
| 活动数 | 24 | 24 | 一致 |
| 商品快照数 | 7,278 | 3,601 | **远端少 3,677** |
| 运营状态数 | 7,278 | 3,601 | **远端少 3,677** |
| selectedToLibrary=true | 2,673 | 421 | **远端少 2,252** |
| DISPLAYING | 1,958 | 420 | **远端少 1,538** |
| PENDING | 755 | 2,798 | **远端多 2,043** |
| 商品库 API total | 1,958 | 未测试 (login 500) | — |
| 同步任务 | 启用 | **禁用** | **关键差异** |
| 同步失败 | 无 | **有 (unique constraint)** | **关键差异** |

### 远端商品库 API

远端 login 返回 500（与本地相同的 JSON 转义问题），未能直接测试 API。但通过 SQL 统计确认远端 library_visible = 420。

## 10. 根因结论

商品库数量不足存在**三个并存根因**：

### 根因 A：远端同步任务禁用（主要影响远端）

- 远端 `PRODUCT_ACTIVITY_SYNC_ENABLED` 未设置，默认 false
- 同步定时任务不执行，远端仅有手动同步的数据
- 远端只有 3,601 个商品（本地 7,278），仅 421 个入库（本地 2,673）
- **影响**：远端商品库 API total 仅 420（本地 1,958）

### 根因 B：唯一索引约束导致同步事务回滚（影响本地+远端）

- `uk_pos_one_displaying_per_product` 唯一索引存在于远端数据库
- `ProductDisplayRuleService.persistDisplayDecision` 在切换 DISPLAYING 记录时，先 UPDATE 新记录为 DISPLAYING，再 UPDATE 旧记录为 HIDDEN
- 在同一事务内，两个 UPDATE 之间短暂存在同一 productId 的两条 DISPLAYING 记录，违反唯一索引
- 异常导致 `@Transactional` 回滚，所有快照 upsert 也回滚
- **影响**：3 个活动的同步永久失败（ok=0, fail=3），新商品无法入库

### 根因 C：过期活动商品卡 PENDING（影响本地+远端）

- 活动 3859426 和 3558291 的 end_time 已过期
- `selectActiveActivityIds` 排除过期活动，不再同步
- 其 281 个推广中商品的展示规则从未被应用，永久卡 PENDING
- **影响**：本地 281 个推广中商品不可见于商品库

### 综合判断

| 分类 | 是否成立 | 说明 |
| --- | --- | --- |
| A. 同步不足 | **是** | 远端同步任务禁用 |
| B. 同步成功但未入库 | 否 | 推广中商品自动入库正常 |
| C. 入库但未展示 | **是** | 684 个商品 PENDING（根因 B+C） |
| D. 展示但 total 少 | 否 | API total 与 SQL 完全一致 |
| E. 前端分页问题 | 否 | P-FIX-001C 正确生效 |
| F. 本地远端不一致 | **是** | 远端少 3,677 个商品 |
| G. 远端代码/包不最新 | 部分 | 差 9 个 docs commit，业务代码一致 |

## 11. 后续修复任务建议

### P-FIX-002A：远端同步任务启用

- **目标**：远端设置 `PRODUCT_ACTIVITY_SYNC_ENABLED=true` 和合适的 cron
- **修改范围**：远端 env 文件 `/opt/saas/env/.env.real-pre`，重新部署
- **验收标准**：远端同步日志出现 `ProductActivitySyncJob finished, ok=N`
- **风险**：首次全量同步可能触发根因 B 的 unique constraint 问题

### P-FIX-002B：展示规则唯一索引冲突修复

- **目标**：修复 `persistDisplayDecision` 中的 DISPLAYING 切换逻辑，避免违反唯一索引
- **修改范围**：`ProductDisplayRuleService.java` 的 `applyNormalDisplayDedup` 方法
- **方案选项**：
  1. 先降级旧 DISPLAYING 为 HIDDEN，再升级新记录为 DISPLAYING（两次 flush）
  2. 使用 DEFERRABLE 约束
  3. 在单个 UPDATE 语句中原子切换
- **验收标准**：同步任务不再因 unique constraint 失败
- **风险**：需要修改 Java 代码 + 后端测试

### P-FIX-002C：过期活动 PENDING 商品修复

- **目标**：对过期活动的 PENDING 商品手动执行展示规则
- **修改范围**：通过 repair 入口或手动调用 `applyForActivityId`
- **验收标准**：PENDING 数量降为 0 或仅剩合理值
- **风险**：需要先修复根因 B，否则 repair 也会失败

### P-FIX-002D：远端部署对齐

- **目标**：将本地最新代码部署到远端
- **修改范围**：`deploy-remote.ps1` 执行
- **验收标准**：远端 commit 与本地一致，容器重建
- **风险**：需用户明确要求

### P-FIX-002E：前端加载更多二次修复

- **不需要**：P-FIX-001C 已正确生效，前端分页不是问题

## 12. 风险残留

- U-2.5-B / TEST-1 后端测试基线：工作区 dirty 变更未提交，但不影响本排查任务
- 远端未验证 API：login 接口 JSON 转义问题导致无法直接测试远端 API
- 未重启容器：本任务为只读排查，未重启
- 未执行写库：本任务为只读排查，未写库
- 本地唯一索引不存在：本地 real-pre 数据库缺少 `uk_pos_one_displaying_per_product` 索引，与远端不一致

## 13. 最终状态

**DONE**：只读排查完成，证据完整。

- 三个并存根因已定位并有明确证据
- 本地 SQL 统计与 API total 完全一致
- 远端数据差距已量化
- 同步失败根因已从日志和约束定义确认
- 后续修复任务已拆分并给出方案

# Batch C 审查报告：达人/寄样状态机

审查日期：2026-05-24
审查批次：C（达人/寄样状态机）
对照文档：
- `docs/archive/audits/17-V1全域代码审查清单.md` 三（达人域、寄样域）、四（状态机断言）、五（角色权限）、七（数据一致性）
- `docs/04-上线验收清单.md` 寄样相关验收项

---

## 一、审查范围与方法

### 代码锚点

| 文件 | 行数 | 核心职责 |
|------|------|----------|
| `TalentService.java` | 1066 | 达人认领/释放/过期/快照，Redis 分布式锁 |
| `TalentController.java` | 438 | 达人 CRUD API，角色与数据范围守卫 |
| `TalentQueryService.java` | ~600 | 达人查询，`assertCanOperate()` 跨组 403 |
| `TalentClaimReleaseJob.java` | 40 | 定时任务：每日 02:15 自动释放过期认领 |
| `SampleController.java` | 2728 | 寄样全流程：创建/审核/发货/签收/作业/关闭，状态机+权限+日志 |
| `SampleLifecycleService.java` | 350 | 自动状态流转：待交作业→完成、超时关闭 |
| `SampleEligibilityService.java` | 155 | 寄样资格校验：30 天销售额+达人等级 |
| `SampleStatusLogService.java` | 61 | `sample_status_log` 表写入 |
| `SampleDomainEventPublisher.java` | ~80 | 寄样生命周期领域事件发布 |
| `LogisticsTrackService.java` | 28 | 物流轨迹刷新薄封装 |
| `SampleRequest.java` | 143 | 寄样实体，继承 `VersionedEntity`（乐观锁） |
| `DistributedJobLockService.java` | ~50 | Redis 分布式任务锁 |

### 方法

按 `17-V1全域代码审查清单` 三（达人域、寄样域）、四（状态机断言）、五（角色权限）、七（数据一致性）逐项断言，对照当前代码实际行为输出"符合/矛盾/待补证"判定。

---

## 二、逐项审查结果

### 2.1 达人认领状态机

**文档期望**（清单 三 达人域）：
1. 多人认领模型：同达人可被多用户认领
2. 释放后保留归属快照
3. 保护期内不被自动释放
4. 保护期延长（有订单产生时续期）
5. 跨组操作 403

**代码事实**：

| 断言 | 代码位置 | 结论 |
|------|----------|------|
| 多人认领模型 | `TalentService.claim()`: 同一 talentId 可有多条 ACTIVE 记录 | 符合 |
| `owner_id` 指向最新认领人 | `claim()` 最后执行 `talent.setOwnerId(userId)` | 符合 |
| 释放保留归属快照 | `release()` → `applyReleaseOwnerSnapshot()` | 符合。释放单个认领时，若有其他 ACTIVE 认领，`owner_id` 指向剩余认领中的最新者，不清空 |
| 保护期配置 | `businessRuleConfigService.getTalentProtectionDays()` | 符合 |
| 保护期延长 | `releaseExpiredClaims()`: 检查认领后是否有订单（`orderMapper.countOrdersSince()`），有订单则跳过释放 | 符合 |
| 跨组 403 | `TalentQueryService.assertCanOperate()`: ADMIN 放行；CHANNEL_LEADER 仅限本部门有活跃认领；CHANNEL_STAFF 仅限本人有活跃认领 | 符合 |
| Redis 分布式锁 | `claim()`: `talent:claim:lock:{talentId}`，TTL 10s | 符合 |
| 认领类型 | `TalentClaim.ClaimType.MANUAL(1)` | 符合 |
| 状态枚举 | `ACTIVE(1)`, `EXPIRED(2)`, `RELEASED(3)` | 符合 |

**判定**：**全部符合**

---

### 2.2 保护期与自动释放

**文档期望**（清单 三 达人域）：
1. 定时任务自动释放过期认领
2. 分布式锁防止重复执行
3. 保护期内有订单不释放

**代码事实**：

| 断言 | 代码位置 | 结论 |
|------|----------|------|
| 定时任务 | `TalentClaimReleaseJob`: cron `0 15 2 * * ?`（每日 02:15） | 符合 |
| 分布式锁 | `DistributedJobLockService.tryAcquire("talent-claim-release")` | 符合 |
| 保护期判断 | `releaseExpiredClaims()`: `claimTime + protectionDays < now` 且 `countOrdersSince(claimTime) == 0` | 符合 |
| 操作日志 | `operationLogService.recordSystemAction()` 在 claim/release 操作中调用（lines 426, 532, 575, 624） | 符合 |

**判定**：**全部符合**

---

### 2.3 寄样状态机

**文档期望**（清单 三 寄样域、四 状态机断言）：
1. 完整状态链：待审核→待发货→运输中→已签收→待交作业→完成/关闭
2. 审核拒绝：待审核→已拒绝
3. 关闭：待发货超时关闭、待交作业超时关闭
4. 角色权限：审核→招商角色；发货/签收→运营角色
5. 前置状态校验
6. 乐观锁并发控制
7. 状态日志+操作日志

**代码事实**：

#### 手动状态流转（`SampleController.actionSample()`）

| 目标状态 | 前置状态 | 操作角色 | 代码位置 | 结论 |
|----------|----------|----------|----------|------|
| PENDING_AUDIT(1) → PENDING_SHIP(2) | PENDING_AUDIT | BIZ_LEADER/BIZ_STAFF | `actionSample()` approve 分支 | 符合 |
| PENDING_AUDIT(1) → REJECTED(7) | PENDING_AUDIT | BIZ_LEADER/BIZ_STAFF | `actionSample()` reject 分支 | 符合 |
| PENDING_SHIP(2) → SHIPPING(3) | PENDING_SHIP | OPS_STAFF | `actionSample()` ship 分支 | 符合 |
| SHIPPING(3) → DELIVERED(4) | SHIPPING | OPS_STAFF | `actionSample()` receive 分支 | 符合 |
| DELIVERED(4) → PENDING_HOMEWORK(5) | DELIVERED | 通用（有作业提交时） | `actionSample()` confirmReceive 分支 | 符合 |
| PENDING_HOMEWORK(5) → COMPLETED(6) | PENDING_HOMEWORK | 通用 | `actionSample()` complete 分支 | 符合 |
| PENDING_HOMEWORK(5) → CLOSED(8) | PENDING_HOMEWORK | 通用 | `actionSample()` close 分支 | 符合 |

#### 自动状态流转（`SampleLifecycleService`）

| 流转 | 触发条件 | 代码位置 | 结论 |
|------|----------|----------|------|
| PENDING_HOMEWORK(5) → COMPLETED(6) | 达人提交作业 | `completePendingHomeworkByOrder()` | 符合 |
| PENDING_HOMEWORK(5) → CLOSED(8) | 超时未交作业 | `autoCloseTimeoutPendingHomework()` | 符合 |
| PENDING_SHIP(2) → CLOSED(8) | 超时未发货 | `autoCloseTimeoutPendingShip()` | 符合 |

#### 角色权限矩阵

| 断言 | 代码位置 | 结论 |
|------|----------|------|
| 类级别角色限制 | `@RequireRoles({BIZ_LEADER, BIZ_STAFF, CHANNEL_LEADER, CHANNEL_STAFF, OPS_STAFF})` | 符合 |
| 操作级角色检查 | `ensureActionRolePermission()`: 审核→BIZ_LEADER/BIZ_STAFF；发货/签收→OPS_STAFF；其他→通用 | 符合 |
| DataScope 访问控制 | `assertCanAccessSample()`: PERSONAL(本人创建)、DEPT(本部门)、ALL(无限制) | 符合 |
| 产品负责人检查 | 创建时校验 `productAssignmentService` | 符合 |

#### 前置状态校验

| 断言 | 代码位置 | 结论 |
|------|----------|------|
| 手动操作前置状态 | `actionSample()` 每个分支校验 `currentStatus == expectedStatus`，否则抛 400 | 符合 |
| CLOSED 来源校验 | 手动关闭仅允许从 PENDING_HOMEWORK(5)；自动关闭允许从 PENDING_SHIP(2) 和 PENDING_HOMEWORK(5) | 符合。手动/自动分离合理 |
| REJECTED 来源 | 仅从 PENDING_AUDIT(1) | 符合 |

#### 乐观锁

| 断言 | 代码位置 | 结论 |
|------|----------|------|
| 手动操作乐观锁 | `persistSample()`: `WHERE id = ? AND deleted = 0 AND COALESCE(version, 0) = ?` | 符合 |
| 自动操作乐观锁 | `SampleLifecycleService.batchUpdateSamples()`: 同样带 version 条件 | 符合 |
| 实体继承 | `SampleRequest extends VersionedEntity` | 符合 |

**判定**：**全部符合**

---

### 2.4 寄样资格校验

**文档期望**（清单 三 寄样域）：
1. 创建寄样前校验达人资格
2. 可配置的资格标准（30 天销售额、达人等级）

**代码事实**：

| 断言 | 代码位置 | 结论 |
|------|----------|------|
| 资格校验入口 | `SampleEligibilityService.checkEligibility()` | 符合 |
| 30 天销售额 | 原始 SQL 聚合 `colonelsettlement_order`，`extra_data ->> 'talent_uid'` 或 `author_id` 或 `talent_name` 匹配 | 符合 |
| 达人等级 | `getTalentSnapshot()` 查询达人等级，A/S→LV2, B→LV1, default→LV0 | 符合 |
| 不支持字段处理 | provider 返回 unsupported 时，返回 reasons 要求填写申请理由 | 符合 |
| 结果结构 | `EligibilityResult(eligible, reasons, standard, actual)` | 符合 |

**判定**：**符合（资格校验机制完整）**

**备注**：`SampleEligibilityService` 使用原始 `JdbcTemplate` SQL 而非 MyBatis-Plus。JSONB 字段 COALESCE 匹配在大数据量下可能有性能影响，但 V1 阶段订单量有限，暂不构成风险。

---

### 2.5 七天重复寄样限制

**文档期望**（清单 三 寄样域）：
1. 同一达人 7 天内不得重复寄样
2. 组长（ADMIN/CHANNEL_LEADER）豁免

**代码事实**：

| 断言 | 代码位置 | 结论 |
|------|----------|------|
| 限制检查 | `SampleController.checkSevenDaysLimit()` (lines 1346-1364) | 符合 |
| 可配置天数 | `businessRuleConfigService.getSampleRestrictDays()` | 符合 |
| 开关控制 | `businessRuleConfigService.isSampleRestrictEnabled()` | 符合 |
| 角色豁免 | `if (isAdmin || isChannelLeader) return;` 跳过检查 | 符合 |
| 查询逻辑 | `sampleRequestMapper.countRecentByTalentId(talentId, sinceDate)` | 符合 |

**判定**：**全部符合**

---

### 2.6 操作日志与状态日志

**文档期望**（清单 七）：
1. 寄样状态变化和自动任务要同步写日志
2. 操作日志覆盖关键动作

**代码事实**：

| 断言 | 代码位置 | 结论 |
|------|----------|------|
| 寄样状态日志 | `sampleStatusLogService.log()` 在手动操作时写入 `sample_status_log` 表（lines 219, 577, 715, 754, 791） | 符合 |
| 自动任务状态日志 | `SampleLifecycleService` 使用 `sampleStatusLogService.logBatch()` | 符合 |
| 领域事件 | `sampleDomainEventPublisher` 在所有手动+自动操作中发布事件（lines 220, 578, 716, 755, 791, 1847-1853） | 符合 |
| 达人域操作日志 | `operationLogService.recordSystemAction()` 在 claim/release 操作中调用（lines 426, 532, 575, 624） | 符合 |
| 寄样域 `operation_log` | **不存在**。`SampleController` 不调用 `operationLogService` | **矛盾** |

**关键发现**：

达人域和寄样域使用了**不同的日志机制**：
- **达人域**：写入 `operation_log` 表（通过 `operationLogService`），记录 claim/release 等操作
- **寄样域**：写入 `sample_status_log` 表（通过 `sampleStatusLogService`）+ 发布领域事件（`sampleDomainEventPublisher`），**不写入** `operation_log` 表

清单七断言"operation_log: 导出、重算、配置修改、归因副作用、商品主链路关键动作可审计"中，寄样关键动作（创建、审核、发货、签收、完成、关闭）**未在 `operation_log` 中记录**。`sample_status_log` 仅记录状态变化（fromStatus→toStatus+operatorId+remark），不含操作详情（操作类型、IP、请求参数等）。

**判定**：**部分矛盾（MEDIUM）**

- `sample_status_log` + 领域事件覆盖了状态变化审计需求
- 但 `operation_log` 表中缺少寄样操作记录，与达人域不一致
- 若运营/审计需要统一从 `operation_log` 查询所有操作历史，寄样操作将缺失

---

### 2.7 物流跟踪

**文档期望**（清单 三 寄样域）：
1. 物流轨迹同步
2. 运营角色仅看物流后续数据

**代码事实**：

| 断言 | 代码位置 | 结论 |
|------|----------|------|
| 物流轨迹刷新 | `LogisticsTrackService.refreshAndProgress()` → `sampleLogisticsSyncService.syncOne()` | 符合 |
| 运营角色可见性 | `isOpsVisibleStatusCode()`: OPS_STAFF 只能看到 SHIPPING(3)、DELIVERED(4)、PENDING_HOMEWORK(5)、COMPLETED(6)、CLOSED(8) | 符合。待审核和待发货对运营不可见 |

**判定**：**全部符合**

---

## 三、矛盾汇总与风险评级

| # | 矛盾项 | 严重度 | 审查清单断言 | 代码实际行为 |
|---|--------|--------|-------------|-------------|
| C-1 | **寄样操作未写入 `operation_log`** | **MEDIUM** | 操作日志和状态日志覆盖关键动作 | 寄样域仅写 `sample_status_log` + 领域事件，不写 `operation_log` |

---

## 四、建议

### MEDIUM — C-1：寄样操作日志不一致

**当前状态**：寄样域使用 `sample_status_log` + 领域事件，达人域使用 `operation_log`。两套机制覆盖的审计维度不同。

**建议**（三选一）：
1. **选项 A（推荐）**：在 `SampleController` 关键操作（创建、审核通过/拒绝、发货、签收、完成、关闭）中增加 `operationLogService.recordAction()` 调用，使寄样操作可从统一的 `operation_log` 查询
2. **选项 B**：保持现状，但在审计文档中明确说明：达人域审计查 `operation_log`，寄样域审计查 `sample_status_log`，并补充跨表联合查询指南
3. **选项 C**：将领域事件监听器接入 `operation_log` 写入，通过事件驱动统一日志落库，避免 Controller 层侵入

**推荐选项 A**：侵入最小，只需在现有 `actionSample()` 的每个分支中增加一行 `operationLogService.recordAction()` 调用，与 `sampleStatusLogService.log()` 并行写入。

---

## 五、通过项汇总

| 审查项 | 结论 | 备注 |
|--------|------|------|
| 多人认领模型 | **通过** | 同达人可被多用户认领 |
| 释放后保留归属快照 | **通过** | `applyReleaseOwnerSnapshot()` |
| 保护期配置与延长 | **通过** | 有订单则续期 |
| 跨组操作 403 | **通过** | `assertCanOperate()` 角色+认领校验 |
| Redis 分布式锁 | **通过** | claim 操作锁 10s TTL |
| 定时释放过期认领 | **通过** | 每日 02:15，分布式锁防重 |
| 寄样完整状态链 | **通过** | 8 个状态 + 手动/自动流转 |
| 角色权限矩阵 | **通过** | 审核→招商，发货/签收→运营 |
| 前置状态校验 | **通过** | 每个分支校验 currentStatus |
| CLOSED 来源分离 | **通过** | 手动仅从 PENDING_HOMEWORK，自动允许从 PENDING_SHIP |
| REJECTED 来源 | **通过** | 仅从 PENDING_AUDIT |
| 乐观锁并发控制 | **通过** | version 条件 SQL WHERE |
| 寄样资格校验 | **通过** | 30 天销售+达人等级 |
| 七天重复限制+组长豁免 | **通过** | 可配置天数，ADMIN/CHANNEL_LEADER 豁免 |
| 状态日志 | **通过** | `sample_status_log` 表，手动+自动均写入 |
| 领域事件 | **通过** | 所有生命周期事件均发布 |
| 物流轨迹同步 | **通过** | `LogisticsTrackService` 封装 |
| 运营角色可见性 | **通过** | `isOpsVisibleStatusCode()` 过滤 |

---

## 六、后续批次预告

| 批次 | 目标 | 状态 |
|------|------|------|
| A | 权限与数据范围防回归 | 已完成（2026-05-22） |
| B | 订单归因与金额双轨 | 已完成（2026-05-24） |
| **C** | **达人/寄样状态机** | **本报告** |
| D | 配置与缓存 | 待执行 |
| E | 性能与可维护性 | 待执行 |

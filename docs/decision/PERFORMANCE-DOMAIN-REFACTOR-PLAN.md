# PERFORMANCE-DOMAIN-REFACTOR-PLAN 业绩域重构修复计划

**日期:** 2026-07-16
**作者:** Hermes Agent
**状态:** 架构师级修复路线图 (待评审 + 实施)
**关联:** 用户诊断（壮云问题）+ DDD 切片继续推进

---

## 1. 问题诊断（基于你 2026-07-16 反馈）

### 1.1 核心问题

> **系统表面上做了订单域与业绩域分层，但内部核心逻辑没有完整按照业绩域要求设计。**

### 1.2 6 个具体偏差

| # | 偏差 | 代码位置（已扫描）|
|---|------|---------------------|
| 1 | 订单域承担"业绩归属"语义，未分别建模渠道归属和招商归属 | `OrderDefaultAttributionPolicy.applyToOrder` line 100-105 |
| 2 | 招商归属写 `colonel_user_id`，查询用渠道维度的 `user_id/dept_id` | `OrderDefaultAttributionPolicy.java:100-102`（`order.setUserId(result.defaultChannelUserId())` + `order.setColonelUserId(result.defaultRecruiterId())`）|
| 3 | 订单只有统一归属状态，缺"招商已归属/渠道未归属"等多状态 | `AttributionService.java:46-48` 仅 2 状态（ATTRIBUTED/UNATTRIBUTED）|
| 4 | 归属来源/部门/版本/生效时间无完整快照 | `OrderAttributionInput` 6 字段，缺 4 维中 2 维（来源/版本/时间）|
| 5 | 订单重放不一定产生新事件 | `OrderSyncedEvent` 已发但 Performance 域不订阅（按需查询）|
| 6 | 订单表 + performance_records 两口径 | 两表独立计算 4 维归属（订单表 `colonel_user_id` / 性能表 `default_recruiter_user_id`）|

### 1.3 你指出的关键洞察

> **壮云的问题并不只是角色配置错误，而是现有内部模型无法稳定表达和传递"招商个人归属"。**
> 修改角色和回填订单只能作为数据修复，不能算业务逻辑已经符合业绩域设计。

---

## 2. 修复目标

按"小→大 + 真实 + 高价值"——**5 阶段修复路径**：

### 目标

| 阶段 | 目标 | 风险 | 工作量 |
|------|------|------|--------|
| Phase 1 | 事件订阅完整化（订单重放触发性能重算）| 🟢 低 | 0.5-1 天 |
| Phase 2 | 归属快照完整化（4 维 + 来源 + 版本 + 时间）| 🟡 中 | 1-2 天 |
| Phase 3 | 多归属状态（招商已归/渠道已归/全部归/未归）| 🟡 中 | 1-2 天 |
| Phase 4 | 业绩域为唯一权威口径（去除订单表计算）| 🔴 高 | 1-2 周 |
| Phase 5 | 字段语义明确（user_id 渠道/colonel_user_id 招商）+ 文档 | 🟢 低 | 0.5 天 |

**总工作量：3-4 周**（架构师级变更）

---

## 3. Phase 1: 事件订阅完整化（最低风险，最高价值）

### 3.1 目标

让 `PerformanceAggregateApplicationService` 订阅 `OrderSyncedEvent`，
订单重放时自动触发性能重算。

### 3.2 当前状态（已扫描）

| 组件 | 状态 |
|------|------|
| `event/OrderSyncedEvent.java` | ✅ 188 行事件 record 已存在 |
| `listener/OrderSyncedEventListener.java` | ✅ 154 行监听器，订阅仪表盘 + 达人保护期 |
| `service/DashboardPerformanceSummaryService.java` | ✅ 订阅 OrderSyncedEvent 更新仪表盘 |
| `domain/analytics/application/AnalyticsEventConsumer.java` | ✅ Analytics 域订阅 |
| **`domain/performance/application/PerformanceAggregateApplicationService.java`** | ❌ **不订阅事件**（按需查询）|

### 3.3 实施步骤

| # | 步骤 | 内容 | 文件 |
|---|------|------|------|
| 1 | 加订阅方法 | `PerformanceAggregateApplicationService.handleOrderSynced(OrderSyncedEvent)` | `PerformanceAggregateApplicationService.java` |
| 2 | 失效缓存 | 监听器调用 `aggregateRange` 重算 + 失效短 TTL 缓存 | 同上 |
| 3 | 监听器路由 | `OrderSyncedEventListener` 新增 `performanceAggregateApplicationService.handleOrderSynced` | `OrderSyncedEventListener.java` |
| 4 | 测试 | `OrderSyncedEventTest` 验证事件触发重算 | 新建测试 |
| 5 | 监控 | 监听器失败告警（重算失败不影响主流程）| log warn |

### 3.4 验收标准

- [ ] 订单重放 → OrderSyncedEvent → PerformanceAggregateApplicationService.handleOrderSynced
- [ ] 性能记录实时更新（不需手动调用查询）
- [ ] 缓存失效（下次查询重新计算）
- [ ] 异常隔离（监听器失败不影响订单同步主流程）
- [ ] 测试覆盖：成功路径 + 异常路径

### 3.5 风险评估

| 风险 | 等级 | 缓解 |
|------|------|------|
| 性能记录频繁更新导致 DB 压力 | 🟡 中 | 异步处理 + 短 TTL 缓存 |
| 监听器失败影响主流程 | 🟢 低 | try-catch 包住 + log error |
| 重复触发（多次订单同步）| 🟡 中 | 用 `event.orderId` 去重 |
| 性能记录和订单表数据不一致 | 🟢 低 | Phase 1 不解决（Phase 4） |

### 3.6 Commit 计划

```
commit 1: feat(performance): 订阅 OrderSyncedEvent 触发性能重算
- 新增 handleOrderSynced 方法
- 新增 OrderSyncedPerformanceEventListener（或合并到 OrderSyncedEventListener）
- 新增 ApplicationTest
- 1 commit, 可独立回退
```

---

## 4. Phase 2: 归属快照完整化（4 维 + 来源 + 版本 + 时间）

### 4.1 目标

`OrderAttributionInput` / `OrderDefaultAttributionResult` / `OrderAttributionInput`
扩展为 4 维快照 + 来源 + 映射版本 + 生效时间。

### 4.2 4 维快照模型

| 字段 | 含义 | 来源 |
|------|------|------|
| `source` | 归属来源（ORDER/SYNC/MANUAL/REPLAY）| `OrderAttributionInput` |
| `mappingVersion` | 映射版本（`pick_source_mapping.version`）| `OrderPickSourceMappingAdapter` |
| `effectiveTime` | 归属生效时间（默认 = now）| `OrderAttributionInput` |
| `recruiterDeptId` | 招商归属部门（已部分实现）| `OrderAttributionInput` |

### 4.3 实施步骤

| # | 步骤 | 内容 | 文件 |
|---|------|------|------|
| 1 | 扩展 record | `OrderAttributionInput` 加 source/mappingVersion/effectiveTime/recruiterDeptId | `OrderAttributionInput.java` |
| 2 | 扩展 record | `OrderDefaultAttributionResult` 加上述字段 | `OrderDefaultAttributionResult.java` |
| 3 | 解析 mapping version | `OrderPickSourceMappingAdapter` 加 `getMappingVersion(pickSource, pickExtra)` | `OrderPickSourceMappingAdapter.java` |
| 4 | OrderAttributionInput.from | 提取 source (从 rawPayload.context) | `OrderAttributionInput.java` |
| 5 | applyToOrder | 写新字段到订单表（DDL 迁移）| `OrderDefaultAttributionPolicy.java` |
| 6 | 性能域订阅 + 持久化 4 维 | PerformanceRecord 加 4 维字段（如缺失）| `PerformanceRecord.java` + DDL |
| 7 | 测试 | 4 维快照完整 + 跨域一致 | 新建测试 |

### 4.4 DDL 迁移

```sql
-- 订单表加 4 维快照字段
ALTER TABLE colonelsettlement_order
  ADD COLUMN attribution_source VARCHAR(20) DEFAULT 'ORDER',
  ADD COLUMN attribution_mapping_version BIGINT,
  ADD COLUMN attribution_effective_time TIMESTAMP,
  ADD COLUMN recruiter_dept_id UUID;

-- 性能表加 4 维快照字段（如缺失）
ALTER TABLE performance_record
  ADD COLUMN recruiter_dept_id UUID,
  ADD COLUMN channel_dept_id UUID;
```

### 4.5 风险评估

| 风险 | 等级 | 缓解 |
|------|------|------|
| DDL 迁移失败 | 🟡 中 | 写幂等迁移脚本（IF NOT EXISTS）|
| 旧数据无 4 维快照 | 🟡 中 | 默认值填充（source=ORDER, version=null, time=now）|
| 性能表字段冲突 | 🟢 低 | PerformanceRecord 已 4 维，只需加 dept |
| 性能记录与订单记录不一致 | 🟢 低 | Phase 1 已事件订阅，Phase 2 解决快照完整性 |

### 4.6 Commit 计划

```
commit 1: feat(order): OrderAttributionInput 加 4 维快照字段
commit 2: feat(order): OrderDefaultAttributionResult 加 4 维快照
commit 3: feat(order): applyToOrder 写 4 维到订单表 + DDL 迁移
commit 4: feat(performance): PerformanceRecord 加 dept_id + 持久化
commit 5: test(order, performance): 4 维快照跨域一致测试
```

---

## 5. Phase 3: 多归属状态（招商已归/渠道已归/全部归/未归）

### 5.1 目标

`AttributionService` 加 4 种归属状态（替换 2 状态）：
- `UNATTRIBUTED`：未归属（任一维度未归）
- `CHANNEL_ATTRIBUTED`：渠道已归属，招商未归属
- `RECRUITER_ATTRIBUTED`：招商已归属，渠道未归属
- `FULLY_ATTRIBUTED`：全部已归属

### 5.2 实施步骤

| # | 步骤 | 内容 | 文件 |
|---|------|------|------|
| 1 | 加 4 状态枚举 | `AttributionService` 新增 STATUS_* 常量 | `AttributionService.java` |
| 2 | 状态判定逻辑 | `OrderDefaultAttributionPolicy` 算多状态 | `OrderDefaultAttributionPolicy.java` |
| 3 | 状态机迁移 | 旧 ATTRIBUTED → FULLY_ATTRIBUTED，UNATTRIBUTED → UNATTRIBUTED | DDL + 脚本 |
| 4 | 4 维快照联动 | status 字段与 source/version/time 联动 | `OrderAttributionInput.java` |
| 5 | 测试 | 4 状态判定正确 | 新建测试 |

### 5.3 状态判定矩阵

| 渠道 | 招商 | 状态 |
|------|------|------|
| ✗ | ✗ | `UNATTRIBUTED` |
| ✓ | ✗ | `CHANNEL_ATTRIBUTED` |
| ✗ | ✓ | `RECRUITER_ATTRIBUTED` |
| ✓ | ✓ | `FULLY_ATTRIBUTED` |

### 5.4 风险评估

| 风险 | 等级 | 缓解 |
|------|------|------|
| 旧 ATTRIBUTED 数据迁移 | 🟡 中 | DDL 后台脚本 |
| 旧 OrderService 查询 `attributionStatus='ATTRIBUTED'` 失败 | 🟡 中 | 兼容查询（UNATTRIBUTED + 其他 3 状态都算"已归属"）|
| 状态机不一致 | 🟢 低 | 单元测试覆盖 4 状态 |

### 5.5 Commit 计划

```
commit 1: feat(order): AttributionService 加 4 状态枚举
commit 2: feat(order): OrderDefaultAttributionPolicy 多状态判定
commit 3: feat(order): DDL 数据迁移 (ATTRIBUTED → FULLY_ATTRIBUTED)
commit 4: test(order): 4 状态判定测试
```

---

## 6. Phase 4: 业绩域为唯一权威口径（最高风险）

### 6.1 目标

`performance_records` 为唯一权威业绩口径，
订单表保留归属字段（前端依赖）但**标记废弃**。

### 6.2 实施步骤

| # | 步骤 | 内容 | 文件 |
|---|------|------|------|
| 1 | 性能域补全 | PerformanceAggregateApplicationService 完整持久化 4 维 + 状态 + 来源 | `PerformanceAggregateApplicationService.java` |
| 2 | 订阅事件 | 订单重放 → 重算 performance_records | Phase 1 + 2 已就位 |
| 3 | 标记废弃 | 订单表 4 维归属字段加 `@Deprecated` + 注释"以 performance_records 为权威" | `ColonelsettlementOrder.java` |
| 4 | 兼容层 | OrderService 查询接口保留（标 deprecated），但实际从 performance_records 读 | `OrderService.java` |
| 5 | 监控 | 两表 diff 监控（订单表 colonel_user_id vs performance_records.default_recruiter_user_id）| 新建监控 |
| 6 | 旧数据修复 | 一次性脚本：旧订单表 4 维字段从 performance_records 回填 | DDL 脚本 |
| 7 | 文档 | 业绩域权威口径声明（DEV.md）| docs/ |

### 6.3 风险评估

| 风险 | 等级 | 缓解 |
|------|------|------|
| 旧数据不一致 | 🔴 高 | 一次性回填脚本 + 监控 |
| 前端依赖订单表字段 | 🔴 高 | 标记 deprecated + 保留字段（不删）|
| 性能域重算慢 | 🟡 中 | 异步 + 短 TTL 缓存 |
| 业务逻辑错位 | 🔴 高 | 完整单元测试 + 集成测试 |

### 6.4 Commit 计划

```
commit 1: feat(performance): PerformanceAggregateApplicationService 完整持久化 4 维
commit 2: feat(order): OrderService 4 维字段标记 @Deprecated
commit 3: feat(order, performance): 一次性回填脚本（从 performance_records 写订单表）
commit 4: monitor(order, performance): 两表 diff 监控
commit 5: docs: 业绩域权威口径声明
```

---

## 7. Phase 5: 字段语义明确（最低风险，文档级）

### 7.1 目标

- 明确 `user_id` = 渠道（不是招商！）
- 明确 `colonel_user_id` = 招商
- 改字段命名（如 `recruiter_user_id` / `channel_user_id`）—— **需要 DDL + 数据迁移**

### 7.2 实施步骤

| # | 步骤 | 内容 | 文件 |
|---|------|------|------|
| 1 | 文档化字段语义 | `ColonelsettlementOrder` 字段 Javadoc 明确 | `ColonelsettlementOrder.java` |
| 2 | 字段重命名 | `user_id` → `channel_user_id`（DDL + 实体）| DDL + `ColonelsettlementOrder.java` |
| 3 | 字段重命名 | `colonel_user_id` → `recruiter_user_id`（DDL + 实体）| DDL + `ColonelsettlementOrder.java` |
| 4 | 查询接口重命名 | OrderService.findByChannelUserId / findByRecruiterUserId | `OrderService.java` |
| 5 | 文档 | FIELD_SEMANTICS.md（字段含义表）| docs/ |

### 7.3 风险评估

| 风险 | 等级 | 缓解 |
|------|------|------|
| DDL 重命名 + 数据迁移 | 🔴 高 | 备份 + 测试环境演练 |
| 现有查询接口破坏 | 🔴 高 | 兼容层（保留旧字段名）|
| 前端影响 | 🟡 中 | 沟通 + 灰度 |

### 7.4 Commit 计划

```
commit 1: docs(order): FIELD_SEMANTICS.md 字段语义表
commit 2: docs(order): ColonelsettlementOrder 字段 Javadoc
commit 3: refactor(order): 字段重命名 + DDL 迁移 + 兼容层
```

---

## 8. 全局风险与缓解

### 8.1 风险矩阵

| 风险 | 等级 | 概率 | 影响 | 缓解 |
|------|------|------|------|------|
| Phase 4 业务逻辑错位 | 🔴 高 | 中 | 业绩数据错误 | 完整测试 + 监控 |
| DDL 迁移失败 | 🟡 中 | 中 | 数据不一致 | 幂等脚本 + 回滚 |
| 前端依赖破坏 | 🔴 高 | 中 | UI 异常 | 字段保留 + 兼容层 |
| 性能域订阅事件失败 | 🟢 低 | 低 | 性能记录不更新 | 异步 + 重试 + 告警 |
| 旧数据丢失 | 🔴 高 | 低 | 历史数据 | 完整备份 + 灰度 |

### 8.2 回退方案

每个 Phase 独立 commit，可独立回退：
- Phase 1 失败 → 移除事件订阅代码（性能域继续按需查询）
- Phase 2 失败 → 回退 DDL（保留旧字段默认值）
- Phase 3 失败 → 状态机回退（保留 2 状态 + alias）
- Phase 4 失败 → 保留订单表字段（不标记 deprecated）+ 文档说明
- Phase 5 失败 → 不重命名字段（仅文档说明）

### 8.3 监控指标

| 指标 | 阈值 | 告警 |
|------|------|------|
| 性能域订阅事件失败率 | > 5% | 高 |
| 两表 diff（订单表 vs performance_records）| > 0.1% | 高 |
| 4 维快照完整性（新数据）| < 100% | 中 |
| 订单重放触发性能重算 | < 95% | 中 |
| 监听器耗时 P99 | > 1s | 中 |

---

## 9. 实施时间线

按"小→大 + 真实"——**3-4 周**：

| Week | Phase | 任务 | 验证 |
|------|-------|------|------|
| Week 1 | Phase 1 + 5 | 事件订阅 + 字段语义文档 | 测试 PASS |
| Week 2 | Phase 2 | 4 维快照完整化 | DDL 迁移 + 测试 |
| Week 3 | Phase 3 | 多归属状态 | 状态机测试 + 监控 |
| Week 4 | Phase 4 | 业绩域权威口径 | 完整回归 + 灰度 |

---

## 10. 验收标准（全局）

### 10.1 功能性

- [ ] 订单重放触发 performance_records 重算（Phase 1）
- [ ] 4 维快照完整（来源/部门/版本/时间）（Phase 2）
- [ ] 多归属状态正确（4 种状态）（Phase 3）
- [ ] 业绩域为唯一权威（订单表字段 deprecated）（Phase 4）
- [ ] 字段语义明确（user_id=渠道，colonel_user_id=招商）（Phase 5）

### 10.2 质量

- [ ] 单元测试覆盖率 ≥ 80%
- [ ] 集成测试覆盖所有 Phase
- [ ] ArchUnit 红测 PASS（业务规则无违规）
- [ ] 性能测试（事件订阅不影响主流程性能）

### 10.3 业务验证

- [ ] 真实数据 diff 监控就位
- [ ] 旧数据回填脚本就位
- [ ] 业务方验证（业务正确性确认）
- [ ] 监控告警配置就位
- [ ] 文档完整（FIELD_SEMANTICS.md + 业绩域权威口径声明）

---

## 11. 后续待办（Phase 完成后）

| 任务 | 描述 |
|------|------|
| OrderService 业务迁出 | 1-2 周（god service 切 Application）|
| OrderSyncService 重构 | 按"事实 + 事件" |
| SampleLogisticsSyncService 真实切片 | 445 行 god service |
| 业绩域 DDD 切片完整化 | PerformanceAggregateApplicationService 反向依赖修复 |
| 持续监控 1 周 | 远端部署后真实数据验证 |

---

## 12. 决策记录

按 ask-matt "honest-sprint-eval"——

1. **业务问题 vs 数据修复** — 你的诊断明确"修改角色和回填订单只能作为数据修复，不能算业务逻辑已经符合业绩域设计"
2. **Phase 1 优先** — 最低风险，最高价值（事件订阅完整化）
3. **每 Phase 独立 commit** — 可独立回退
4. **旧数据保留** — 字段标 deprecated，不删除（前端兼容）
5. **性能域为唯一权威** — Phase 4 实施（最高风险）
6. **字段语义明确** — Phase 5 文档级（最低风险）

---

## 13. 关联 commit

- `387b3e10` P9.5 阶段 2 - 全局锁 owner-safe
- `676de811` 远端 real-pre 部署 PASS
- `79db5b62` STATUS-2026-07-14 DDD 切片整体收尾
- `9913866a` Slice 3 真实切片（Kuaidi100）

---

**待评审 + 实施** — Hermes Agent, 2026-07-16

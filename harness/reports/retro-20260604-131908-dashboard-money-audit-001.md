# DASHBOARD-MONEY-AUDIT-001 — Retro Summary

> **任务**：DASHBOARD-MONEY-AUDIT-001 — 数据看板资金口径只读审查
> **时间**：2026-06-04 13:19 +08:00
> **类型**：retro（任务后复盘）
> **状态**：DONE（docs-only / 只读审查）

---

## 1. 本次审查经验

### 1.1 审查范围覆盖

- 后端：11 个核心 Java 文件完整读取（Controller × 2、Service × 5、Entity × 2、VO × 2）
- 前端：4 个核心文件完整读取（Vue × 2、TS × 1、API × 1）
- 数据库：2 张核心表只读 SELECT 对账（colonelsettlement_order 460 行、performance_records 404 行）
- 文档：V1 范围、不做清单、验收清单、harness 状态文件全部读取

### 1.2 审查效率

- 8 个维度（A-H）全部完成
- 10 个问题已识别（4 P0 + 4 P1 + 2 P2）
- SQL 对账与代码审查交叉验证，P0-001 同时有代码证据和 SQL 数据证据

---

## 2. 发现的系统性问题

### 2.1 双轨模型"形式完整但实质不完整"

实体层（Entity）双轨字段齐全，但计算层（PerformanceCalculationService）的回退逻辑破坏了结算轨的语义完整性。这意味着：

- **字段存在 ≠ 数据正确**：字段设计对了，但写入逻辑有 bug
- **回退模式需要审慎**：`if (value == 0) fallback` 模式在双轨场景下危险，因为 0 本身就是合法值（未结算=0）

### 2.2 新旧接口并存导致口径分裂

- `/dashboard/summary`（旧版单轨）和 `/dashboard/metrics`（新版双轨）并存
- 旧版接口字段映射错误（settle_colonel_commission 当服务费）
- 前端两个看板页面共存，V1 验收引用关系不明确

### 2.3 V1 不做清单落地不彻底

- V1 明确不做毛利，但 MetricsVO 含 grossProfit、前端多处展示
- "不做"需要从计算→存储→API→前端全链路隐藏，当前只在需求文档层面"不做"

---

## 3. 是否需要更新 harness

### 3.1 KNOWN_ISSUES.md

**需要新增**：DASHBOARD-MONEY-AUDIT-001 审查发现的 4 个 P0 问题

### 3.2 HARNESS_DEBT.md

**不需要新增**：本次发现的全是业务域问题，不属于 harness 工程债务

### 3.3 p0-p1-register.md

**需要新增**：DASH-MONEY-P0-001 到 P0-004 登记为 RISK

### 3.4 QUALITY_LEDGER.md

**需要更新**：分析模块评分可能需要下调（存在 P0 级口径错误）

---

## 4. 下一步建议

### 4.1 推荐下一步（按优先级）

1. **DASHBOARD-MONEY-FIX-001**：修复 P0-001（settle_amount 回退）+ P0-003（聚合污染）+ P0-004（毛利隐藏）+ P1-002（talentCommission）
2. **DASHBOARD-MONEY-FIX-002**：旧版接口治理（废弃 or 修复）
3. **DASHBOARD-MONEY-TEST-001**：补齐双轨隔离测试、settle_amount=0 测试

### 4.2 业务侧

- 等待 FIX-001 修复后重新执行 SQL 对账验证
- 确认 V1 验收引用哪个看板页面（旧版 or 新版 or 两者）

---

## 5. 经验教训

1. **回退模式在双轨场景下危险**：`if (value == 0) fallback` 把"未结算"（合法值 0）误判为"缺数据"并用其他值填充。双轨字段必须允许 0 值。
2. **新旧接口并存是口径分裂的温床**：如果 V1 只需要双轨接口，应明确废弃旧版，避免验收时引用错误接口。
3. **"V1 不做"需要全链路落地**：从 Entity → Service → VO → API → 前端，每一层都需要检查是否有 V1 不做的字段泄漏。
4. **SQL 对账是发现计算层 bug 的最有效手段**：代码审查可能遗漏回退逻辑的影响，但 SQL 数据对比直接暴露 settle_amount=771125 ≠ 0 的矛盾。
5. **只读审查的价值**：不修改代码的纯审查可以在不引入新 bug 的情况下发现系统性问题，为后续修复提供精确靶点。

---

## 6. 状态

- **DONE**（docs-only / 只读审查）
- 工作区 clean（本任务新增 3 个 untracked 报告待 commit）
- 4 P0 + 4 P1 + 2 P2 问题已登记
- 下一步：DASHBOARD-MONEY-FIX-001（推荐）

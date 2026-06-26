# ADR-011 订单金额 Policy 拆解为 3 Policy + 1 Writer

> 范围标记：[V2 必做] 结构性深化（不引入新行为）。
> 关联：ADR-009（订单金额双轨结算口径冻结，本 ADR 是其执行细化）；CONTEXT.md "订单金额事实"节（本 ADR 触发的术语补充）。
> 触发：`/improve-codebase-architecture` skill 扫描 2026-06-26；grilling Q1-Q5 收尾答案。

## 状态

- [V2 必做] 已采纳。结构性深化（不引入新行为），零资金偏差红线强制。

## 背景

- [V2 必做] `OrderAmountMapperPolicy.java`（740 行）`domain/order/policy/` 标为"纯 Policy"，但实测藏 3 个独立子概念（alias 字典、字段赋值规则、兜底链）—— interface ≈ implementation，删测不通过。
- [V2 必做] `OrderAmountMappingRouter.java` 的 policy-enabled adapter 曾把 `DualTrackAmounts` 第 7/8 位误当成可丢弃字段：`toDualTrack()` 写 `0L, 0L`，`toMapped()` 写 `null, null`。源码证据显示这两位实际是 `estimateServiceFeeExpense` / `effectiveServiceFeeExpense`，不是 `serviceFeeRate` / `commissionRate`。
- [V2 必做] 此 bug 会导致 DDD order amount policy 开关启用时双机构服务费支出被清零，进而影响业绩域服务费收益与毛利消费事实。`serviceFeeRate` / `commissionRate` 当前是 raw payload 或商品快照中的费率提示，不是 `ColonelsettlementOrder` 落库金额事实。
- [V2 必做] `OrderAmountMapperPolicy` 是 ADR-009 line 29 明确点名的对象："`OrderAmountMapperPolicy` 和 1603 结算链路禁止 estimate -> effective fallback"——本 ADR 是该声明的执行细化。
- [V2 必做] 唯一调用方为 `OrderAmountMappingRouter`（7 个方法），属"1 个 caller → hypothetical seam"阶段，但 3 子概念足够独立值得深化。
- [历史归档] 旧归档文档（`docs/归档/`）未涉及本结构问题，不构成参考。

## 决策

### 拆解形态

[V2 必做] 将 `OrderAmountMapperPolicy` 拆为 **3 Policy + 1 Writer**：

| 模块 | 路径 | 行为 | 接口形态 |
| --- | --- | --- | --- |
| `OrderPayAmountAliasPolicy` | `domain/order/policy/` | 给定 `OutputField` + raw payload → 解析 raw 数值 + 用了哪个 alias | `resolve(raw, field) → RawValue(amount, usedAliasKey)` |
| `OrderAmountAssignmentPolicy` | `domain/order/policy/` | 给定 raw value + track + existing → 写到目标字段（firstPositive / firstNonNegative / firstFromInstitutions / calculateServiceFeeIncome） | `assign(raw, field, track, existing) → ResolvedValue(amount, warnings)` |
| `OrderAmountFallbackPolicy` | `domain/order/policy/` | 5 个允许的兜底链（详见 CONTEXT.md "兜底规则"子节） | `apply(current, existing, track) → FinalValue` |
| `OrderAmountWriter` | `domain/order/application/` | 编排：AliasPolicy → AssignmentPolicy → FallbackPolicy → `MappedAmounts` | `write(raw, existing, track) → MappedAmounts` |

[V2 必做] `OrderAmountMappingRouter.map()` 退化为单行委派：`OrderAmountWriter.write(raw, existing, track)`。

### 执行顺序

[V2 必做] **先修数据丢失 bug，再拆 Policy**（grilling Q4 答案）。

| 阶段 | 任务 | 灰度 |
| --- | --- | --- |
| 0 | 本 ADR + CONTEXT.md 术语补充 | - |
| 1 | 修 `toDualTrack` / `toMapped` serviceFeeExpense 字段丢失 | 1 周 |
| 2 | 抽 `OrderPayAmountAliasPolicy` | 1 周 |
| 3 | 抽 `OrderAmountAssignmentPolicy` | 1 周 |
| 4 | 抽 `OrderAmountFallbackPolicy` + `OrderAmountWriter` | 1 周 |
| 5 | 删旧 `OrderAmountMapperPolicy` 文件 | - |

[V2 必做] 每阶段 parity test 必须 100% 转绿才能进下阶段；任何阶段失败立即回滚（`git revert` + `DddRefactorProperties.ddd.order.amount=false`）。

### 红线

[V2 必做] **零资金偏差**：所有资金字段（payAmount / settleAmount / estimateServiceFee / effectiveServiceFee / estimateTechServiceFee / effectiveTechServiceFee）byte-for-byte 相等。

[V2 必做] legacy `OrderDualTrackAmountResolver` 不修改；`DddRefactorProperties` 灰度开关保留。

[V2 必做] `ColonelsettlementOrder` 字段不动；DB schema 不动；ADR-009 决策不动。

### 命名

[V2 必做] 沿用代码现状的 `settleAmount` / `SETTLEMENT_STRICT` 命名（grilling Q5 答案 A）—— 业务侧通过 CONTEXT.md "订单金额事实"节 + "解析轨道"子节补充术语。`settleColonelCommission` / `settleColonelTechServiceFee` 标记为"历史兼容字段"（CONTEXT.md 同节），新代码不应再作主字段名。

## 与 ADR-009 的边界

| 维度 | ADR-009 | ADR-011（本文件） |
| --- | --- | --- |
| 主题 | 资金字段口径（双轨、独立事实、禁止兜底） | `OrderAmountMapperPolicy` 模块拆解（3 Policy + 1 Writer） |
| 状态 | 有效 | 有效（执行细化） |
| 范围 | 业务口径、约束 | 结构深化、模块边界 |
| 改动类型 | 不可逆（口径冻结） | 可逆（每个阶段独立 PR，可单独回滚） |
| 验证 | 业务事实一致性 | parity test 转绿 + 资金 byte-for-byte 相等 |

## 与 CONTEXT.md 的关系

[V2 必做] 触发 CONTEXT.md 新增"订单金额事实"节（5 字段术语 + 2 轨 + alias 规则 + 5 兜底链 + 历史兼容字段）。

[V2 必做] 触发 `CONTEXT.md` 顶部"业务闭环"节末尾插入子节；不影响其他 BC 术语。

[V2 必做] 行数控制在 ≤ 200（harness/scripts/check-harness-limits.ps1）；若超过则拆为根目录总览 + `docs/术语/订单金额事实.md` 两文件。

## 影响

- [V2 必做] `OrderAmountMapperPolicy` 在阶段 5 物理删除；备份留 `harness/manifests/2026-XX-XX-order-amount-policy-decompose.json`。
- [V2 必做] `OrderAmountMappingRouter.map()` 阶段 4 后退化为单行委派；`toDualTrack` / `toMapped` adapter 行为变更（修 bug，`estimateServiceFeeExpense` / `effectiveServiceFeeExpense` 不再丢失）。
- [V2 必做] 新增 3 Policy + 1 Writer + 至少 200+ 测试用例。
- [V2 必做] 工期 6 周 + 1 天（4 周灰度 + 2 周开发 + 1 天清理）。
- [V2 必做] 不影响 `OrderService`、其他 BC、DB、前端。

## 验证方式

- [V2 必做] 阶段 1: `OrderAmountMappingRouterTest` policy-enabled serviceFeeExpense 回归用例全绿，并与 legacy `OrderDualTrackAmountResolver` 结果保持一致。
- [V2 必做] 阶段 2: `OrderPayAmountAliasPolicyTest` 30+ 用例全绿（8 字段 × 8 alias 矩阵）。
- [V2 必做] 阶段 3: `OrderAmountAssignmentPolicyTest` 40+ 用例全绿（8 字段 × 2 轨 × 4 边界）。
- [V2 必做] 阶段 4: `OrderAmountFallbackPolicyTest` 120+ 用例 + `OrderAmountWriterTest` 200+ 用例全绿。
- [V2 必做] 阶段 5: `mvn test -Dtest='OrderAmount*' -DfailIfNoTests=false` 全绿。
- [V2 必做] 灰度期间每天跑一次 full test，零 fail。
- [V2 必做] ADR-009 line 20-22 行为不变量由专门的 5 个 parity test 覆盖（estimate→effective 禁止等）。

## 与已有 ADR 的互引

- 引用 ADR-009（行 20-22 行为不变量作为本 ADR 验证基准）
- 引用 ADR-003（订单域只存上游事实）
- 引用 ADR-004（业绩域负责双轨金额和最终业绩计算）

## 风险

| 风险 | 缓解 |
| --- | --- |
| 阶段 1 修 bug 引入新 bug | 双轨灰度 + 1 周观察 + parity test |
| 阶段 2 搬移 alias 漏一个 | 30+ 用例覆盖 8 字段 × 8 alias 矩阵 |
| 阶段 3 兜底规则边界场景漏 | 40+ 用例覆盖 4 边界 × 8 字段 × 2 轨 |
| 阶段 4 Writer 内部 ordering 错 | 200+ 端到端测试 |
| 灰度期间 real-pre 出现差异 | `DddRefactorProperties` 一键回滚 |

## 变更历史

- v1.0 (2026-06-26): 初版。`/improve-codebase-architecture` skill 触发；grilling 5 题答案落地。

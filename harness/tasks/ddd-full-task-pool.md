# DDD 重构全部任务池（53 项）

> 创建时间：2026-06-12
> 领域边界：订单域=同步/存储/默认归因/事件；业绩域=归属/提成/毛利/独家；用户域=身份/权限/组织架构/data_scope；分析模块=汇总表

---

## Phase 0：基础防护与重构准入

| 编号 | 任务 | Agent | 优先级 |
|------|------|-------|--------|
| DDD-BASE-001 | 新增 DDD 重构安全开关 | Infra Agent | P0 |
| DDD-BASE-002 | 建立当前行为 Characterization Tests | Test Agent | P0 |
| DDD-BASE-003 | 跨域依赖扫描与 ArchUnit 防回退 | Architecture Guard Agent | P0 |
| DDD-BASE-004 | 建立 DDD 目标包结构但不迁移逻辑 | Architecture Guard Agent | P0 |

## Phase 1：用户域统一数据范围

| 编号 | 任务 | Agent | 优先级 |
|------|------|-------|--------|
| DDD-USER-001 | 新增 UserDomainFacade | User Agent | P0 |
| DDD-USER-002 | 订单域数据范围改走 UserDomainFacade | User Agent + Order Agent | P0 |
| DDD-USER-003 | 寄样域数据范围改走 UserDomainFacade | User Agent + Sample Agent | P0 |
| DDD-USER-004 | 业绩域和分析模块数据范围改走 UserDomainFacade | User + Performance + Analytics Agent | P0 |

## Phase 2：配置域统一配置读取

| 编号 | 任务 | Agent | 优先级 |
|------|------|-------|--------|
| DDD-CONFIG-001 | 新增 ConfigDomainFacade | Config Agent | P0 |
| DDD-CONFIG-002 | 寄样域和达人域配置读取改走 ConfigDomainFacade | Config + Sample + Talent Agent | P0 |
| DDD-CONFIG-003 | 业绩域和商品域配置读取改走 ConfigDomainFacade | Config + Performance + Product Agent | P0 |
| DDD-CONFIG-004 | 配置更新事件兼容层 ConfigUpdatedEvent | Config Agent | P1 |

## Phase 3：商品域重构

| 编号 | 任务 | Agent | 优先级 |
|------|------|-------|--------|
| DDD-PRODUCT-001 | 新增 ProductDomainFacade | Product Agent | P0 |
| DDD-PRODUCT-002 | 抽取 ProductDisplayPolicy 商品展示规则 | Product Agent | P0 |
| DDD-PRODUCT-003 | 抽取 ProductPinPolicy 商品置顶规则 | Product Agent | P1 |
| DDD-PRODUCT-004 | 复制讲解重构为 CopyPromotionApplicationService + DouyinConvertPort | Product Agent | P0 |
| DDD-PRODUCT-005 | 快速寄样入口改走 SampleApplicationPort | Product Agent + Sample Agent | P0 |

## Phase 4：订单域重构

| 编号 | 任务 | Agent | 优先级 |
|------|------|-------|--------|
| DDD-ORDER-001 | 抽取 OrderSyncApplicationService | Order Agent | P0 |
| DDD-ORDER-002 | 抽取 OrderAmountMapperPolicy 双轨金额映射 | Order Agent | P0 |
| DDD-ORDER-003 | 抽取 OrderStatusMapperPolicy 订单状态映射 | Order Agent | P0 |
| DDD-ORDER-004 | 抽取 OrderDefaultAttributionPolicy 默认归因 | Order Agent | P0 |
| DDD-ORDER-005 | 新增 OrderDomainEventPublisher | Order Agent | P1 |
| DDD-ORDER-006 | 订单查询模型和同步模型解耦 | Order Agent | P1 |

## Phase 5：业绩域重构

| 编号 | 任务 | Agent | 优先级 |
|------|------|-------|--------|
| DDD-PERF-001 | 抽取 PerformanceCalculationApplicationService | Performance Agent | P0 |
| DDD-PERF-002 | 抽取 PerformanceMoneyPolicy 双轨金额公式 | Performance Agent | P0 |
| DDD-PERF-003 | 抽取 PerformanceAttributionPolicy 最终归属策略 | Performance Agent | P0 |
| DDD-PERF-004 | 新增 PerformanceQueryFacade | Performance Agent | P0 |
| DDD-PERF-005 | 独家商家服务独立化 | Performance Agent | P1 |

## Phase 6：达人域重构

| 编号 | 任务 | Agent | 优先级 |
|------|------|-------|--------|
| DDD-TALENT-001 | 新增 TalentDomainFacade | Talent Agent | P0 |
| DDD-TALENT-002 | 抽取 TalentClaimPolicy 达人认领策略 | Talent Agent | P0 |
| DDD-TALENT-003 | 抽取 TalentTagPolicy 和 TalentAddressPolicy | Talent Agent | P1 |
| DDD-TALENT-004 | 独家达人判定服务独立化 | Talent Agent | P1 |

## Phase 7：寄样域重构

| 编号 | 任务 | Agent | 优先级 |
|------|------|-------|--------|
| DDD-SAMPLE-001 | 抽取 SampleApplicationService | Sample Agent | P0 |
| DDD-SAMPLE-002 | 抽取 SampleEligibilityPolicy 寄样申请校验 | Sample Agent | P0 |
| DDD-SAMPLE-003 | 抽取 SampleStateMachine 寄样状态机 | Sample Agent | P0 |
| DDD-SAMPLE-004 | 订单事件驱动寄样交作业完成 | Sample Agent + Order Agent | P1 |
| DDD-SAMPLE-005 | 寄样查询和导出拆为 SampleQueryService | Sample Agent | P1 |

## Phase 8：分析模块重构

| 编号 | 任务 | Agent | 优先级 |
|------|------|-------|--------|
| DDD-ANALYTICS-001 | 新增 AnalyticsEventConsumer 兼容层 | Analytics Agent | P1 |
| DDD-ANALYTICS-002 | Dashboard Shadow Compare 新旧看板影子对账 | Analytics Agent | P1 |

## Phase 9：事件与 Outbox

| 编号 | 任务 | Agent | 优先级 |
|------|------|-------|--------|
| DDD-EVENT-001 | 新增 Outbox 表和发布接口 | Infra Agent | P1 |
| DDD-EVENT-002 | 订单已同步事件写入 Outbox | Order Agent + Infra Agent | P1 |
| DDD-EVENT-003 | Outbox Dispatcher Dry Run | Infra Agent | P2 |

## Phase 10：God Service 瘦身

| 编号 | 任务 | Agent | 优先级 |
|------|------|-------|--------|
| DDD-SLIM-PRODUCT-001 | ProductService 只瘦身商品展示规则 | Product Agent | P1 |
| DDD-SLIM-ORDER-001 | OrderSyncService 只瘦身金额映射 | Order Agent | P0 |
| DDD-SLIM-ORDER-002 | OrderSyncService 只瘦身默认归因 | Order Agent | P0 |
| DDD-SLIM-PERF-001 | 业绩服务只瘦身金额公式 | Performance Agent | P0 |
| DDD-SLIM-SAMPLE-001 | SampleService 只瘦身申请校验 | Sample Agent | P1 |

## Phase 11：跨域 Mapper 清理

| 编号 | 任务 | Agent | 优先级 |
|------|------|-------|--------|
| DDD-CLEAN-001 | 订单域移除 SysUserMapper 直接注入 | Order Agent + User Agent | P1 |
| DDD-CLEAN-002 | 寄样域移除商品/达人/用户/配置跨域 Mapper | Sample Agent | P1 |
| DDD-CLEAN-003 | 业绩域移除订单/商品/达人/配置/用户跨域 Mapper | Performance Agent | P1 |
| DDD-CLEAN-004 | 商品域移除寄样域直接依赖 | Product Agent + Sample Agent | P1 |

## Phase 12：前端兼容与最终验收

| 编号 | 任务 | Agent | 优先级 |
|------|------|-------|--------|
| DDD-FRONT-001 | 订单明细字段来源标注与前端回归 | Frontend Agent | P1 |
| DDD-VERIFY-001 | DDD 重构阶段性全链路验收 | Integration + Review Agent | P0 |

---

## 推荐执行顺序

```text
Phase 0:  DDD-BASE-001 → 002 → 003 → 004
Phase 1:  DDD-USER-001 / CONFIG-001 / PRODUCT-001 / TALENT-001（并行）
Phase 2:  DDD-USER-002 → 003 → 004
Phase 3:  DDD-CONFIG-002 → 003 → 004
Phase 4:  DDD-PRODUCT-002 → 003 → 004 → SAMPLE-001 → PRODUCT-005
Phase 5:  DDD-ORDER-001 → 002 → 003 → 004 → 005 → 006
Phase 6:  DDD-PERF-001 → 002 → 003 → 004 → 005
Phase 7:  DDD-TALENT-002 → 003 → 004
Phase 8:  DDD-SAMPLE-002 → 003 → 004 → 005
Phase 9:  DDD-ANALYTICS-001 → 002
Phase 10: DDD-EVENT-001 → 002 → 003
Phase 11: DDD-SLIM-* → DDD-CLEAN-*（CLEAN 必须最后）
Phase 12: DDD-FRONT-001 → DDD-VERIFY-001
```

---

## 并行批次

### Batch 0：只做防护，不动业务代码
Infra Agent → BASE-001 | Test Agent → BASE-002 | Architecture Agent → BASE-003 / 004

### Batch 1：只新增 Facade，不替换调用
User Agent → USER-001 | Config Agent → CONFIG-001 | Product Agent → PRODUCT-001 | Talent Agent → TALENT-001

### Batch 2：领域内 Policy 抽取（每 Agent 只改自己领域）
ORDER-002 / 003 | PERF-002 | PRODUCT-002 / 003 | TALENT-002 | SAMPLE-002 / 003

### Batch 3：ApplicationService 与 QueryService
ORDER-001 / 006 | PERF-001 / 004 | SAMPLE-001 / 005 | PRODUCT-004

### Batch 4：跨域调用替换（半串行，不可乱并行）
USER-002 → 003 → 004 | CONFIG-002 → 003 | PRODUCT-005 | ORDER-004 | PERF-003

### Batch 5：事件、分析、Outbox
ORDER-005 | CONFIG-004 | SAMPLE-004 | ANALYTICS-001 / 002 | EVENT-001 → 002 → 003

### Batch 6：瘦身与清理（CLEAN 必须最后）
SLIM-PRODUCT-001 | SLIM-ORDER-001 / 002 | SLIM-PERF-001 | SLIM-SAMPLE-001 | CLEAN-001 → 002 → 003 → 004

### Batch 7：前端与最终验收
FRONT-001 → VERIFY-001

---

## 最优先执行 Top 10

| 序号 | 编号 | 任务 |
|------|------|------|
| 1 | DDD-BASE-001 | 新增 DDD 重构安全开关 |
| 2 | DDD-BASE-002 | 建立当前行为 Characterization Tests |
| 3 | DDD-BASE-003 | 跨域依赖扫描与 ArchUnit 防回退 |
| 4 | DDD-BASE-004 | 建立 DDD 目标包结构 |
| 5 | DDD-USER-001 | 新增 UserDomainFacade |
| 6 | DDD-CONFIG-001 | 新增 ConfigDomainFacade |
| 7 | DDD-PRODUCT-001 | 新增 ProductDomainFacade |
| 8 | DDD-TALENT-001 | 新增 TalentDomainFacade |
| 9 | DDD-ORDER-002 | 抽取 OrderAmountMapperPolicy |
| 10 | DDD-PERF-002 | 抽取 PerformanceMoneyPolicy |

> 完成这 10 项后，项目进入"可安全重构"状态。

---

状态跟踪见 `ddd-multi-agent-board.md`，依赖图见 `ddd-task-dependency-graph.md`。

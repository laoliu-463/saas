# DDD Multi-Agent Board

更新时间：2026-06-10  
分支：`feature/auth-system`  
HEAD：`a60a045a`（含 DDD-ANALYTICS-002）

## 图例

| 状态 | 含义 |
|------|------|
| DONE | 已 commit + report |
| PARTIAL | 已 commit 但测试/边界未全绿 |
| WIP | 工作区未提交 |
| TODO | 未开始 |
| BLOCKED | 被依赖或冲突阻塞 |

## Batch 0 — 防护基线

| task_id | owner | 状态 | commit | 报告 |
|---------|-------|------|--------|------|
| DDD-BASE-001 | Infra | DONE | `8f1244a6` | `harness/reports/ddd-base-001-refactor-switches.md` |
| DDD-BASE-003 | Architecture Guard | DONE | `86b16922` | `harness/reports/ddd-dependency-map.md` |
| DDD-BASE-004 | Infra | DONE | `1ab9cd92` | `harness/reports/ddd-base-004-package-structure.md` |
| DDD-BASE-002 | Test | PARTIAL | — | `harness/reports/ddd-base-002-characterization.md`；`CharacterizationBaselineTest` 受 Sample 循环依赖阻塞 |
| Coordinator 看板 | Coordinator | **本文件** | — | — |

## Batch 1 — Facade（只新增，少替换）

| task_id | owner | 状态 | 说明 |
|---------|-------|------|------|
| DDD-USER-001 | User | DONE | `UserDomainFacade` |
| DDD-USER-002 | User | DONE | 订单域读用户 |
| DDD-CONFIG-001~004 | Config | DONE/PARTIAL | Facade + Event；CONFIG-003 测试 2 fail |
| DDD-PRODUCT-001 | Product | TODO | `ProductDomainFacade` |
| DDD-TALENT-001 | Talent | TODO | `TalentDomainFacade` |
| DDD-PERF-001 | Performance | TODO | `PerformanceQueryFacade` |
| DDD-PRODUCT-005 | Product | DONE | `SampleApplicationPort` |

## Batch 2 — Policy

| task_id | owner | 状态 | 说明 |
|---------|-------|------|------|
| DDD-PRODUCT-002 | Product | DONE | `ProductDisplayPolicy` |
| DDD-ORDER-001 | Order | DONE | `OrderSyncApplicationService` |
| DDD-ORDER-002 | Order | WIP | `OrderAmountMapperPolicy` 未提交 |
| DDD-PERF-002 | Performance | TODO | `PerformanceMoneyPolicy` |
| DDD-SAMPLE-006 | Sample | TODO | `SampleStateMachine` 抽取 |
| DDD-TALENT-002 | Talent | TODO | `TalentClaimPolicy` |

## Batch 3 — 跨域替换

串行，由 Integration Agent 控制。见 `ddd-task-dependency-graph.md`。

## Batch 4 — 事件 / Analytics

| task_id | owner | 状态 |
|---------|-------|------|
| DDD-ANALYTICS-001 | Analytics | DONE |
| DDD-ANALYTICS-002 | Analytics | DONE（shadow compare） |
| DDD-OUTBOX-001 | Infra + 各领域 | TODO |

## 当前 Sprint（Coordinator 裁定）

| 优先级 | task_id | owner | 原因 |
|--------|---------|-------|------|
| P0 | DDD-SAMPLE-005-FIX | Sample | 循环依赖；全量测试基线 |
| P0 | DDD-CONFIG-003-FIX | Config | `DddConfig003ConfigRoutingTest` 2 failures |
| P1 | DDD-ORDER-002 | Order | 提交 Amount Policy + 开关委派 |
| P1 | DDD-USER-003 | User | 扩展 Facade 消费、减 whitelist |
| P2 | DDD-PRODUCT-001 | Product | ProductDomainFacade |

## 并行许可（P0 修复后）

可并行：**User-003** + **Product-001** + **Talent-001**（无共享文件）  
不可并行：**Order-002** 与任何改 `OrderSyncService` 的任务

# DDD Multi-Agent Board

> 完整 53 项任务定义见 `ddd-full-task-pool.md`，依赖图见 `ddd-task-dependency-graph.md`

更新时间：2026-06-12  
分支：`feature/ddd/DDD-SAMPLE-005-FIX-sample-agent`  
HEAD：`0337d07f`（Batch3 WIP：TALENT-003 Facade 路由）

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
| DDD-BASE-002 | Test | PARTIAL | — | Sample 循环依赖已解除；Characterization 仍 PARTIAL |
| Coordinator 看板 | Coordinator | **本文件** | — | — |

## Batch 1 — Facade（只新增，少替换）

| task_id | owner | 状态 | 说明 |
|---------|-------|------|------|
| DDD-USER-001 | User | DONE | `UserDomainFacade` |
| DDD-USER-002 | User | DONE | 订单域读用户 |
| DDD-USER-003 | User | DONE | `834ca16e` 跨域 SysUserMapper→UserDomainFacade |
| DDD-USER-004 | User | DONE | `f3846415` DataScope 迁移至 domain/user |
| DDD-CONFIG-001~004 | Config | DONE | Facade + Event；CONFIG-003 路由单测已绿 |
| DDD-PRODUCT-001 | Product | DONE | `42843d09` |
| DDD-TALENT-001 | Talent | DONE | `60b9d062` |
| DDD-PERF-001 | Performance | TODO | `PerformanceQueryFacade` |
| DDD-PRODUCT-005 | Product | DONE | `SampleApplicationPort` |

## Batch 2 — Policy

| task_id | owner | 状态 | 说明 |
|---------|-------|------|------|
| DDD-PRODUCT-002 | Product | DONE | `ProductDisplayPolicy` |
| DDD-ORDER-001 | Order | DONE | `OrderSyncApplicationService` |
| DDD-ORDER-002 | Order | DONE | `876a447d` Router + `f3846415` SettlementGateway |
| DDD-ORDER-003 | Order | DONE | `f3846415` SettlementOrderGateway + OrderSyncService 重构 |
| DDD-SAMPLE-005 | Sample | DONE | `f3846415` SampleController query/command 拆分 |
| DDD-PERF-002 | Performance | DONE | `59d3a085` `PerformanceMoneyPolicy` |
| DDD-SAMPLE-006 | Sample | DONE | `98299d1e` `SampleStateMachine` |
| DDD-TALENT-002 | Talent | DONE | `d41c4d58` `TalentClaimPolicy` |

## Batch 3 — 跨域替换

| task_id | owner | 状态 | 说明 |
|---------|-------|------|------|
| DDD-TALENT-003 | Talent | WIP | TalentController → TalentQueryApplicationService → Facade |
| DDD-SAMPLE-007 | Sample | TODO | SampleController 切 Facade |
| DDD-PRODUCT-003 | Product | TODO | QuickSample 切 Facade |
| DDD-PERF-003 | Performance | TODO | QueryController 切 Facade |
| DDD-ORDER-003 | Order | TODO | Controller 切 Facade（勿碰 OrderSyncService） |

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
| P0 | DDD-SAMPLE-005-FIX | Sample | **DONE** (`eb191ac2`) |
| P1 | DDD-ORDER-002+003 | Order | **DONE** `f3846415` |
| P1 | DDD-PRODUCT-001 | Product | **DONE** `42843d09` |
| P1 | DDD-USER-003+004 | User | **DONE** `f3846415` |
| P2 | DDD-TALENT-001 | Talent | **DONE** `60b9d062` |
| P2 | DDD-PERF-002 | Performance | **DONE** `59d3a085` |
| P2 | DDD-TALENT-002 / SAMPLE-006 | Batch2 Policy | **DONE** `d41c4d58` / `98299d1e` |
| P2 | Batch3 Replace | Integration | **WIP** — 串行启动 |

## 下一步优先

- Batch3 调用替换（Talent → Sample → Product → Performance → Order）
- `agent-do -Scope backend` 补证据
- **Step 5**：BASE-002 Characterization 全绿

可并行：**User-003** + **Product-001** + **Talent-001**（无共享文件）  
不可并行：**Order-002** 与任何改 `OrderSyncService` 的任务

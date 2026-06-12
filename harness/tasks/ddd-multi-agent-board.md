# DDD Multi-Agent Board

> 完整 53 项任务定义见 `ddd-full-task-pool.md`，依赖图见 `ddd-task-dependency-graph.md`

更新时间：2026-06-12 15:59  
分支：`feature/ddd/SPRINT-1-P0`（基于 SAMPLE-005-FIX 拉新分支，专做 100% 计划 Sprint 1 P0 任务）  
HEAD：`f2aa5943`（SAMPLE-002 extract SampleEligibilityPolicy，agent-do PASS）

> 100% 完成度路线图：`harness/tasks/ddd-100-percent-completion-plan.md`  
> 当前进度：**35/53 = 66%**（SAMPLE-002 落地）

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
| DDD-BASE-002 | Test | DONE | — | Characterization Baseline Tests 全绿 |
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
| DDD-PERF-001 | Performance | DONE | `PerformanceQueryFacade` |
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
| DDD-TALENT-003 | Talent | DONE | `69e8d106` Controller → ApplicationService → Facade |
| DDD-SAMPLE-007 | Sample | DONE | `5cea498a` Controller → ApplicationService → Facade |
| DDD-PRODUCT-003 | Product | DONE | `19c7da8b` QuickSample 切 Facade |
| DDD-PERF-003 | Performance | DONE | `dd892ea0` QueryController 切 Facade |
| DDD-ORDER-003 | Order | DONE | Controller 切 Facade（勿碰 OrderSyncService） |
| P-FIX-002E | Product | DONE | `aca79f74` ProductDisplayRuleService + 4 dry-run SQL 报告 |

串行，由 Integration Agent 控制。见 `ddd-task-dependency-graph.md`。

## Batch 4 — 事件 / Analytics

| task_id | owner | 状态 |
|---------|-------|------|
| DDD-ANALYTICS-001 | Analytics | DONE |
| DDD-ANALYTICS-002 | Analytics | DONE（shadow compare） |
| DDD-OUTBOX-001 | Infra + 各领域 | DONE | OrderSyncedEvent Outbox 路由 |

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
| P2 | Batch3 Replace | Integration | **DONE** — 五域 Facade 替换已完成 |
| P2 | Batch4 Outbox | Infra | **DONE** — OUTBOX-001 OrderSynced 路由（`27d15ae6`） |
| P2 | P-FIX-002E | Product | **DONE** — 商品 PENDING repair（`aca79f74`） |

## Batch 5 — 事件 / Outbox 余项

| task_id | 状态 | 说明 |
|---------|------|------|
| DDD-SAMPLE-004 | DONE | `6682bf3a` `sample-homework-event` + Bridge/Listener |
| DDD-EVENT-003 | TODO | Outbox Dispatcher Dry Run |

## Batch 6 — 瘦身与清理 (SLIM & CLEAN)

| task_id | owner | 状态 | 说明 |
|---------|-------|------|------|
| DDD-SLIM-ORDER-001 | Order | DONE | `aca79f74` OrderSyncService 彻底瘦身金额映射并移除 Policy/Resolver 直接依赖 |
| DDD-SLIM-ORDER-002 | Order | DONE | `6c577ae8` `OrderAttributionRouter` + `OrderDefaultAttributionPolicy` 抽离（待 EVENT-003 分支 merge） |

## Sprint 1（100% 计划，P0 集中）

| task_id | owner | 状态 | commit | 报告 |
|---------|-------|------|--------|------|
| DDD-SAMPLE-002 | Sample | **DONE** | `f2aa5943` | `harness/reports/evidence-20260612-155838.md` |
| DDD-PRODUCT-004 | Product | TODO | — | CopyPromotion + DouyinConvertPort |
| DDD-PERF-003 | Performance | TODO | — | PerformanceAttributionPolicy |
| DDD-EVENT-003 | Infra | TODO | — | Dispatcher Dry Run（parallel agent 在做） |

> ORDER-004 = SLIM-ORDER-002（已落地）。

## 下一步优先

1. **P0** `DDD-PRODUCT-004` — CopyPromotion 重构（Sprint 1 第 2 项）
2. **P0** `DDD-PERF-003` — PerformanceAttributionPolicy（Sprint 1 第 3 项）
3. **P2** `DDD-EVENT-003` — Dispatcher Dry Run（parallel agent）
4. **P0** `DDD-VERIFY-001` — E2E P0 终验（最后一道）

可并行：**Sample-002** + **Product-004** + **PERF-003** + **EVENT-003**（无文件冲突）  
不可并行：**Order 域** 内所有改 `OrderSyncService` 的任务串行  
强约束：**CLEAN-001~004** 必须在所有 SLIM-* 和 Phase 3-9 收尾后执行；**VERIFY-001** 最后

## 真实环境（real-pre）状态

- backend-real-pre：**healthy**（agent-do 重启后恢复）
- frontend / postgres / redis：healthy
- test 环境：healthy

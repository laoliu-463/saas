# DDD Multi-Agent Board

> 完整 53 项任务定义见 `ddd-full-task-pool.md`，依赖图见 `ddd-task-dependency-graph.md`。

更新时间：2026-06-12 16:12
分支：`feature/ddd/DDD-SAMPLE-002-eligibility-policy`
HEAD：`1b30259e`（DDD-SAMPLE-002 已提交并推送）

> 100% 完成度路线图：`harness/tasks/ddd-100-percent-completion-plan.md`
> 当前进度：**36/53 = 68%**（DDD-SAMPLE-002 已落地；下一步 DDD-PRODUCT-004）

## 图例

| 状态 | 含义 |
| --- | --- |
| DONE | 已 commit + report |
| PARTIAL | 已 commit 但测试或边界未全绿 |
| WIP | 工作区未提交 |
| TODO | 未开始 |
| BLOCKED | 被依赖或冲突阻塞 |

## Batch 0 - 防护基线

| task_id | owner | 状态 | commit | 报告 |
| --- | --- | --- | --- | --- |
| DDD-BASE-001 | Infra | DONE | `8f1244a6` | `harness/reports/ddd-base-001-refactor-switches.md` |
| DDD-BASE-003 | Architecture Guard | DONE | `86b16922` | `harness/reports/ddd-dependency-map.md` |
| DDD-BASE-004 | Infra | DONE | `1ab9cd92` | `harness/reports/ddd-base-004-package-structure.md` |
| DDD-BASE-002 | Test | DONE | - | Characterization Baseline Tests 全绿 |

## Batch 1 - Facade

| task_id | owner | 状态 | 说明 |
| --- | --- | --- | --- |
| DDD-USER-001 | User | DONE | `UserDomainFacade` |
| DDD-USER-002 | User | DONE | 订单域读用户 |
| DDD-USER-003 | User | DONE | `834ca16e` 跨域 SysUserMapper -> UserDomainFacade |
| DDD-USER-004 | User | DONE | `f3846415` DataScope 迁移至 domain/user |
| DDD-CONFIG-001~004 | Config | DONE | Facade + Event |
| DDD-PRODUCT-001 | Product | DONE | `42843d09` |
| DDD-TALENT-001 | Talent | DONE | `60b9d062` |
| DDD-PERF-001 | Performance | DONE | `PerformanceQueryFacade` |
| DDD-PRODUCT-005 | Product | DONE | `SampleApplicationPort` |

## Batch 2 - Policy

| task_id | owner | 状态 | 说明 |
| --- | --- | --- | --- |
| DDD-PRODUCT-002 | Product | DONE | `ProductDisplayPolicy` |
| DDD-ORDER-001 | Order | DONE | `OrderSyncApplicationService` |
| DDD-ORDER-002 | Order | DONE | `876a447d` Router + `f3846415` SettlementGateway |
| DDD-ORDER-003 | Order | DONE | `f3846415` SettlementOrderGateway + OrderSyncService 重构 |
| DDD-SAMPLE-005 | Sample | DONE | `f3846415` SampleController query/command 拆分 |
| DDD-PERF-002 | Performance | DONE | `59d3a085` `PerformanceMoneyPolicy` |
| DDD-SAMPLE-006 | Sample | DONE | `98299d1e` `SampleStateMachine` |
| DDD-TALENT-002 | Talent | DONE | `d41c4d58` `TalentClaimPolicy` |
| DDD-SAMPLE-002 | Sample | DONE | `1b30259e` `SampleEligibilityPolicy` |

## Batch 3 - 跨域替换

| task_id | owner | 状态 | 说明 |
| --- | --- | --- | --- |
| DDD-TALENT-003 | Talent | DONE | `69e8d106` Controller -> ApplicationService -> Facade |
| DDD-SAMPLE-007 | Sample | DONE | `5cea498a` Controller -> ApplicationService -> Facade |
| DDD-PRODUCT-003 | Product | DONE | `19c7da8b` QuickSample 切 Facade |
| DDD-PERF-003 | Performance | DONE | `dd892ea0` QueryController 切 Facade |
| DDD-ORDER-003 | Order | DONE | Controller 切 Facade（勿碰 OrderSyncService） |
| P-FIX-002E | Product | DONE | `aca79f74` ProductDisplayRuleService + dry-run SQL 报告 |

## Batch 4 - 事件 / Analytics

| task_id | owner | 状态 | 说明 |
| --- | --- | --- | --- |
| DDD-ANALYTICS-001 | Analytics | DONE | - |
| DDD-ANALYTICS-002 | Analytics | DONE | shadow compare |
| DDD-OUTBOX-001 | Infra + 各领域 | DONE | OrderSyncedEvent Outbox 路由 |

## Batch 5 - 事件 / Outbox 余项

| task_id | 状态 | 说明 |
| --- | --- | --- |
| DDD-SAMPLE-004 | DONE | `6682bf3a` `sample-homework-event` + Bridge/Listener |
| DDD-EVENT-003 | DONE | `fcaf664a` `app.domain-event.dispatch-dry-run` + Job 单测；agent-do 构建、健康检查、real-pre preflight PASS |

## Batch 6 - 瘦身与清理

| task_id | owner | 状态 | 说明 |
| --- | --- | --- | --- |
| DDD-SLIM-ORDER-001 | Order | DONE | `8c912953` `mapAndApplyToOrder` 收口 mapOrder 金额映射 |
| DDD-SLIM-ORDER-002 | Order | DONE | `6c577ae8` + `fcaf664a` 修复 `AttributionService` -> `OrderAttributionRouter` 循环依赖 |

## 下一步优先

1. **P0** `DDD-PRODUCT-004` - CopyPromotion + DouyinConvertPort。
2. **P0** `DDD-PERF-003` - PerformanceAttributionPolicy。
3. **P0** `DDD-VERIFY-001` - E2E P0 终验。

## real-pre 状态

- backend-real-pre：healthy（`agent-do` 16:01 PASS，health 200 UP）
- frontend / postgres / redis：healthy
- test 环境：healthy

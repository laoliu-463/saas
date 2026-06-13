# DDD Multi-Agent Board

> 完整 53 项任务定义见 `ddd-full-task-pool.md`，依赖图见 `ddd-task-dependency-graph.md`。

更新时间：2026-06-13 15:33
分支：`feature/ddd/DDD-SLIM-PERF-001`（owner 集成分支，已合 Sprint1 P0 + 第二批）
HEAD：`c419c350`（DDD-SLIM-PERF-001）

> 100% 完成度路线图：`harness/tasks/ddd-100-percent-completion-plan.md`
> 当前进度：**strict DONE 42/53 = 79%**（含 PARTIAL 44/53 = 83%）

## 图例

| 状态 | 含义 |
| --- | --- |
| DONE | 已 commit + report |
| PARTIAL | 已 commit 但 spec 仍有缺口（如 facade 误标 / policy 未提） |
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
| DDD-PERF-001 | Performance | **PARTIAL** | board 标 DONE 但缺 `PerformanceCalculationApplicationService`（仅 facade 已实现，task-pool 期望 calculation 层） |
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
| DDD-TALENT-003 | Talent | DONE | `39613fd5` TalentTagPolicy + TalentAddressPolicy（task-pool 期望 Tag/Address，已 commit） |
| DDD-SAMPLE-007 | Sample | DONE | `5cea498a` Controller -> ApplicationService -> Facade |
| DDD-PRODUCT-003 | Product | DONE | `2f0c1077` ProductPinPolicy（board 旧标 DONE 但缺 ProductPinPolicy 类，已补齐） |
| DDD-PERF-003 | Performance | DONE | `dd892ea0` + `89d5e7f1` QueryController 切 Facade + PerformanceAttributionPolicy 落地 |
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
| DDD-SLIM-PERF-001 | Performance | DONE | `c419c350` delegate CommissionService money formula to PerformanceMoneyPolicy |

## Batch 7 - Sprint 1 P0（owner 集成分支）

| task_id | owner | 状态 | commit | 报告 / handover |
| --- | --- | --- | --- | --- |
| DDD-PROGRESS-AUDIT | Integration | DONE | `712df1ed` + `626d779e` | `harness/reports/ddd-progress-audit-2026-06-13.md` + `harness/handovers/DDD-PROGRESS-AUDIT-handover.md` |
| DDD-ORDER-004 | Order | DONE | `95020743` + `24a9e0bf` + `86286cf7` | OrderDefaultAttributionPolicy + Router + Resolver |
| DDD-PERF-004 | Performance | DONE | `a9522ac8` + `3819e93c` + `ab536385` + `e5db1041` | OrderPerformanceQueryFacade + Data BFF 接线 + whitelist 收紧 |
| DDD-PRODUCT-004 | Product | DONE | `fce4b2fb` + `f1391eee` | CopyPromotionApplicationService + DouyinConvertPort |
| DDD-VERIFY-SPRINT1-P0 | Integration | DONE | `34c8ae57` | `harness/reports/ddd-sprint1-p0-stage-acceptance-2026-06-13.md` (PARTIAL_PASS 35/53) |
| DDD-PERF-005 | Performance | DONE | `70b2e287` + `46675e9d` | `harness/reports/ddd-perf-005-2026-06-13.md` ExclusiveMerchant service + PerformanceAttributionPolicy |
| DDD-TALENT-004 | Talent | DONE | `d9a33028` + `b399701f` + `679223a6` | `harness/reports/ddd-talent-004-2026-06-13.md` ExclusiveTalent service |

## Batch 8 - 订单域 query/sync 解耦（owner 集成分支）

| task_id | owner | 状态 | commit | 报告 / handover |
| --- | --- | --- | --- | --- |
| DDD-ORDER-005 | Order | DONE | `8b49dd12` + `dbdfdbb2` | `harness/tasks/DDD-ORDER-005-handover.md` OrderDomainEventPublisher + InProcessOrderDomainEventPublisher + OrderEventPayloadMapper + OrderSyncedEvent payload 补齐 |
| DDD-ORDER-006 | Order | DONE | `eb3be708` + `8cd3a8c0` + `0f6dd1d4` | `harness/tasks/DDD-ORDER-006-handover.md` OrderQueryView + OrderDetailView，Controller/Facade 不再暴露 Entity |

## 仍未完成

### PARTIAL（spec 缺口）

| task_id | 缺口 |
| --- | --- |
| DDD-PERF-001 | 缺 `PerformanceCalculationApplicationService`（task-pool 期望 calculation 层，仅 facade 已做） |
| DDD-SAMPLE-001 | 缺 `domain/sample/application/SampleApplicationService`（已有 `SampleCommandApplicationService` + `SampleQueryApplicationService` + `SampleApplicationPortImpl`，可能等价实现但路径未对齐） |

### TODO（下一批执行）

| task_id | 优先级 | 说明 |
| --- | --- | --- |
| DDD-SLIM-PRODUCT-001 | P1 | `ProductService` 仍含非委派的展示规则 |
| DDD-SLIM-SAMPLE-001 | P1 | SampleService 申请校验瘦身（如果 god service 仍存在） |
| DDD-CLEAN-001 | P1 | 订单域移除 SysUserMapper 直接注入（除 USER-002 已做部分） |
| DDD-CLEAN-002 | P1 | 寄样域移除商品/达人/用户/配置跨域 Mapper |
| DDD-CLEAN-003 | P1 | 业绩域移除订单/商品/达人/配置/用户跨域 Mapper |
| DDD-CLEAN-004 | P1 | 商品域移除寄样域直接依赖 |
| DDD-FRONT-001 | P1 | 订单明细字段来源标注 + 前端回归 |
| DDD-VERIFY-001 | P0 | DDD 重构阶段性全链路验收（CLEAN 完才进入） |

> 注：进入 CLEAN 阶段必须等 DDD-TALENT-004 / DDD-PERF-005 / DDD-ORDER-006 全绿且 backend 全量测试 0 fail。

## 下一步优先（按推荐顺序）

1. **P0** `DDD-VERIFY-001` - 全链路验证 owner 集成分支 DDD-SLIM-PERF-001 是否整体绿（先跑 mvn 测试，验证 41 DONE + 2 PARTIAL 不引入回归）。
2. **P1** `DDD-SLIM-PRODUCT-001` - ProductService 瘦身。
3. **P1** `DDD-SLIM-SAMPLE-001` - SampleService 瘦身（如果 god service 仍存在）。
4. **P1** `DDD-CLEAN-001~004` - 跨域 mapper 清理（按 phase 11 顺序串行）。
5. **P1** `DDD-FRONT-001` - 前端字段标注。
6. **P1** 修正 `DDD-PERF-001` 与 `DDD-SAMPLE-001` PARTIAL 缺口。

## real-pre 状态

- backend-real-pre：healthy（DDD-SPRINT1-P0 stage acceptance 13:13-13:17 PASS；68 测试 0 fail）
- frontend / postgres / redis：healthy
- test 环境：healthy（131 baseline fail/error 需后续清理）

## 关联 handover

- `harness/tasks/DDD-SPRINT1-P0-handover.md`（Sprint 1 P0 第一批 5 个任务 stage 验收）
- `harness/tasks/DDD-ORDER-005-handover.md`（订单域事件发布收敛）
- `harness/tasks/DDD-ORDER-006-handover.md`（订单 query/sync 模型解耦）
- `harness/tasks/ddd-sample-005-fix-sample-agent-handover.md`（历史，Sample 重写）
- `harness/handovers/DDD-PROGRESS-AUDIT-handover.md`（PROGRESS-AUDIT 推荐 Sprint1 P0 + 不进 CLEAN）

## 重要 milestone

- **2026-06-12 18:55** - 老 board 36/53 = 68% 截图（DDD-SAMPLE-002 已落地）
- **2026-06-13 13:13** - Antigravity Agent stage acceptance：68 测试 0 fail
- **2026-06-13 13:23** - DDD-TALENT-004 收尾（d9a33028）
- **2026-06-13 13:26** - DDD-PERF-005 收尾（70b2e287 + 46675e9d）
- **2026-06-13 14:25** - DDD-ORDER-006 收尾（0f6dd1d4）
- **2026-06-13 15:33** - 本次 board 同步（strict 42/53 = 79%, 含 PARTIAL 44/53 = 83%）
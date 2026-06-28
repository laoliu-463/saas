# DDD Multi-Agent Board

> 完整 53 项任务定义见 `ddd-full-task-pool.md`，依赖图见 `ddd-task-dependency-graph.md`。

更新时间：2026-06-28 16:40
分支：`feature/ddd/DDD-CLEAN-004`（CLEAN-003/004 full harness PASS + PERF-001 计算层落地）
HEAD：`16721547`（DDD-CLEAN full verification）→ working tree 含 PERF-001 收口 + FRONT-001 详情弹窗

> 口径更新（2026-06-28）：本 53 项 board 仅保留为历史批次 / commit 索引，不再作为整体 DDD 完成度口径。
> 当前唯一进度口径：`DDD_DOMAIN_TASK_MATRIX*.md` 178 项矩阵 + `DOMAIN_STATUS.md` + evidence reports。
> 当前整体状态：**PARTIAL**。`strict DONE 53/53 = 100%` 已废弃，不能作为 DDD 完成证明。

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
| DDD-PERF-001 | Performance | DONE | working tree 新增 `PerformanceCalculationApplicationService`（27 行门面） + `DddPerf001CalculationApplicationServiceRoutingTest` 护栏 + `PerformanceCalculationApplicationServiceTest` 单测；listener / backfill / month-recalc 三处调用方全部切换；DDD guard 75 / 0 / 1 SKIP，targeted 11 / 0 |
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
| DDD-SLIM-PRODUCT-001 | Product | DONE | `6452425f` + `c21387b2` slim ProductService display rules to ProductDisplayPolicy（`DddSlimProduct001DisplayPolicyRoutingTest` 落地） |
| DDD-SLIM-SAMPLE-001 | Sample | DONE | `c436f1f0` + `f90ea9d1` slim SampleService eligibility checks（delegated to SampleEligibilityPolicy failure rules） |
| DDD-CLEAN-001 | Order + User | DONE | `5d90d355` order code no longer injects SysUserMapper/SysUserService; guard `DddClean001OrderUserDependencyGuardTest` added |
| DDD-CLEAN-002 | Sample | DONE | `879b6b4b` 寄样域移除 Product/Talent 跨域 Mapper；targeted 124 run / 0 fail / 1 skipped；full harness PASS |
| DDD-CLEAN-003 | Performance | DONE | `34e2f105` 业绩域移除订单跨域 Mapper，改走 `OrderReadFacade`；targeted 42 run / 0 fail / 1 skipped；full harness PASS `2620585f` |
| DDD-CLEAN-004 | Product + Sample | DONE | `a437d524` 商品域快速寄样改走 `ProductSampleApplicationPort`；guard + QuickSample tests PASS；full harness PASS `2620585f` |

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
| DDD-SAMPLE-001 | Sample | DONE | `7efee009` | `harness/reports/ddd-sample-001-2026-06-13.md` 统一 SampleApplicationService（合并 Query/Command 两个 service） |

## Batch 8 - 订单域 query/sync 解耦（owner 集成分支）

| task_id | owner | 状态 | commit | 报告 / handover |
| --- | --- | --- | --- | --- |
| DDD-ORDER-005 | Order | DONE | `8b49dd12` + `dbdfdbb2` | `harness/tasks/DDD-ORDER-005-handover.md` OrderDomainEventPublisher + InProcessOrderDomainEventPublisher + OrderEventPayloadMapper + OrderSyncedEvent payload 补齐 |
| DDD-ORDER-006 | Order | DONE | `eb3be708` + `8cd3a8c0` + `0f6dd1d4` | `harness/tasks/DDD-ORDER-006-handover.md` OrderQueryView + OrderDetailView，Controller/Facade 不再暴露 Entity |

## 仍未完成

### PARTIAL（spec 缺口）

| task_id | 缺口 |
| --- | --- |
| DDD-FRONT-001 | 订单详情字段来源标注 unit/build/full harness PASS；浏览器/E2E 详情页复核进行中：working tree 已加 `runtime/qa/order-detail-field-source-visible.cjs` Playwright QA 脚本 + 操作列「详情」按钮 + 单测 33/33 PASS |
| DDD-VERIFY-001 | 阶段性 PARTIAL_PASS；等待 FRONT 详情页证据、final P0 验收和 Maven baseline 策略 |

### TODO（下一批执行）

| task_id | 优先级 | 说明 |
| --- | --- | --- |
| - | - | CLEAN 阶段当前无 TODO |

> 注：进入 CLEAN 阶段必须等 DDD-TALENT-004 / DDD-PERF-005 / DDD-ORDER-006 全绿且 backend 全量测试 0 fail。

## 下一步优先（按当前口径）

1. **P0** 当前红灯：收口 `TalentProfileApplicationServiceTest.updateTagsNormalizesAndPersistsTags` 与对应达人域 Slice。
2. **P0** `DDD-GIT-INTAKE-001` - 分类当前 dirty / evidence / report 状态，未分类前不得写整体 DONE。
3. **P0** 按 `DOMAIN_STATUS.md` 推进用户域 `DataScopeResolver` / `PermissionChecker` / `UserDomainFacade` 收口。
4. **P0** 补 `DDD-VERIFY-001` final P0 验收、Maven baseline 和 real-pre 阻塞 / 通过证据。

## real-pre 状态

- backend-real-pre：healthy（DDD-SPRINT1-P0 stage acceptance 13:13-13:17 PASS；68 测试 0 fail）
- frontend / postgres / redis：healthy
- test 环境：healthy（131 baseline fail/error 需后续清理）

## DDD-VERIFY-001 准备（2026-06-13 18:37）

- `mvn -f backend/pom.xml '-Dtest=Ddd*Test' '-Dspring.profiles.active=test' test`
- 70 测试：**68 PASS / 2 FAIL / 1 SKIP**（13.9s，CLEAN-002 前置 RED）
- CLEAN-002 本轮已补护栏并修复：targeted 124 run / 0 fail / 1 skipped；full harness PASS
- 21 套件全跑：CLEAN-002 前所有 DONE 任务对应的护栏测试全 PASS
- 1 SKIP 来自 `DddCrossDomainMapperGuardTest` 的 legacy whitelist 基线
- 详细 evidence：`harness/reports/ddd-clean-002-2026-06-13.md`
- 结论：DDD-VERIFY-001 final 可在 DDD-CLEAN-004 + DDD-FRONT-001 E2E 完成后启动
- Stash 清理：drop 3 个过时 stash（TALENT-004 WIP / SLIM-SAMPLE-001 WIP / main-pre-merge-modified）

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
- **2026-06-13 15:33** - 第一次 board 同步（strict 42/53 = 79%, 含 PARTIAL 44/53 = 83%）
- **2026-06-13 15:40** - 修复 board commit UTF-8 编码并 amend（e3bbaf5b）
- **2026-06-13 15:47** - DDD-SAMPLE-001 收尾（7efee009 统一 SampleApplicationService）
- **2026-06-13 16:09** - DDD-SLIM-PRODUCT-001 收尾（6452425f ProductService 瘦身）
- **2026-06-13 16:51** - DDD-SLIM-SAMPLE-001 收尾（f90ea9d1 SampleService 瘦身）
- **2026-06-13 16:58** - 第二次 board 同步（strict 45/53 = 85%, 含 PARTIAL 46/53 = 87%）
- **2026-06-13 17:05** - DDD-CLEAN-001 收尾（订单域 SysUserMapper/SysUserService 直接依赖护栏）
- **2026-06-13 18:37** - VERIFY-001 准备（DDD 架构护栏 70 测：68 PASS / 2 FAIL / 1 SKIP；2 FAIL 是 DDD-CLEAN-002 预期 violation；3 过时 stash 清理）
- **2026-06-13 19:44** - 重跑 DDD 架构护栏测试（owner CLEAN-002 working tree 修复后）：**70 测 / 0 FAIL / 1 SKIP / BUILD SUCCESS**，20.9s。CLEAN-002 actual DONE。
- **2026-06-13 20:01** - DDD-CLEAN-002 full harness 收口：backend/frontend build PASS，real-pre backend/frontend restart + health PASS，`e2e:real-pre:p0:preflight` PASS，提交/推送 `879b6b4b`。
- **2026-06-13 20:11** - DDD-CLEAN-003 backend harness 收口：业绩域改走 `OrderReadFacade`，targeted 26 run / 0 fail / 1 skipped，backend build/restart/health/preflight PASS，提交/推送 `34e2f105`。
- **2026-06-13 20:24** - DDD-CLEAN-003/004 full harness 收口：targeted 42 run / 0 fail / 1 skipped，DDD guard 73 run / 0 fail / 1 skipped，backend/frontend build PASS，real-pre backend/frontend restart + health PASS，`e2e:real-pre:p0:preflight` PASS，提交/推送 `2620585f`。
- **2026-06-13 21:49** - owner close DDD-CLEAN full verification (`16721547`)：working tree 含 PERF-001 calculation 层 + FRONT-001 详情按钮 + E2E QA 脚本。
- **2026-06-14 08:37** - 重跑 DDD 架构护栏：**75 测 / 0 FAIL / 1 SKIP / BUILD SUCCESS**,23.4s。新增 5 测：DddClean003/004 + DddPerf001 routing。
- **2026-06-14 08:40** - PERF-001 calculation 层单测：targeted 11 run / 0 fail / 0 skipped / BUILD SUCCESS；前端 orders/index.test.ts 33/33 PASS。strict DONE 53/53 = 100%。

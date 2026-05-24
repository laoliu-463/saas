# 测试覆盖率基线

**日期**：2026-05-24
**分支**：`feature/auth-system`
**口径**：合并并推送 `2b28bef` 后的本地覆盖率报告

## 验证命令

```powershell
$env:DB_HOST='localhost'; $env:REDIS_HOST='localhost'; cd backend; mvn test
npm --prefix frontend run test:coverage
```

## 后端 JaCoCo 总览

后端全量单测结果：`1410 tests, 0 failures, 0 errors`

| 指标 | 覆盖 | 总量 | 覆盖率 |
|---|---:|---:|---:|
| Instruction | 104863 | 175714 | 59.68% |
| Branch | 7206 | 23499 | 30.67% |
| Line | 20504 | 24892 | 82.37% |
| Method | 5846 | 7472 | 78.24% |
| Complexity | 8105 | 19334 | 41.92% |

结论：后端行覆盖率已达到交付前验收基础线，但分支覆盖率和复杂度覆盖率偏低，后续补测应优先覆盖复杂判断路径、异常路径、权限分支和状态机分支。

## 前端 Vitest 覆盖率

前端测试结果：`45 test files, 190 tests passed`

| 指标 | 覆盖 | 总量 | 覆盖率 |
|---|---:|---:|---:|
| Statements | 716 | 716 | 100.00% |
| Branches | 233 | 233 | 100.00% |
| Functions | 58 | 58 | 100.00% |
| Lines | 716 | 716 | 100.00% |

说明：当前前端 coverage 配置只纳入 7 个文件，主要是 `constants` 与 `router`，不能代表整个 `frontend/src` 的全量覆盖率。

## 核心领域覆盖率聚合

| 领域 | 类数量 | 分支覆盖率 | 分支 | 行覆盖率 | 行 |
|---|---:|---:|---:|---:|---:|
| Dashboard | 12 | 10.59% | 47/444 | 86.09% | 291/338 |
| Performance | 34 | 37.22% | 294/790 | 62.32% | 837/1343 |
| Sample | 48 | 43.80% | 717/1637 | 78.41% | 2041/2603 |
| Gateway | 125 | 51.12% | 1528/2989 | 85.39% | 4157/4868 |
| Permission/DataScope | 14 | 51.93% | 215/414 | 69.90% | 562/804 |
| Product | 75 | 57.05% | 1582/2773 | 78.65% | 3839/4881 |
| Order/Attribution | 52 | 60.46% | 997/1649 | 87.20% | 2324/2665 |

优先级判断：

1. Dashboard 分支覆盖最低，应补事件重复消费、订单冲正、预估/结算口径和权限过滤。
2. Performance 行覆盖与分支覆盖都偏低，应补结算状态、退款、金额为 0、提成比例缺失、回填任务。
3. Sample 已有较多覆盖，但仍应补 7 天限制、豁免、拒绝不限制、物流自动完成和状态机异常分支。
4. Permission/DataScope 聚合分支已过 50%，但行覆盖偏低，应补空组、跨组、禁用用户和缓存失效分支。

## 初始低分支覆盖核心类候选

以下为本日基线时排除 DTO/VO/entity/config 后的优先关注候选；补测后的变化见下方进展记录：

| 类 | 分支覆盖 | 行覆盖 | 建议 |
|---|---:|---:|---|
| `DomainEventOutboxService` | 0/16 | 4/48 | 事件写入、幂等、失败重试、重复消费 |
| `Kuaidi100LogisticsQueryGateway` | 0/35 | 5/59 | 空响应、签名失败、状态映射、超时 |
| `ExclusiveMerchantQueryService` | 0/20 | 5/54 | self/group/all 与空数据 |
| `PerformanceMonthRecalculationService` | 0/10 | 5/41 | 月度重算边界和异常 |
| `ProductDisplayAuditService` | 0/6 | 4/29 | 上架/隐藏审计分支 |
| `UserPermissionCacheService` | 0/8 | 3/20 | 缓存命中、失效、空角色 |
| `SampleLogisticsSyncJob` | 0/6 | 8/21 | 开关、并发、失败重试 |
| `KuaidiNiaoLogisticsQueryGateway` | 2/35 | 11/59 | 失败码、轨迹缺失、状态映射 |
| `OrgStructureService` | 13/82 | 29/135 | 组织字段、角色、部门删除约束 |
| `RuleCenterService` | 6/36 | 21/126 | 配置校验、批量更新、事件发布 |
| `PerformanceAccessScope` | 45/140 | 50/112 | 权限范围和数据范围组合 |
| `PerformanceMetricsQueryService` | 20/60 | 77/128 | 指标口径、空聚合、异常金额 |
| `PerformanceSummaryService` | 14/37 | 51/87 | 汇总口径、空数据、边界时间 |
| `SampleLogisticsSyncService` | 20/46 | 72/118 | 自动完成、签收、失败回滚 |

## 下一步验收口径

不建议盲目追求全项目 90% 行覆盖率。下一轮目标应为：

- 后端全项目行覆盖率保持 `>= 80%`
- 核心 Service 分支覆盖率提升到 `>= 50%`
- Dashboard / Performance / Sample 三类优先补业务分支
- 补测后重新跑 `mvn test` 和 JaCoCo
- 再跑 Docker 集成、前端 build、Playwright E2E、real-pre 预检和业务验收脚本

## 2026-05-24 补测进展

本轮补测聚焦 Dashboard、Performance、事件 Outbox、权限缓存、寄样物流同步、物流查询网关、商品展示审计、独家商家查询、团长联系人维护与推广文案渲染核心分支；最新后端全量测试计数从基线 `1410` 增至 `1502`：

- `DashboardServiceTest`：新增 4 个用例，覆盖无订单归因率、分页归一化、个人/部门数据范围、诊断分类兼容。
- `PerformanceSummaryServiceTest`：新增 5 个用例，覆盖空查询默认值、异常数值容错、pay/settle 时间口径、订单状态映射、渠道人员越权。
- `PerformanceMonthRecalculationServiceTest`：新增 4 个用例，覆盖月份/原因校验、月份 trim、已结算跳过、单条重算失败不中断。
- `DomainEventOutboxServiceTest`：新增 11 个用例，覆盖配置变更事件入库、空 items、序列化失败、发布成功、失败重试、死信、死信重试、锁定待发布事件、分页查询和按 ID 查询。
- `PerformanceAccessScopeTest`：净增 12 个用例，覆盖导出权限、月度重算权限、员工越权过滤、admin-like、组长、个人/部门范围和 SQL 条件拼接。
- `UserPermissionCacheServiceTest`：新增 7 个用例，覆盖用户/角色/组别变更缓存失效、空 ID 和全角色权限缓存失效。
- `SampleLogisticsSyncServiceTest`：净增 9 个用例，覆盖寄样单缺失、空物流单号、按物流单查询缺参/未命中、结果空值防御、轨迹替换、签收状态保护和批量成功/失败/跳过/异常统计。
- `SampleLogisticsSyncJobTest`：新增 5 个用例，覆盖同步开关、测试模式跳过、锁未获取跳过、正常批量同步和服务异常时释放锁。
- `Kuaidi100LogisticsQueryGatewayTest`：新增 7 个用例，覆盖配置缺失、空单号、输入 trim、成功轨迹映射、上游失败/空响应、上游异常和内部状态映射。
- `ProductDisplayAuditServiceTest`：新增 5 个用例，覆盖审计日志落库、JSON 序列化失败兜底、detail 为空、分页归一化和空商品过滤。
- `ExclusiveMerchantQueryServiceTest`：新增 5 个用例，覆盖空 partner、未命中、命中后招聘人名称、空 recruiter 和异常月份容错。
- `KuaidiNiaoLogisticsQueryGatewayTest`：新增 7 个用例，覆盖快递鸟配置缺失、空单号、输入 trim、成功轨迹映射、上游失败/空响应、上游异常和内部状态映射。
- `ColonelPartnerAdminServiceTest`：新增 4 个用例，覆盖联系人字段 trim、空字段置 null、未命中和乐观锁更新失败。
- `PromotionCopyBriefServiceTest`：新增 5 个用例，覆盖占位符替换、空值安全渲染、空 map 返回模板、忽略未知 key 和缺失 key 兜底。

补测后后端全量单测结果：`1502 tests, 0 failures, 0 errors`

| 指标 | 当前覆盖率 | 原基线 | 变化 |
|---|---:|---:|---:|
| Instruction | 61.45% | 59.68% | +1.77pp |
| Branch | 32.39% | 30.67% | +1.72pp |
| Line | 84.46% | 82.37% | +2.09pp |
| Method | 80.11% | 78.24% | +1.87pp |
| Complexity | 43.53% | 41.92% | +1.61pp |

关键类当前覆盖：

| 类 | 分支覆盖 | 行覆盖 | 说明 |
|---|---:|---:|---|
| `PerformanceMonthRecalculationService` | 10/10 | 41/41 | 月度重算服务已补齐分支与行覆盖 |
| `PerformanceSummaryService` | 29/37 | 81/87 | 仍剩少量权限范围组合和状态映射边界 |
| `DashboardService` | 42/64 | 219/247 | 汇总口径与数据范围核心分支已覆盖一轮 |
| `DomainEventOutboxService` | 14/16 | 46/48 | 事件发布、失败、分页与锁定主分支已覆盖 |
| `PerformanceAccessScope` | 124/140 | 110/112 | 权限范围主分支已覆盖，剩余为少量防御性分支 |
| `UserPermissionCacheService` | 8/8 | 20/20 | 权限缓存失效服务已补齐分支与行覆盖 |
| `SampleLogisticsSyncJob` | 6/6 | 21/21 | 定时同步任务开关、锁和异常释放分支已补齐 |
| `SampleLogisticsSyncService` | 36/46 | 116/118 | 物流查询、轨迹替换、批量统计和签收推进主分支已覆盖 |
| `Kuaidi100LogisticsQueryGateway` | 33/35 | 59/59 | 快递100查询适配器主分支与状态映射已基本覆盖 |
| `ProductDisplayAuditService` | 6/6 | 29/29 | 商品展示审计服务已补齐分支与行覆盖 |
| `ExclusiveMerchantQueryService` | 18/20 | 53/54 | 独家商家查询主分支已覆盖，剩余为少量防御性分支 |
| `KuaidiNiaoLogisticsQueryGateway` | 33/35 | 59/59 | 快递鸟查询适配器主分支与状态映射已基本覆盖 |
| `ColonelPartnerAdminService` | 13/14 | 21/21 | 团长联系人维护主分支已覆盖 |
| `PromotionCopyBriefService` | 6/6 | 17/17 | 推广文案渲染服务已补齐分支与行覆盖 |

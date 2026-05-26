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

本轮补测聚焦 Dashboard、Performance、事件 Outbox、权限缓存、寄样物流同步、物流查询网关、物流健康诊断、商品展示审计、独家商家查询、团长联系人维护、推广文案渲染与规则中心核心分支；最新后端全量测试计数从基线 `1410` 增至 `1515`：

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
- `LogisticsGatewayHealthServiceTest`：新增 5 个用例，覆盖 provider 归一化、未知/未启用 provider、mock provider、未配置真实 provider、test 环境阻断、sandbox/real 成功状态缓存、失败与 NOT_CONFIGURED 映射、物流单号脱敏边界。
- `RuleCenterServiceTest`：新增 8 个用例，覆盖空/未知配置校验、当前/分组配置取值、分组保存过滤与 warning 合并、未知分组/空过滤拒绝、批量更新空过滤拒绝、变更日志转换、事件消费状态聚合和事件不存在。

补测后后端全量单测结果：`1515 tests, 0 failures, 0 errors`

| 指标 | 当前覆盖率 | 原基线 | 变化 |
|---|---:|---:|---:|
| Instruction | 61.94% | 59.68% | +2.26pp |
| Branch | 32.65% | 30.67% | +1.98pp |
| Line | 85.20% | 82.37% | +2.83pp |
| Method | 80.80% | 78.24% | +2.56pp |
| Complexity | 43.96% | 41.92% | +2.04pp |

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
| `LogisticsGatewayHealthService` | 59/68 | 132/136 | 物流健康诊断、测试查询和状态缓存主分支已覆盖 |
| `RuleCenterService` | 28/36 | 109/126 | 规则校验、保存过滤、日志与事件状态主分支已覆盖 |

## 2026-05-25 real-pre-only 覆盖率口径对齐

本轮按“只对齐 real-pre 容器环境”执行，没有启动 TEST/mock PostgreSQL / Redis，因此未重跑后端全量 `mvn test`。后端全量覆盖率仍以上方 2026-05-24 基线为准；本节只记录快递100订阅推送与寄样物流链路的局部验证证据，不能作为全项目覆盖率。

后端定向回归命令：

```powershell
cd backend
mvn clean "-Dtest=Kuaidi100LogisticsGatewayTest,Kuaidi100LogisticsQueryGatewayTest,Kuaidi100LogisticsCallbackControllerTest,Kuaidi100LogisticsCallbackServiceTest,SampleLogisticsSubscriptionServiceTest,SampleLogisticsSyncServiceTest,SampleLogisticsImportServiceTest,SampleControllerTest,RuntimeExposurePolicyTest,SecurityConfigTest,LogisticsGatewayHealthServiceTest" test
```

结果：`167 tests, 0 failures, 0 errors, 0 skipped`。首次非 clean 重跑曾出现 JaCoCo class/execution data 不匹配警告，已通过 `mvn clean` 消除。

本次局部 JaCoCo 报告只覆盖上述 11 个测试类触达的代码路径，整体数值偏低是预期现象：

| 指标 | 覆盖 | 总量 | 覆盖率 |
|---|---:|---:|---:|
| Instruction | 13246 | 181269 | 7.31% |
| Branch | 976 | 24167 | 4.04% |
| Line | 2671 | 25638 | 10.42% |
| Method | 1011 | 7702 | 13.13% |
| Complexity | 1416 | 19905 | 7.11% |

本轮关注包的局部覆盖：

| 包 | 行覆盖 | 分支覆盖 | 说明 |
|---|---:|---:|---|
| `com/colonel/saas/gateway/logistics/kuaidi100` | 202/219 (92.24%) | 95/229 (41.48%) | 覆盖实时查询、订阅请求、签名、异常响应与传输失败 |
| `com/colonel/saas/gateway/logistics/query` | 167/297 (56.23%) | 54/271 (19.93%) | 覆盖快递100查询网关、兜底查询与路由相关分支 |
| `com/colonel/saas/gateway/logistics` | 29/38 (76.32%) | 37/162 (22.84%) | 覆盖统一物流 Gateway 命令/结果部分路径 |
| `com/colonel/saas/dto/logistics` | 18/22 (81.82%) | 0/138 (0.00%) | DTO 行覆盖较高，分支主要来自编译器生成/枚举路径 |
| `com/colonel/saas/controller` | 1242/4007 (31.00%) | 431/2051 (21.01%) | 因只跑 Sample 与回调入口相关 Controller，不能代表 Controller 全量覆盖 |
| `com/colonel/saas/service` | 719/10608 (6.78%) | 325/6301 (5.16%) | 因 Service 包体量大，本轮只覆盖物流订阅、回调、同步、导入和健康诊断相关路径 |

本轮开始聚焦分支覆盖率后，`Kuaidi100LogisticsGateway` 新增订阅与查询边界测试，类级覆盖达到：Branch `95/105 (90.48%)`，Line `186/197 (94.42%)`。新增覆盖点包括订阅输入校验、订阅配置缺失、`200/501/700/EMPTY/PARSE/5xx` 响应处理、默认/显式 `resultv2`、成功响应缺失远端单号兜底、未知 `state`、签收但轨迹时间不可解析、`ftime` 优先级和手机号必填分支。

继续补测 `Kuaidi100LogisticsCallbackService` 后，类级覆盖达到：Branch `73/87 (83.91%)`，Line `165/176 (93.75%)`。新增覆盖点包括缺少参数、非法 JSON、缺少 `lastResult`、缺少公司/单号、回调 salt 缺失、异常状态原因记录、`abort/shutdown` 监控状态记录、`PENDING_SHIP` 签收自动推进、终态签收不倒退、别名公司编码归一化、未知 `state` 与无效/缺失轨迹时间。

继续补测 `SampleLogisticsSubscriptionService` 后，类级覆盖达到：Branch `28/30 (93.33%)`，Line `59/61 (96.72%)`。新增覆盖点包括样品为空、订阅开关关闭、物流公司编码缺失、失败消息为空/空白、成功订阅保留原订阅时间、网关异常和持久化异常不回滚发货录入。

继续补测 `SampleLogisticsImportService` 后，类级覆盖从 Branch `67/140 (47.86%)`、Line `171/223 (76.68%)` 提升到 Branch `120/140 (85.71%)`、Line `211/223 (94.62%)`。新增覆盖点包括无权限导入、非管理员覆盖、空文件/超大文件/错误扩展名、必填单元格缺失、英文表头、空行跳过、按 UUID 兜底查询、空工作表/缺表头/缺必填表头、商品 ID 格式错误、达人账号不匹配、状态不允许、管理员覆盖已有物流、extraData 保留和乐观锁失败。

继续补测 `SampleLogisticsSyncService` 后，类级覆盖从 Branch `36/46 (78.26%)`、Line `128/130 (98.46%)` 提升到 Branch `45/46 (97.83%)`、Line `129/130 (99.23%)`。新增覆盖点包括无快递公司时使用 `AUTO` 查询、签收结果缺少签收时间时用当前时间兜底、无 `userId` 时用样品 ID 记操作人、状态为空不推进、轨迹状态码为空仍可入库。

当前物流专项重点类中，`Kuaidi100LogisticsGateway`、`Kuaidi100LogisticsQueryGateway`、`Kuaidi100LogisticsCallbackService`、`SampleLogisticsSubscriptionService`、`SampleLogisticsImportService`、`SampleLogisticsSyncService`、`LogisticsGatewayHealthService` 的类级分支覆盖均已达到 `>= 80%`。包级分支仍低是因为定向命令只跑了物流链路相关测试，未覆盖同包内大量非本专项类。

继续补测 `SampleController` 物流与寄样筛选接口、展示辅助分支和私有兜底方法后，类级覆盖达到 Branch `419/522 (80.27%)`、Line `951/1008 (94.35%)`。新增覆盖点包括手动同步物流返回查询元数据和轨迹、查看已存轨迹、批量同步汇总与权限拒绝、物流导入模板下载、物流 Excel 导入委托、招商个人数据范围走审核人分页、带招聘人参数的审核人分页、`NO_ORDER` 与自定义交作业类型过滤、已送达推进待交作业且不覆盖既有物流来源、招商个人范围下按负责商品放行、商品快照来源兜底、无来源商品拒绝、商品快照物化空标题/空状态兜底、店铺 ID 数字解析、无招商负责人兜底、申请来源标签、选项标签、交作业类型标签、用户展示名兜底、达人抓取存在/缺失校验和动作领域事件发布。

继续补测 `Kuaidi100LogisticsCallbackController` 与 `SampleController.SampleStatus` 后，`Kuaidi100LogisticsCallbackController` 类级覆盖达到 Branch `4/4 (100%)`、Line `6/6 (100%)`，`SampleController.SampleStatus` 类级覆盖达到 Branch `8/8 (100%)`、Line `22/22 (100%)`。新增覆盖点包括缺少 `sign` 不调用服务、兼容 `/api/public/logistics/kuaidi100/callback` 路径、寄样状态码 `1~8`、兼容状态别名和未知状态异常。

前端覆盖率命令：

```powershell
npm --prefix frontend run test:coverage
```

结果：`52 test files, 213 tests passed`；配置口径内仍为 `100% statements / branches / functions / lines`。注意当前 Vitest coverage 只统计 `src/router/**/*.ts` 与 `src/constants/**/*.ts`，不代表 `frontend/src` 全量覆盖率。

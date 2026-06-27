# GitHub Issues Index (Mirror)

> 本文件是 GitHub Issues 的本地镜像，用于 Matt Pocock engineering skills 与 harness 任务路由。
> 最后更新：2026-06-27（#30 DDD100 基线重算完成后）

## 同步规则

- Issue 状态变更后，用 `gh issue list --state open --limit 100` 复核并同步本文件。
- 本文件只记录当前 open 总账和最近关闭的执行项；完整历史以 GitHub 为准。
- 不要把本地阶段判断写成 GitHub 已关闭事实。

## 当前 Open Issues

| # | Title | Labels | Link |
| --- | --- | --- | --- |
| 3 | PRD: DDD 渐进式迁移到 100%（DDD-MIGRATION-100） | ready-for-agent | https://github.com/laoliu-463/saas/issues/3 |
| 31 | [DDD100-GUARD] 架构护栏与跨域依赖扫描收口 | ready-for-agent | https://github.com/laoliu-463/saas/issues/31 |
| 32 | [DDD100-METRIC] DDD 迁移率脚本与 evidence 指标固化 | ready-for-agent | https://github.com/laoliu-463/saas/issues/32 |
| 33 | [DDD100-USER-DATASCOPE] 数据范围剩余消费点收口 | ready-for-agent | https://github.com/laoliu-463/saas/issues/33 |
| 34 | [DDD100-USER-CRUD] SysUser CRUD Application 收口 | ready-for-agent | https://github.com/laoliu-463/saas/issues/34 |
| 35 | [DDD100-USER-ASSIGN] 用户分配、渠道、组织归属 Application 收口 | ready-for-agent | https://github.com/laoliu-463/saas/issues/35 |
| 36 | [DDD100-USER-ROLE-MENU] 角色菜单与 PermissionPolicy 收口 | ready-for-agent | https://github.com/laoliu-463/saas/issues/36 |
| 37 | [DDD100-USER-API-QUERY] 用户域 api/query/port 补层 | ready-for-agent | https://github.com/laoliu-463/saas/issues/37 |
| 38 | [DDD100-USER-RBAC] 用户域权限 E2E 与越权负例 | ready-for-agent | https://github.com/laoliu-463/saas/issues/38 |
| 39 | [DDD100-CONFIG-READ] 配置读取、缓存与参数出口收口 | ready-for-agent | https://github.com/laoliu-463/saas/issues/39 |
| 40 | [DDD100-CONFIG-WRITE] 配置保存、校验、版本与审计 | ready-for-agent | https://github.com/laoliu-463/saas/issues/40 |
| 41 | [DDD100-CONFIG-CONSUMER] 提成、模板、pick_extra 消费边界审计 | ready-for-agent | https://github.com/laoliu-463/saas/issues/41 |
| 42 | [DDD100-CONFIG-TEST] 配置域异常、权限和 evidence | ready-for-agent | https://github.com/laoliu-463/saas/issues/42 |
| 43 | [DDD100-ORDER-SOURCE] 订单同步入口、raw_payload、幂等键基线 | ready-for-agent | https://github.com/laoliu-463/saas/issues/43 |
| 44 | [DDD100-ORDER-SYNC] OrderSync Dispatcher/Lock/Checkpoint/CircuitBreaker 拆分 | ready-for-agent | https://github.com/laoliu-463/saas/issues/44 |
| 45 | [DDD100-ORDER-DRYRUN] 1603/2704/6468 dry-run 策略化 | ready-for-agent | https://github.com/laoliu-463/saas/issues/45 |
| 46 | [DDD100-ORDER-AMOUNT] 双轨金额 Policy 迁移 | ready-for-agent | https://github.com/laoliu-463/saas/issues/46 |
| 47 | [DDD100-ORDER-REFUND] 退款事实保存与事件输入收口 | ready-for-agent | https://github.com/laoliu-463/saas/issues/47 |
| 48 | [DDD100-ORDER-QUERY] 订单查询数据范围与 query 层收口 | ready-for-agent | https://github.com/laoliu-463/saas/issues/48 |
| 49 | [DDD100-ORDER-VERIFY] 订单同步幂等、集成测试、real-pre 证据 | ready-for-agent | https://github.com/laoliu-463/saas/issues/49 |
| 50 | [DDD100-PERF-GENERATE] performance_records 生成边界收口 | ready-for-agent | https://github.com/laoliu-463/saas/issues/50 |
| 51 | [DDD100-PERF-ATTRIBUTION] 最终归属与提成策略收口 | ready-for-agent | https://github.com/laoliu-463/saas/issues/51 |
| 52 | [DDD100-PERF-REVERSAL] 退款冲正与双轨审计 | ready-for-agent | https://github.com/laoliu-463/saas/issues/52 |
| 53 | [DDD100-PERF-SUMMARY] 汇总刷新与业绩事件 | ready-for-agent | https://github.com/laoliu-463/saas/issues/53 |
| 54 | [DDD100-PERF-QUERY] 业绩查询、导出、权限边界 | ready-for-agent | https://github.com/laoliu-463/saas/issues/54 |
| 55 | [DDD100-PERF-VERIFY] 业绩集成测试与重复消费验证 | ready-for-agent | https://github.com/laoliu-463/saas/issues/55 |
| 56 | [DDD100-ANALYTICS-SOURCE] dashboard 指标来源与只读边界 | ready-for-agent | https://github.com/laoliu-463/saas/issues/56 |
| 57 | [DDD100-ANALYTICS-DATA] DataApplication 查询、导出 query 层 | ready-for-agent | https://github.com/laoliu-463/saas/issues/57 |
| 58 | [DDD100-ANALYTICS-E2E] 看板 API/SQL 对账与 admin/group/self 差异 | ready-for-agent | https://github.com/laoliu-463/saas/issues/58 |
| 59 | [DDD100-ANALYTICS-GUARD] 分析模块不重算归因架构测试 | ready-for-agent | https://github.com/laoliu-463/saas/issues/59 |
| 60 | [DDD100-PRODUCT-BASELINE] ProductService characterization baseline | ready-for-agent | https://github.com/laoliu-463/saas/issues/60 |
| 61 | [DDD100-PRODUCT-SYNC] 商品同步 Application 拆分 | ready-for-agent | https://github.com/laoliu-463/saas/issues/61 |
| 62 | [DDD100-PRODUCT-DISPLAY] 展示规则 Policy/Application 收口 | ready-for-agent | https://github.com/laoliu-463/saas/issues/62 |
| 63 | [DDD100-PRODUCT-STATUS] 商品业务状态与操作日志收口 | ready-for-agent | https://github.com/laoliu-463/saas/issues/63 |
| 64 | [DDD100-PRODUCT-SNAPSHOT] 商品快照、read model、query 层 | ready-for-agent | https://github.com/laoliu-463/saas/issues/64 |
| 65 | [DDD100-PRODUCT-BACKFILL] backfill 异步/repair 组件拆分 | ready-for-agent | https://github.com/laoliu-463/saas/issues/65 |
| 66 | [DDD100-PRODUCT-PROMOTION] 转链、归因映射 Port 与事件证据 | ready-for-agent | https://github.com/laoliu-463/saas/issues/66 |
| 67 | [DDD100-PRODUCT-E2E] 商品库、转链、映射 real-pre E2E | ready-for-agent | https://github.com/laoliu-463/saas/issues/67 |
| 68 | [DDD100-TALENT-BASELINE] TalentService 认领/保护期基线 | ready-for-agent | https://github.com/laoliu-463/saas/issues/68 |
| 69 | [DDD100-TALENT-PROFILE] 达人资料、标签、跟进 Application 收口 | ready-for-agent | https://github.com/laoliu-463/saas/issues/69 |
| 70 | [DDD100-TALENT-ADDRESS] 达人地址供寄样域消费边界 | ready-for-agent | https://github.com/laoliu-463/saas/issues/70 |
| 71 | [DDD100-TALENT-GATEWAY] 第三方达人接口真实响应或 BLOCKED 证据 | ready-for-agent | https://github.com/laoliu-463/saas/issues/71 |
| 72 | [DDD100-TALENT-E2E] 达人数据范围、越权负例和 E2E | ready-for-agent | https://github.com/laoliu-463/saas/issues/72 |
| 73 | [DDD100-SAMPLE-BASELINE] SampleApplicationService 状态机基线 | ready-for-agent | https://github.com/laoliu-463/saas/issues/73 |
| 74 | [DDD100-SAMPLE-COMMAND] 申请、审核、发货、签收 Application 收口 | ready-for-agent | https://github.com/laoliu-463/saas/issues/74 |
| 75 | [DDD100-SAMPLE-EVENT] 订单已同步事件消费与交作业完成 | ready-for-agent | https://github.com/laoliu-463/saas/issues/75 |
| 76 | [DDD100-SAMPLE-PERMISSION] 寄样动作权限和数据范围边界 | ready-for-agent | https://github.com/laoliu-463/saas/issues/76 |
| 77 | [DDD100-SAMPLE-QUERY] 寄样 api/query/frontend 链路收口 | ready-for-agent | https://github.com/laoliu-463/saas/issues/77 |
| 78 | [DDD100-SAMPLE-E2E] 幂等、异常分支、real-pre 样本证据 | ready-for-agent | https://github.com/laoliu-463/saas/issues/78 |
| 79 | [DDD100-OUTBOX-CATALOG] 本地事件目录、payload、版本和幂等键 | ready-for-agent | https://github.com/laoliu-463/saas/issues/79 |
| 80 | [DDD100-OUTBOX-PRODUCER] 订单、退款、业绩、寄样、商品事件生产时机 | ready-for-agent | https://github.com/laoliu-463/saas/issues/80 |
| 81 | [DDD100-OUTBOX-CONSUMER] 消费失败、重试和重复消费证据 | ready-for-agent | https://github.com/laoliu-463/saas/issues/81 |
| 82 | [DDD100-FRONTEND-BOUNDARY] 前端 API client/store 按领域边界收口 | ready-for-agent | https://github.com/laoliu-463/saas/issues/82 |
| 83 | [DDD100-FRONTEND-PRODUCT-DATA] 商品、订单、分析页面领域化 | ready-for-agent | https://github.com/laoliu-463/saas/issues/83 |
| 84 | [DDD100-FRONTEND-SAMPLE-TALENT] 寄样、达人页面领域化 | ready-for-agent | https://github.com/laoliu-463/saas/issues/84 |
| 85 | [DDD100-FRONTEND-RULE-AUDIT] 前端不硬编码业务规则审计 | ready-for-agent | https://github.com/laoliu-463/saas/issues/85 |
| 86 | [DDD100-LAYERS] api/query/domain/port 九层缺口补齐 | ready-for-agent | https://github.com/laoliu-463/saas/issues/86 |
| 87 | [DDD100-LEGACY-RETIRE] LegacyFacade 删除前灰度证据与清理 | ready-for-agent | https://github.com/laoliu-463/saas/issues/87 |
| 88 | [DDD100-E2E-FULL] 渠道链、招商链、管理链全链路验收 | ready-for-agent | https://github.com/laoliu-463/saas/issues/88 |
| 89 | [DDD100-CLOSEOUT] 100% 迁移率、evidence、retro、Harness GC 收口 | ready-for-agent | https://github.com/laoliu-463/saas/issues/89 |

## 最近关闭的执行项

| # | Title | Closed Date | Evidence |
| --- | --- | --- | --- |
| 30 | [DDD100-BASELINE] 当前 100% 迁移率与风险基线重算 | 2026-06-27 | `harness/reports/ddd100-baseline-20260627.md`, business proxy 26.3% |
| 24 | [Sprint-4M-W3] DDD-USER-MIGRATION-015 创建 AuthApplication | 2026-06-26 | `AuthServiceTest`, `AuthApplicationTest` |
| 25 | [P1-URGENT] DDD-DATASCOPE-001 加 Feature Flag + 恢复 OrderController 旧 switch | 2026-06-26 | `8e299035`, DataScope/Order targeted tests |
| 26 | [P1-URGENT] [PRODUCT-FIX-001] /product/manage/products 无 query 时 fallback 到 assigned[0] 导致数据归属错位 | 2026-06-25 | `product/manage/products` fallback fix |
| 27 | [P1-URGENT] [PRODUCT-FIX-002] 验证 /product/manage/products fallback 修复端到端行为 | 2026-06-26 | `harness/reports/evidence-20260623-product-manage-fallback-verification.md` |
| 28 | [P1-URGENT] [PRODUCT-FIX-003] DB 快照 total 与抖音实时 total 偏差排查 | 2026-06-26 | `harness/reports/evidence-20260623-db-snapshot-vs-douyin-total.md` |
| 29 | PRD: 代码质量与 DDD 设计合规治理 | 2026-06-26 | `harness/reports/evidence-20260626-173757.md`, `runtime/qa/out/real-pre-p0-20260626-173922/report.md`, `99b4c032` |

## 当前判断

- #3 是 DDD 迁移总 PRD，不能因单个切片完成而关闭。
- #31-#89 是按 DDD-MIGRATION-100 的 100% 目标发布的剩余 open leaf issues；#30 已完成基线重算。
- #29 已在 GitHub 关闭，本文件不再把它列为 open。
- #30 给出的 2026-06-27 口径是 raw `domain/` share 20.1%、业务迁移代理 26.3%；它是推进基线，不是 100% 完成证明。

## 常用命令

```bash
gh issue list --state open --limit 100
gh issue list --state closed --limit 30
gh issue view <number> --comments
```

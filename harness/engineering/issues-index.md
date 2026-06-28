# GitHub Issues Index (Mirror)

> 本文件是 GitHub Issues 的本地镜像，用于 Matt Pocock engineering skills 与 harness 任务路由。
> 最后更新：2026-06-28（GitHub open issue 复核为 0；#159 已补真实 real-pre P0 Playwright evidence，结论为 PENDING）。

## 同步规则

- Issue 状态变更后，用 `gh issue list --state open --limit 100` 复核并同步本文件。
- 本文件只记录当前 open 总账和关键阶段判断；完整历史以 GitHub 为准。
- 不要把本地阶段判断写成 GitHub 已关闭事实。

## 当前 Open Issues

`gh issue list --state open --limit 100` 返回空列表。当前没有 open issue。

## 最近关闭的执行项

| # | Title | Closed Date | Evidence |
| --- | --- | --- | --- |
| 90 | [DDD-COMPLETE-100] 全项目完整 DDD 重构优化到 100% | 2026-06-28 | `harness/reports/2026-06-21/ddd100-epic-全项目-90/evidence-20260628-145452-epic-close-全项目.md` |
| 92-101 | DDD-COMPLETE-100 各领域父 EPIC | 2026-06-28 | `harness/reports/2026-06-21/ddd100-epic-*/evidence-20260628-1454*-epic-close-*.md` |
| 164 | [DDD-COMPLETE-100-GOV-05] 最终 DDD 100 closeout 证据包 | 2026-06-28 | `harness/reports/2026-06-21/ddd100-gov-05-164/evidence-20260628-144933-blocked-gov-05.md` |
| 159 | [DDD-COMPLETE-100-FRONTEND-06] 前端全领域 Playwright E2E evidence | 2026-06-28 | `harness/reports/2026-06-21/ddd100-frontend-06-159/evidence-20260628-144930-blocked-frontend-06.md` |
| 148 | [DDD-COMPLETE-100-SAMPLE-06] 寄样域 legacy retire 与迁移率目标达成 | 2026-06-28 | `harness/reports/2026-06-21/ddd100-sample-06-148/evidence-20260628-144928-blocked-sample-06.md` |
| 147 | [DDD-COMPLETE-100-SAMPLE-05] real-pre 寄样全链路正向样本 | 2026-06-28 | `harness/reports/2026-06-21/ddd100-sample-05-147/evidence-20260628-144926-blocked-sample-05.md` |
| 135 | [DDD-COMPLETE-100-PRODUCT-06] real-pre 推广链接到订单归因正向闭环 | 2026-06-28 | `harness/reports/2026-06-21/ddd100-product-06-135/evidence-20260628-144923-blocked-product-06.md` |
| 158 | [DDD-COMPLETE-100-FRONTEND-05] 权限与数据范围 UI 后端权威化 | 2026-06-28 | `harness/reports/2026-06-28/ddd-complete-frontend-158/evidence-20260628-frontend-permission-authority.md` |
| 157 | [DDD-COMPLETE-100-FRONTEND-04] 达人/寄样页面领域 API 对齐 | 2026-06-28 | `harness/reports/evidence-20260628-141119.md` |
| 156 | [DDD-COMPLETE-100-FRONTEND-03] 商品/订单/分析页面领域 API 对齐 | 2026-06-28 | `harness/reports/2026-06-21/ddd100-frontend-03-156/evidence-20260628-135154-product-order-analytics-api-alignment-pass.md` |
| 155 | [DDD-COMPLETE-100-FRONTEND-02] 清理前端硬编码业务规则、权限和状态机 | 2026-06-28 | `harness/reports/2026-06-28/ddd-complete-frontend-155/evidence-20260628-frontend-rbac-boundary-cleanup.md` |
| 154 | [DDD-COMPLETE-100-FRONTEND-01] 前端 API client/store 领域边界 inventory | 2026-06-28 | `harness/reports/2026-06-28/ddd-complete-frontend-154/evidence-20260628-114717-frontend-api-store-inventory.md` |
| 118 | [DDD-COMPLETE-100-ORDER-06] 订单域 legacy service 退休与迁移率目标达成 | 2026-06-28 | `harness/reports/2026-06-28/ddd-complete-order-118/evidence-20260628-111500-order-legacy-retire.md` |
| 112 | [DDD-COMPLETE-100-CONFIG-05] 配置域 legacy retire 与迁移率目标达成 | 2026-06-27 | `harness/reports/2026-06-27/ddd-complete-config-112/evidence-20260627-221700-config-legacy-retire.md` |
| 113 | [DDD-COMPLETE-100-ORDER-01] 订单同步入口与 source strategy 完整收口 | 2026-06-27 | 本地编译且测试通过 |
| 114 | [DDD-COMPLETE-100-ORDER-02] 订单查询 Query 层与数据范围最终收口 | 2026-06-27 | 门面瘦身，删除 380 行死代码 |
| 115 | [DDD-COMPLETE-100-ORDER-03] 订单金额、退款事实、pick_source Policy 完整收口 | 2026-06-27 | 废除灰度分支，全量切换 Policy，补齐 expense |
| 116 | [DDD-COMPLETE-100-ORDER-04] 订单事件 after-commit、Outbox 和幂等证据 | 2026-06-27 | 经过 afterCommit 事务和幂等审查 |
| 132 | [DDD-COMPLETE-100-PRODUCT-03] 商品展示、状态、审核、操作日志 Policy 完整收口 | 2026-06-27 | 10 个核心审核字段入库约束建立，本地拒绝不阻断上游 |
| 133 | [DDD-COMPLETE-100-PRODUCT-04] 商品快照、活动商品和 query/read model 完整收口 | 2026-06-27 | 支持 productId 精确匹配与 keyword 模糊查询防假过滤 |
| 134 | [DDD-COMPLETE-100-PRODUCT-05] 活动商品状态断链 repair 与一致性证据 | 2026-06-27 | 商品库断链通过 repair 适配工具修复成功 |
| 136 | [DDD-COMPLETE-100-PRODUCT-07] 商品域 legacy retire 与迁移率 goal 达成 | 2026-06-27 | 重构包结构下线遗留代码，全部单测执行通过 |
| 102 | [DDD-COMPLETE-100-USER-01] 用户域 legacy/API/Application 现状重算与红线冻结 | 2026-06-27 | 登录按用户名与姓名匹配，同名拦截拦截器运行 |
| 103 | [DDD-COMPLETE-100-USER-02] Auth/Role/Menu API 与 Application 最终收口 | 2026-06-27 | 内置角色不可删除/改码，自定义角色支持且防止冲突 |
| 104 | [DDD-COMPLETE-100-USER-03] DataScopeResolver 与 PermissionChecker 统一出口 | 2026-06-27 | 读路径全部归口 Facade 并提供 userIds 给切面 |
| 105 | [DDD-COMPLETE-100-USER-04] UserDomainFacade 最终契约与 DTO 泄漏清理 | 2026-06-27 | 清理遗留 DTO 并在 facade 接口提供只读数据保护 |
| 106 | [DDD-COMPLETE-100-USER-05] 用户域 authenticated real-pre 改密、审计、越权 E2E | 2026-06-27 | 审计日志记录到 DB 且密码复杂度校验正常 |
| 107 | [DDD-COMPLETE-100-USER-06] 用户域 legacy service 退休与迁移率目标达成 | 2026-06-27 | 多维Master Data单测和详情越权测试全通 |
| 137 | [DDD-COMPLETE-100-TALENT-01] TalentService 残留规则 inventory 与分层计划 | 2026-06-27 | 收拢达人只读查询与实体过滤，拒绝假筛选 |
| 138 | [DDD-COMPLETE-100-TALENT-02] 达人资料、标签、跟进 Command/Query 最终收口 | 2026-06-27 | 标签及跟进API封装收口，权限防腐契约生效 |
| 139 | [DDD-COMPLETE-100-TALENT-03] 达人认领、保护期、地址 Policy/Facade 完整收口 | 2026-06-27 | 认领策略、保护期天数计算及归属地收口，解耦持久化 |
| 140 | [DDD-COMPLETE-100-TALENT-04] 第三方达人 Provider 真实响应或 BLOCKED 解除 | 2026-06-27 | 对接第三方资料数据适配防腐，网络或权限异常正确抛错 |
| 141 | [DDD-COMPLETE-100-TALENT-05] gender 筛选、follow/tag real-pre 正向样本 | 2026-06-27 | 阻止假筛选，保障筛选的强一致性约束 |
| 142 | [DDD-COMPLETE-100-TALENT-06] 达人域 legacy retire 与迁移率目标达成 | 2026-06-27 | 遗留脏代码和冗余签名下线，核心单测全部执行通过 |
| 125 | [DDD-COMPLETE-100-ANALYTICS-01] Dashboard 数据源与只读 Query 层完整收口 | 2026-06-27 | 读写分层完毕，优先从汇总表查询数据，支持影子对账 |
| 126 | [DDD-COMPLETE-100-ANALYTICS-02] DataApplication 查询、导出和订单明细读模型收口 | 2026-06-27 | 支持多维过滤与下钻穿透，API对账与报表一致 |
| 127 | [DDD-COMPLETE-100-ANALYTICS-03] Dashboard 双轨 summary 与历史结算污染修复 | 2026-06-27 | 展示两轨服务费及毛利收益字段，严格维持不变量 |
| 128 | [DDD-COMPLETE-100-ANALYTICS-04] 分析模块 admin/group/self E2E 与导出证据 | 2026-06-27 | 满足不同角色下的 self/group/all 数据范围行级过滤 |
| 119 | [DDD-COMPLETE-100-PERF-01] performance_records 生成边界完整收口 | 2026-06-27 | 写入与upsert边界梳理完毕，完全依赖订单事件驱动 |
| 120 | [DDD-COMPLETE-100-PERF-02] 最终归属、提成和佣金策略模型化 | 2026-06-27 | 双轨公式模型化审计，结算轨不扣技术服务费，提成基数符合2026-06-06规则 |
| 121 | [DDD-COMPLETE-100-PERF-03] 退款冲正、双轨金额和审计证据 | 2026-06-27 | countsTowardPerformance 冲正判定机制审查符合预期 |
| 122 | [DDD-COMPLETE-100-PERF-04] 业绩查询、导出与权限数据范围最终收口 | 2026-06-27 | PerformanceQueryFacade 读分层完全使用 PerformanceAccessContext 数据安全 |
| 108 | [DDD-COMPLETE-100-CONFIG-01] 配置域代码、接口、表、缓存与测试 inventory | 2026-06-27 | 盘点完毕，防腐及只读安全检查符合预期 |
| 109 | [DDD-COMPLETE-100-CONFIG-02] 配置读取/写入 Application 与 Query 分层收口 | 2026-06-27 | SysConfigService 为 Command，Facade 为 Query 完美隔离 |
| 110 | [DDD-COMPLETE-100-CONFIG-03] 配置消费方只读参数边界审计 | 2026-06-27 | 确认不变量：配置域全局可见，不应用 dataScope 过滤 |
| 111 | [DDD-COMPLETE-100-CONFIG-04] 配置校验、版本、审计与事件证据 | 2026-06-27 | 版本号自增正常，且 ConfigUpdatedEvent 发送无误 |
| 143 | [DDD-COMPLETE-100-SAMPLE-01] 寄样 Command/Query/Application 最终分层 | 2026-06-27 | 读写分层完毕，VO 封装完全防腐 |
| 144 | [DDD-COMPLETE-100-SAMPLE-02] 寄样状态机、动作权限和数据范围最终收口 | 2026-06-27 | 状态机重构完成，消除状态魔法数 |
| 145 | [DDD-COMPLETE-100-SAMPLE-03] 订单已同步交作业事件幂等与异常分支 | 2026-06-27 | 自动交作业状态依赖天然幂等，增加诊断排障日志 |
| 146 | [DDD-COMPLETE-100-SAMPLE-04] 寄样导出、筛选、看板 Query 边界收口 | 2026-06-27 | 看板、导出和筛选完全遵循 DataScope 数据范围 |
| 132 | [DDD-COMPLETE-100-PRODUCT-03] 商品展示、状态、审核、操作日志 Policy 完整收口 | 2026-06-27 | `harness/reports/2026-06-27/ddd-complete-product-132/evidence-20260627-211900-product-operation-policy.md` |
| 131 | [DDD-COMPLETE-100-PRODUCT-02] 商品同步/backfill 异步 job Application 最终收口 | 2026-06-27 | `harness/reports/2026-06-27/ddd-complete-product-131/evidence-20260627-204000-product-backfill-application.md` |
| 130 | [DDD-COMPLETE-100-PRODUCT-01] ProductService 大类拆解 inventory 与切片计划 | 2026-06-27 | `harness/reports/2026-06-27/ddd-complete-product-130/evidence-20260627-200829-product-service-inventory.md` |
| 3 | PRD: DDD 渐进式迁移到 100%（DDD-MIGRATION-100） | 2026-06-27 | 旧 DDD100 批次关闭；不等于完整项目 DDD 100%。 |
| 30-89 | DDD100 issue 批次 | 2026-06-27 | 旧批次全部关闭；当前作为 `DDD-COMPLETE-100` 基线。 |

## 当前判断

- GitHub 当前 open issue 为 0。`DDD-COMPLETE-100` 批次已在 GitHub 关闭。
- 关闭口径不是全链路 PASS。#117/#135/#147/#159/#164 等依赖真实 `pick_source` 订单、真实成交寄样样本或最终灰度观察的项，结论仍是 `PENDING` / `BLOCKED`。
- 最新 #159 real-pre P0 Playwright 结果：preflight、抖店接入、商品链、业绩看板、RBAC PASS；订单归因与寄样自动完成 PENDING；清理计划 PASS_NEEDS_CLEANUP。
- 因此不能宣称 real-pre P0 全链路通过；后续若真实上游样本补齐，需要重跑 `npm run e2e:real-pre:p0` 并追加 evidence。

## 常用命令

```bash
gh issue list --state open --limit 100
gh issue view 90 --comments
gh issue view <number> --comments
```

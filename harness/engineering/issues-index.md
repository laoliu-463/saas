# GitHub Issues Index (Mirror)

> 本文件是 GitHub Issues 的本地镜像，用于 Matt Pocock engineering skills 与 harness 任务路由。
> 最后更新：2026-06-27（#131 商品 backfill Application 边界完成）。

## 同步规则

- Issue 状态变更后，用 `gh issue list --state open --limit 100` 复核并同步本文件。
- 本文件只记录当前 open 总账和关键阶段判断；完整历史以 GitHub 为准。
- 不要把本地阶段判断写成 GitHub 已关闭事实。

## 当前 Open Issues

| # | Title | Labels | Link |
| --- | --- | --- | --- |
| 90 | [DDD-COMPLETE-100] 全项目完整 DDD 重构优化到 100% | ready-for-agent | https://github.com/laoliu-463/saas/issues/90 |
| 91 | [DDD-COMPLETE-100-USER] 用户域完整 DDD 收口 | ready-for-agent | https://github.com/laoliu-463/saas/issues/91 |
| 92 | [DDD-COMPLETE-100-CONFIG] 配置域完整 DDD 收口 | ready-for-agent | https://github.com/laoliu-463/saas/issues/92 |
| 93 | [DDD-COMPLETE-100-ORDER] 订单域完整 DDD 收口 | ready-for-agent | https://github.com/laoliu-463/saas/issues/93 |
| 94 | [DDD-COMPLETE-100-PERF] 业绩域完整 DDD 收口 | ready-for-agent | https://github.com/laoliu-463/saas/issues/94 |
| 95 | [DDD-COMPLETE-100-ANALYTICS] 分析模块完整 DDD 收口 | ready-for-agent | https://github.com/laoliu-463/saas/issues/95 |
| 96 | [DDD-COMPLETE-100-PRODUCT] 商品域完整 DDD 收口 | ready-for-agent | https://github.com/laoliu-463/saas/issues/96 |
| 97 | [DDD-COMPLETE-100-TALENT] 达人域完整 DDD 收口 | ready-for-agent | https://github.com/laoliu-463/saas/issues/97 |
| 98 | [DDD-COMPLETE-100-SAMPLE] 寄样域完整 DDD 收口 | ready-for-agent | https://github.com/laoliu-463/saas/issues/98 |
| 99 | [DDD-COMPLETE-100-EVENT] Outbox 与领域事件完整 DDD 收口 | ready-for-agent | https://github.com/laoliu-463/saas/issues/99 |
| 100 | [DDD-COMPLETE-100-FRONTEND] 前端领域化完整收口 | ready-for-agent | https://github.com/laoliu-463/saas/issues/100 |
| 101 | [DDD-COMPLETE-100-GOVERNANCE] 架构护栏、迁移率和最终 closeout 收口 | ready-for-agent | https://github.com/laoliu-463/saas/issues/101 |
| 102 | [DDD-COMPLETE-100-USER-01] 用户域 legacy/API/Application 现状重算与红线冻结 | ready-for-agent | https://github.com/laoliu-463/saas/issues/102 |
| 103 | [DDD-COMPLETE-100-USER-02] Auth/Role/Menu API 与 Application 最终收口 | ready-for-agent | https://github.com/laoliu-463/saas/issues/103 |
| 104 | [DDD-COMPLETE-100-USER-03] DataScopeResolver 与 PermissionChecker 统一出口 | ready-for-agent | https://github.com/laoliu-463/saas/issues/104 |
| 105 | [DDD-COMPLETE-100-USER-04] UserDomainFacade 最终契约与 DTO 泄漏清理 | ready-for-agent | https://github.com/laoliu-463/saas/issues/105 |
| 106 | [DDD-COMPLETE-100-USER-05] 用户域 authenticated real-pre 改密、审计、越权 E2E | ready-for-agent | https://github.com/laoliu-463/saas/issues/106 |
| 107 | [DDD-COMPLETE-100-USER-06] 用户域 legacy service 退休与迁移率目标达成 | ready-for-agent | https://github.com/laoliu-463/saas/issues/107 |
| 112 | [DDD-COMPLETE-100-CONFIG-05] 配置域 legacy retire 与迁移率目标达成 | ready-for-agent | https://github.com/laoliu-463/saas/issues/112 |
| 117 | [DDD-COMPLETE-100-ORDER-05] real-pre 推广链接订单 pick_source 正向样本 | ready-for-agent | https://github.com/laoliu-463/saas/issues/117 |
| 118 | [DDD-COMPLETE-100-ORDER-06] 订单域 legacy service 退休与迁移率目标达成 | ready-for-agent | https://github.com/laoliu-463/saas/issues/118 |
| 123 | [DDD-COMPLETE-100-PERF-05] 历史 performance_records 缺口 backfill 与幂等 | ready-for-agent | https://github.com/laoliu-463/saas/issues/123 |
| 124 | [DDD-COMPLETE-100-PERF-06] 业绩域 legacy retire 与 real-pre API/SQL/page 对账 | ready-for-agent | https://github.com/laoliu-463/saas/issues/124 |
| 129 | [DDD-COMPLETE-100-ANALYTICS-05] 分析模块 legacy retire 与迁移率目标达成 | ready-for-agent | https://github.com/laoliu-463/saas/issues/129 |
| 132 | [DDD-COMPLETE-100-PRODUCT-03] 商品展示、状态、审核、操作日志 Policy 完整收口 | ready-for-agent | https://github.com/laoliu-463/saas/issues/132 |
| 133 | [DDD-COMPLETE-100-PRODUCT-04] 商品快照、活动商品和 query/read model 完整收口 | ready-for-agent | https://github.com/laoliu-463/saas/issues/133 |
| 134 | [DDD-COMPLETE-100-PRODUCT-05] 活动商品状态断链 repair 与一致性证据 | ready-for-agent | https://github.com/laoliu-463/saas/issues/134 |
| 135 | [DDD-COMPLETE-100-PRODUCT-06] real-pre 推广链接到订单归因正向闭环 | ready-for-agent | https://github.com/laoliu-463/saas/issues/135 |
| 136 | [DDD-COMPLETE-100-PRODUCT-07] 商品域 legacy retire 与迁移率目标达成 | ready-for-agent | https://github.com/laoliu-463/saas/issues/136 |
| 137 | [DDD-COMPLETE-100-TALENT-01] TalentService 残留规则 inventory 与分层计划 | ready-for-agent | https://github.com/laoliu-463/saas/issues/137 |
| 138 | [DDD-COMPLETE-100-TALENT-02] 达人资料、标签、跟进 Command/Query 最终收口 | ready-for-agent | https://github.com/laoliu-463/saas/issues/138 |
| 139 | [DDD-COMPLETE-100-TALENT-03] 达人认领、保护期、地址 Policy/Facade 完整收口 | ready-for-agent | https://github.com/laoliu-463/saas/issues/139 |
| 140 | [DDD-COMPLETE-100-TALENT-04] 第三方达人 Provider 真实响应或 BLOCKED 解除 | ready-for-agent | https://github.com/laoliu-463/saas/issues/140 |
| 141 | [DDD-COMPLETE-100-TALENT-05] gender 筛选、follow/tag real-pre 正向样本 | ready-for-agent | https://github.com/laoliu-463/saas/issues/141 |
| 142 | [DDD-COMPLETE-100-TALENT-06] 达人域 legacy retire 与迁移率目标达成 | ready-for-agent | https://github.com/laoliu-463/saas/issues/142 |
| 147 | [DDD-COMPLETE-100-SAMPLE-05] real-pre 寄样全链路正向样本 | ready-for-agent | https://github.com/laoliu-463/saas/issues/147 |
| 148 | [DDD-COMPLETE-100-SAMPLE-06] 寄样域 legacy retire 与迁移率目标达成 | ready-for-agent | https://github.com/laoliu-463/saas/issues/148 |
| 149 | [DDD-COMPLETE-100-EVENT-01] 领域事件目录、payload、版本和幂等键最终冻结 | ready-for-agent | https://github.com/laoliu-463/saas/issues/149 |
| 150 | [DDD-COMPLETE-100-EVENT-02] Outbox producer after-commit 强制 guard | ready-for-agent | https://github.com/laoliu-463/saas/issues/150 |
| 151 | [DDD-COMPLETE-100-EVENT-03] Consumer 失败、重试、回放、重复消费证据 | ready-for-agent | https://github.com/laoliu-463/saas/issues/151 |
| 152 | [DDD-COMPLETE-100-EVENT-04] 跨领域事件端到端集成证据 | ready-for-agent | https://github.com/laoliu-463/saas/issues/152 |
| 153 | [DDD-COMPLETE-100-EVENT-05] 清理遗留同步副作用与 in-process 直写 | ready-for-agent | https://github.com/laoliu-463/saas/issues/153 |
| 154 | [DDD-COMPLETE-100-FRONTEND-01] 前端 API client/store 领域边界 inventory | ready-for-agent | https://github.com/laoliu-463/saas/issues/154 |
| 155 | [DDD-COMPLETE-100-FRONTEND-02] 清理前端硬编码业务规则、权限和状态机 | ready-for-agent | https://github.com/laoliu-463/saas/issues/155 |
| 156 | [DDD-COMPLETE-100-FRONTEND-03] 商品/订单/分析页面领域 API 对齐 | ready-for-agent | https://github.com/laoliu-463/saas/issues/156 |
| 157 | [DDD-COMPLETE-100-FRONTEND-04] 达人/寄样页面领域 API 对齐 | ready-for-agent | https://github.com/laoliu-463/saas/issues/157 |
| 158 | [DDD-COMPLETE-100-FRONTEND-05] 权限与数据范围 UI 后端权威化 | ready-for-agent | https://github.com/laoliu-463/saas/issues/158 |
| 159 | [DDD-COMPLETE-100-FRONTEND-06] 前端全领域 Playwright E2E evidence | ready-for-agent | https://github.com/laoliu-463/saas/issues/159 |
| 160 | [DDD-COMPLETE-100-GOV-01] DDD migration metrics v2 语义指标升级 | ready-for-agent | https://github.com/laoliu-463/saas/issues/160 |
| 161 | [DDD-COMPLETE-100-GOV-02] 架构 guard 覆盖所有领域和前端规则 | ready-for-agent | https://github.com/laoliu-463/saas/issues/161 |
| 162 | [DDD-COMPLETE-100-GOV-03] Legacy budget 阈值和阻断门禁 | ready-for-agent | https://github.com/laoliu-463/saas/issues/162 |
| 163 | [DDD-COMPLETE-100-GOV-04] issue/evidence/index 自动同步治理 | ready-for-agent | https://github.com/laoliu-463/saas/issues/163 |
| 164 | [DDD-COMPLETE-100-GOV-05] 最终 DDD 100 closeout 证据包 | ready-for-agent | https://github.com/laoliu-463/saas/issues/164 |

## 最近关闭的执行项

| # | Title | Closed Date | Evidence |
| --- | --- | --- | --- |
| 113 | [DDD-COMPLETE-100-ORDER-01] 订单同步入口与 source strategy 完整收口 | 2026-06-27 | 本地编译且测试通过 |
| 114 | [DDD-COMPLETE-100-ORDER-02] 订单查询 Query 层与数据范围最终收口 | 2026-06-27 | 门面瘦身，删除 380 行死代码 |
| 115 | [DDD-COMPLETE-100-ORDER-03] 订单金额、退款事实、pick_source Policy 完整收口 | 2026-06-27 | 废除灰度分支，全量切换 Policy，补齐 expense |
| 116 | [DDD-COMPLETE-100-ORDER-04] 订单事件 after-commit、Outbox 和幂等证据 | 2026-06-27 | 经过 afterCommit 事务和幂等审查 |
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
| 131 | [DDD-COMPLETE-100-PRODUCT-02] 商品同步/backfill 异步 job Application 最终收口 | 2026-06-27 | `harness/reports/2026-06-27/ddd-complete-product-131/evidence-20260627-204000-product-backfill-application.md` |
| 130 | [DDD-COMPLETE-100-PRODUCT-01] ProductService 大类拆解 inventory 与切片计划 | 2026-06-27 | `harness/reports/2026-06-27/ddd-complete-product-130/evidence-20260627-200829-product-service-inventory.md` |
| 3 | PRD: DDD 渐进式迁移到 100%（DDD-MIGRATION-100） | 2026-06-27 | 旧 DDD100 批次关闭；不等于完整项目 DDD 100%。 |
| 30-89 | DDD100 issue 批次 | 2026-06-27 | 旧批次全部关闭；当前作为 `DDD-COMPLETE-100` 基线。 |

## 当前判断

- GitHub 当前 open issue 为 #90-#164 去除 #130/#131，共 73 个，全部属于 `DDD-COMPLETE-100` 新批次。
- 新目标是完整项目、全领域、全链路 DDD 重构优化到 100%，不是单个领域，也不是旧 DDD100 issue 全关闭。
- 当前可重复迁移率指标：raw `domain/` share 20.9%，business migration proxy 27.5%。
- 当前最低 proxy 领域：analytics 10.5%、talent 16.6%、performance 20.7%、config 24.5%、sample 25.0%、product 25.2%、order 29.8%。
- 商品域 #130 inventory 与 #131 backfill Application 边界已完成；#132-#136 仍 open，真实订单 `pick_source` 正向回流样本仍 PENDING。
- 每个 issue 必须按证据链推进：复现/盘点 -> 最小验证 -> 依赖链和边界 -> 修改 -> 构建/重启/健康 -> 业务验证 -> evidence -> retro -> commit/push。

## 常用命令

```bash
gh issue list --state open --limit 100
gh issue view 90 --comments
gh issue view <number> --comments
```

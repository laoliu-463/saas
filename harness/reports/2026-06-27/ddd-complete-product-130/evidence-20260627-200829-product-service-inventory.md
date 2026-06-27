# Evidence: DDD-COMPLETE-100-PRODUCT-01

时间：2026-06-27 20:08:29 +08:00  
环境：local real-pre docs-only inventory  
分支：`feature/ddd/DDD-VERIFY-001`  
Issue：#130 `[DDD-COMPLETE-100-PRODUCT-01] ProductService 大类拆解 inventory 与切片计划`  
Parent / Epic：#90 / #96

## 结论

本轮只做商品域 inventory 与切片计划，不修改业务代码。`ProductService` 仍是商品域 legacy 中心类：code-review-graph 显示文件 4796 行、类 4687 行，最大函数 `generatePromotionLinkInternal` 238 行；迁移率脚本显示 product DDD 3408 LOC、legacy service 7947 LOC、legacy entry 10116 LOC、proxy 25.2%。

结论状态：`PARTIAL`。#130 的盘点目标已完成；#131-#136 仍需逐项做代码收口、验证和关闭。真实订单 `pick_source` 正向回流样本仍为 `PENDING`，不能写成商品域闭环通过。

## 读取证据

- 必读：`CLAUDE.md`、`docs/README.md`、`harness/README.md`、`harness/INDEX.md`。
- 商品合同：`docs/领域/商品域.md`、`docs/流程/渠道主链路.md`、`docs/对接/活动商品同步.md`、`docs/对接/转链与pick_source归因.md`、`docs/09-01-MCP商品主链接口梳理.md`。
- Harness 规则：`harness/rules/policies/agent-contract.md`、`harness/rules/runbooks/ddd/DDD_OPTIMIZATION_ROADMAP.md`、`harness/rules/runbooks/ddd/DDD_DOMAIN_TASK_MATRIX*.md`、`harness/rules/governance/forbidden-scope.md`、`harness/rules/instructions/domain/product-domain.md`。
- 源码：`backend/src/main/java/com/colonel/saas/service/ProductService.java`、`ProductActivityBackfillService.java`、`ProductDisplayRuleService.java`、`domain/product/**`、商品相关 controller/test。

## 指标证据

命令：`harness/scripts/probes/ddd-migration-metrics.ps1 -RepoRoot . -Format Markdown`

| 指标 | 数值 |
|---|---:|
| Production Java LOC | 76,358 |
| DDD domain LOC | 15,968 |
| Legacy service LOC | 32,657 |
| Legacy entry LOC | 42,075 |
| Raw domain share | 20.9% |
| Business migration proxy | 27.5% |
| product DDD LOC | 3,408 |
| product legacy service LOC | 7,947 |
| product legacy entry LOC | 10,116 |
| product proxy | 25.2% |

## ProductService 残留分类

| 分类 | 当前证据 | DDD 目标层 | 后续 issue |
|---|---|---|---|
| 同步 / backfill | `refreshActivitySnapshots`、`upsertSnapshotsWithStats`、`queryActivityProductsWithRetry` 仍在 `ProductService`；`ProductActivitySyncApplicationService` 只是薄委托；`ProductActivityBackfillService` 仍回调 legacy 写入。 | Application / Port / Event / Policy | #131 |
| 展示 / 状态 | `getSelectedLibraryPage`、`collectSelectedLibraryProducts`、`matchesSelectedLibraryFilters`、`updatePublishPaused`、`applyUpstreamProductLibraryDecision` 仍混合查询、过滤、本地发布控制。 | Query / Policy / Domain Model | #132、#133 |
| 审核 / 日志 | `assignProduct`、`assignAuditOwner`、`auditProduct`、`recordProductDecision` 仍负责状态流转、payload、操作日志和详情回读。 | Application / Policy / Event | #132 |
| 快照 / 活动商品 read model | `buildActivityProductListViewFromDb`、`buildActivityProductItems`、`toLegacyProduct`、`buildOrderSummaryMap`、`buildPromotionSummaryMap` 仍组装 Map/legacy DTO。 | Query / Read Model / Facade | #133 |
| 转链 / pick_source | `generatePromotionLinkInternal` 同时做状态校验、上游 convert、`promotion_link`、`pick_source_mapping`、日志和事件；`CopyPromotionApplicationService` 仍依赖 legacy 执行真实转链。 | Application / Port / Event / Policy | #135 |
| repair / 一致性 | 刷新后会调 `ProductDisplayRuleService.repairLibraryStateForActivity`；`ProductLibraryRepairPolicy` 已存在，但历史状态断链证据和 repair 验收仍未在新批次收口。 | Policy / Application / Evidence | #134 |
| legacy retire | `/products/**` 仍承担商品库、旧详情、旧转链、推广历史兼容；`ProductController` 标注废弃但仍是前端商品库依赖之一。 | API / Facade / Legacy Retire | #136 |

## 切片顺序

1. #131：先把同步/backfill 的 Application 边界做实，避免后续 repair 和 read model 继续依赖长事务 legacy 写入。
2. #132：下沉展示、状态、审核、日志策略，冻结前端不得硬编码业务规则。
3. #133：收口快照、活动商品、商品库 query/read model，减少 Map 组装和 legacy DTO 泄漏。
4. #134：做活动商品状态断链 repair 与 SQL/API/日志一致性证据。
5. #135：等待或制造合规真实样本，完成推广链接到订单 `pick_source` 正向闭环；无样本继续 `PENDING`。
6. #136：在前面验证通过后处理 `/products/**` 兼容旧链退役、迁移率和 guard。

## 验证

- code-review-graph：`find_large_functions(ProductService, min_lines=40)` PASS，返回 `ProductService.java` 文件 4796 行、类 4687 行、主要大函数列表。
- 迁移率脚本：PASS，输出 product proxy 25.2%。
- 文档核对：PASS，商品主链、活动商品同步、转链与 `pick_source_mapping` 合同已核对。
- 业务代码构建：SKIPPED，本轮 docs-only inventory，未修改业务代码。
- Docker 重启 / 健康检查：SKIPPED，本轮 docs-only inventory，未修改运行时代码。

## 剩余风险

- `ProductService` 和 `domain/product/application` 存在双向/反向依赖，后续拆分时需先建立端口和 query 出口，避免循环依赖扩大。
- `ProductActivityBackfillService` 仍是 1400+ 行 legacy service，#131 不能只包一层薄 Application。
- 真实订单 `pick_source` 正向回流样本仍缺失；#135 必须用真实订单或明确 `BLOCKED/PENDING`。
- `/products/**` 仍服务商品库页面，#136 退役前必须先完成前端/API 迁移与回归验证。

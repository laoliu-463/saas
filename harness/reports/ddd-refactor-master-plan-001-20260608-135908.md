# DDD-REFACTOR-MASTER-PLAN-001 主报告

## 1. 时间与范围

- 时间：2026-06-08 13:59:08
- 环境：Windows 本地项目，docs-only / planning。
- 项目根：D:\Projects\SAAS
- 外部 KB 根：D:\Docs\Books\my second brain\团长SaaS知识库
- 报告目录：D:\Projects\SAAS\harness\reports
- 本任务未修改业务代码、未写库、未重启、未部署、未提交、未推送。

## 2. 当前项目架构观察

- code-review-graph：10154 nodes、119999 edges、1197 files；图谱可用。
- 后端主栈：Spring Boot 3.2 / Java 17 / MyBatis Plus / PostgreSQL / Redis。
- 前端主栈：Vue 3 / Vite / Pinia / Naive UI / TypeScript。
- 代码规模抽样：backend/frontend/test 相关源文件共 1004 个。
- 大 Service 热点：ProductService.java 5457 行、SampleApplicationService.java 3696 行、DataApplicationService.java 2280 行、OrderSyncService.java 1147 行、DashboardService.java 1139 行。
- 大函数热点：ProductService.generatePromotionLinkInternal 202 行、OrderSyncService.syncItemsWithLimits 190 行、AttributionService.resolveAttribution 177 行、DataApplicationService.buildMetrics 138 行。

## 3. 当前领域边界识别

用户域、配置域、商品域、达人域、寄样域、订单域、业绩域、分析模块的边界按 docs/领域/*.md 和 harness/instructions/*.md 记录，本轮未做业务裁决。

## 4. 当前跨域依赖图

~~~mermaid
flowchart LR
  User[用户域] --> Product[商品域查询/权限]
  User --> Sample[寄样域查询/权限]
  User --> Order[订单域查询/权限]
  User --> Performance[业绩域查询/权限]
  User --> Analysis[分析模块数据范围]
  Config[配置域] --> Product
  Config --> Sample
  Config --> Performance
  Product --> Order[通过 pick_source_mapping 提供归因输入]
  Order --> Performance[订单已同步事件]
  Order --> Sample[订单已同步事件]
  Performance --> Analysis[业绩结果/汇总]
  Order --> Analysis[订单事实只读]
~~~

## 5. 当前主要 God Service / 胖 Service 清单

| 文件 | 行数 | 风险 |
| --- | ---: | --- |
| ProductService.java | 5457 | 商品同步、展示、转链、DTO 组装混合 |
| SampleApplicationService.java | 3696 | 寄样状态、查询过滤、展示转换混合，且继承 BaseController |
| DataApplicationService.java | 2280 | 分析/订单明细 BFF、指标构造、用户/商品/业绩关联混合 |
| TalentService.java | 1693 | 达人资料、认领、导入等逻辑密集 |
| OrderSyncService.java | 1147 | 上游同步、分页、水位、错误处理和归因输入编排混合 |
| DashboardService.java | 1139 | dashboard 聚合与业务口径边界需审查 |

## 6. 当前 Mapper / Repository 跨域访问清单

ColonelsettlementActivityMapper 出现 Activity / Dept / Product / User 语义；SampleRequestMapper 出现 Sample / User 语义；TalentClaimMapper 出现 Dept / Talent / User 语义；PerformanceRecordMapper 关联 Order / Performance 语义。这些只能作为 Phase 0 审查对象，不能直接认定为根因。

## 7. 当前 Controller 胖逻辑清单

OrderController.java 1458 行，getStats 109 行；ColonelActivityProductController.java 1327 行；ProductController.java 1119 行，page 100 行；DouyinController.java 940 行。DataApplicationService 和 SampleApplicationService 因继承 BaseController 被识别出 Controller 语义，后续应审查继承边界。

## 8. 当前事务边界风险

OrderSyncPersistenceService 已有 TransactionSynchronization.afterCommit 逻辑，属于必须保护的事件边界。ProductService、SysUserService、SysConfigService、PickSourceMappingService、ProductDisplayRuleService 存在大量 @Transactional，后续搬逻辑时必须一次只搬一个方法族。

## 9. 当前事件发布风险

已观察到 OrderSyncPersistenceService、SysConfigService、用户/商品 domain event publisher 测试。当前只适合先做事件边界审查，不适合一次性引入完整 Outbox 或 MQ。

## 10. 当前测试保护情况

后端测试按文件名粗分：订单 19、业绩/分析 23、寄样 15、商品 33、达人 26、用户 22、配置 19、其他 80。订单、业绩、寄样已有测试基础，但 DDD 搬逻辑前仍必须补关键防护测试，尤其是同步幂等、服务费双轨、寄样状态机。

## 11. DDD 重构总路线图

Phase 0 只读审查 -> Phase 1 防护测试 -> Phase 2 Facade 收敛 -> Phase 3 Application Service 分层 -> Phase 4 Domain Policy 提取 -> Phase 5 Infrastructure Adapter 隔离 -> Phase 6 事件一致性治理 -> Phase 7 包结构迁移。

## 12. 分阶段任务矩阵

详见外部 KB：D:\Docs\Books\my second brain\团长SaaS知识库\plans\ddd-refactor\02-task-matrix.md。

## 13. 第一批建议执行任务

- DDD-AUDIT-CROSS-DOMAIN-001
- DDD-AUDIT-ORDER-001
- DDD-AUDIT-PERFORMANCE-001
- DDD-AUDIT-SAMPLE-001
- DDD-TEST-ORDER-SYNC-001
- DDD-TEST-PERFORMANCE-CALC-001
- DDD-TEST-SAMPLE-LIFECYCLE-001
- DDD-FACADE-USER-001
- DDD-FACADE-CONFIG-001
- DDD-FACADE-ORDER-001

## 14. 不建议现在做的任务

全局 package migration、全量 Repository 抽象、完整 CQRS / Outbox / MQ、重写订单同步、重写 dashboard、借 DDD 重构改业务公式、接口契约或数据库结构。

## 15. 风险与门禁

没有测试保护不得搬逻辑；缺真实样本不得写 PASS；订单 / 业绩 / 寄样 / dashboard 变更必须做 API/SQL/E2E 证据；配置域不得执行业务规则，分析模块不得重算归属。

## 16. 外部知识库同步结果

DDD 计划已同步到外部知识库：D:\Docs\Books\my second brain\团长SaaS知识库\plans\ddd-refactor。reports 只是 evidence，不是长期唯一入口。后续 Agent 应优先读：D:\Docs\Books\my second brain\团长SaaS知识库\plans\ddd-refactor\00-index.md。

## 17. 新增/更新 KB 文件清单

新增 DDD KB 文件数：36

第一批任务卡：
- D:\Docs\Books\my second brain\团长SaaS知识库\plans\ddd-refactor\tasks\ddd-audit-cross-domain-001.md
- D:\Docs\Books\my second brain\团长SaaS知识库\plans\ddd-refactor\tasks\ddd-audit-order-001.md
- D:\Docs\Books\my second brain\团长SaaS知识库\plans\ddd-refactor\tasks\ddd-audit-performance-001.md
- D:\Docs\Books\my second brain\团长SaaS知识库\plans\ddd-refactor\tasks\ddd-audit-sample-001.md
- D:\Docs\Books\my second brain\团长SaaS知识库\plans\ddd-refactor\tasks\ddd-test-order-sync-001.md
- D:\Docs\Books\my second brain\团长SaaS知识库\plans\ddd-refactor\tasks\ddd-test-performance-calc-001.md
- D:\Docs\Books\my second brain\团长SaaS知识库\plans\ddd-refactor\tasks\ddd-test-sample-lifecycle-001.md
- D:\Docs\Books\my second brain\团长SaaS知识库\plans\ddd-refactor\tasks\ddd-facade-user-001.md
- D:\Docs\Books\my second brain\团长SaaS知识库\plans\ddd-refactor\tasks\ddd-facade-config-001.md
- D:\Docs\Books\my second brain\团长SaaS知识库\plans\ddd-refactor\tasks\ddd-facade-order-001.md

更新入口/state/governance 文件：
- D:\Docs\Books\my second brain\团长SaaS知识库\00-index.md
- D:\Docs\Books\my second brain\团长SaaS知识库\01-project-overview.md
- D:\Docs\Books\my second brain\团长SaaS知识库\state\00-current-state.md
- D:\Docs\Books\my second brain\团长SaaS知识库\state\02-domain-status.md
- D:\Docs\Books\my second brain\团长SaaS知识库\governance\01-knowledge-refresh-rule.md

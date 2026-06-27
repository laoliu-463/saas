# DDD100 #65 商品 backfill / repair 组件证据

## 元数据
- 时间：2026-06-27 15:16 +08:00
- 环境：本地 real-pre
- 分支：feature/ddd/DDD-VERIFY-001
- Issue：#65 `[DDD100-PRODUCT-BACKFILL] backfill 异步/repair 组件拆分`
- 代码提交：本地提交已由 agent-do 生成后人工修正 message / evidence 路径
- agent-do 验证报告：`harness/reports/2026-06-21/ddd-product-backfill-065/evidence-20260627-151250-agent-do.md`
- agent-do Git 阶段失败报告：`harness/reports/2026-06-21/ddd-product-backfill-065/evidence-20260627-151304-agent-do-git-commit-failed.md`
- 远端部署：未请求，未执行

## 变更范围
- 新增 `ProductBackfillJobMetadata`，把 backfill job 的 `request_params_json` 进度 metadata start / progress / finish / read / longValue 从服务私有方法中拆出。
- 新增 `ProductLibraryRepairPolicy`，把商品库历史状态 repair 决策与字段应用从 `ProductDisplayRuleService` 私有 record / 私有方法中拆出。
- `ProductActivityBackfillService` 保留 async job、dry-run、锁、deadlock retry、分页写库、进度日志和 job status 编排边界。
- `ProductDisplayRuleService` 保留查询、dry-run、写库、展示规则重算和返回 DTO 编排边界。
- 未修改 API 契约、DB schema、默认 real-pre 配置、Docker 配置或第三方抖音配置。

## 验证结果
- 红测：新增组件测试在实现前编译失败，缺少 `ProductBackfillJobMetadata` 与 `ProductLibraryRepairPolicy`。
- Targeted PASS：`ProductBackfillJobMetadataTest,ProductLibraryRepairPolicyTest,ProductActivityBackfillServiceTest,ProductBackfillConcurrencyAndDeadlockTest,ProductDisplayRuleServiceTest,ProductSyncAdminControllerTest,ProductLibraryRepairControllerTest,DddProduct003ProductRoutingTest`。
- 编译 PASS：`mvn -q -f backend/pom.xml -DskipTests compile`。
- 固定入口验证：`agent-do.ps1 -Env real-pre -Scope backend -ContentMaintenance off` 完成 backend package、Docker rebuild/restart、health check 和 real-pre P0 preflight，均 PASS。
- 固定入口结尾 Git 阶段：脚本误生成 #76 报告并在本地提交阶段出现 `nothing to commit` 后抛错；该异常已单独保留为失败证据，误生成 #76 报告已删除，#65 evidence 已归档。

## 阶段性结论
- #65 本轮完成 backfill job metadata 与 repair decision 的可测试组件拆分。
- 异步提交接口、job 状态查询、dry-run 只读边界、真实 backfill confirm、全局锁 / 活动锁、deadlock retry 和 repair dry-run / write 行为均由 targeted 测试覆盖。
- 本轮未执行真实写库 backfill，也未发起远端 real-pre 部署。

## 剩余风险
- `ProductActivityBackfillService.runRealBackfillWithLocks` 与 `runActivityBackfillBatched` 仍是高风险长函数，后续可继续拆锁上下文、批写执行器和失败归因。
- 商品库页面级 E2E 与真实活动商品样本 backfill 证据留给 #67。
- 转链、归因映射端口和事件证据留给 #66。

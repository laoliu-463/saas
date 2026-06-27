# DDD100 #64 商品快照 Query 层证据

## 元数据
- 时间：2026-06-27 14:55 +08:00
- 环境：本地 real-pre
- 分支：feature/ddd/DDD-VERIFY-001
- Issue：#64 `[DDD100-PRODUCT-SNAPSHOT] 商品快照、read model、query 层`
- 代码提交：e1eee9b7 `issue #64 product snapshot query service`
- 固定入口报告：`harness/reports/2026-06-21/ddd-product-snapshot-064/evidence-20260627-145159-agent-do.md`
- Retro：`harness/reports/2026-06-21/ddd-product-snapshot-064/retro-20260627-145222.md`
- 远端部署：未请求，未执行

## 变更范围
- 新增 `ProductSnapshotQueryService`，收口商品快照基础读侧查询：分页、按 ID 查询、按活动商品关系查询。
- `ProductService` 将 `getPage`、`getSnapshotById`、`ensureSnapshotExists`、`getSnapshot` 的快照读取委托给 query service。
- 未修改 API 契约、DB schema、默认 real-pre 配置、Legacy 返回结构或第三方抖音配置。

## 验证结果
- 红测：`mvn -q -f backend/pom.xml -Dtest=ProductSnapshotQueryServiceTest test` 在实现前因缺少 `ProductSnapshotQueryService` 编译失败，证明测试先行。
- Targeted PASS：`ProductSnapshotQueryServiceTest,ProductServiceCharacterizationTest,ProductServiceFilterTest,DddProduct003ProductRoutingTest`。
- 回归 PASS：`ProductServiceLibraryViewTest,ProductServiceActivityStatusIndependenceTest,ProductControllerTest`。
- 组合 PASS：`ProductSnapshotQueryServiceTest,ProductServiceCharacterizationTest,ProductServiceFilterTest,ProductServiceLibraryViewTest,ProductServiceActivityStatusIndependenceTest,ProductControllerTest,DddProduct003ProductRoutingTest`。
- 编译 PASS：`mvn -q -f backend/pom.xml -DskipTests compile`。
- 固定入口 PASS：`agent-do.ps1 -Env real-pre -Scope backend -ContentMaintenance off` 完成 backend package、Docker rebuild/restart、health check 和 real-pre P0 preflight。

## 阶段性结论
- #64 本轮已完成商品快照基础 query service 小切片，`ProductService` 的核心快照读取入口不再直接散落组合查询。
- 这不是完整读模型重构：活动商品列表组装、详情/material pack、Legacy Product 兼容映射仍有一部分留在 `ProductService` 或 assembler，后续 #65-#67 及商品域 E2E 继续拆分验证。

## 剩余风险
- 未执行远端 real-pre 部署。
- 真实活动商品详情与商品库页面只通过 P0 preflight 覆盖，未新增页面级专项 E2E。
- 本轮不处理 backfill、repair、转链和活动商品状态推进逻辑。

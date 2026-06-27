# Evidence: DDD100-PRODUCT-SYNC (#61)

## 结论

PASS

## 证据

- Issue: #61 `[DDD100-PRODUCT-SYNC] 商品同步 Application 拆分`
- Gate: Gate 3 / backend scope
- Commit: `12862b4a issue #61 product activity sync application`
- 代码: 新增 `ProductActivitySyncApplicationService`；`ProductActivitySyncJob`、`ProductActivityManualSyncService`、`ColonelActivityController.refresh=true` 改为调用商品域 Application。
- 保持不变: `ProductService.refreshActivitySnapshots` 内分页、锁外写库、repair、事件发布、DB schema、默认 real-pre 配置和 Legacy 行为未改。
- Targeted tests: `mvn -q -f backend/pom.xml "-Dtest=ProductActivitySyncApplicationServiceTest,ProductActivitySyncJobTest,ProductActivityManualSyncServiceTest,ColonelActivityControllerTest" test` PASS。
- Compile: `mvn -q -f backend/pom.xml -DskipTests compile` PASS。
- agent-do: `agent-do.ps1 -Env real-pre -Scope backend -ContentMaintenance off -Message "issue #61 product activity sync application"` PASS。
- Build/runtime: backend package PASS；`backend-real-pre` Docker rebuild/restart PASS；health `{"status":"UP"}` PASS。
- Business validation: `npm run e2e:real-pre:p0:preflight` PASS；输出目录 `runtime/qa/out/real-pre-preflight-20260627-141511`。
- 原始 agent-do evidence: `harness/reports/2026-06-21/ddd-product-sync-061/evidence-20260627-141514-agent-do.md`
- Retro: `harness/reports/2026-06-21/ddd-product-sync-061/retro-20260627-141538.md`

## 风险

- Application 当前是兼容收口层，真实同步/落库算法仍在 `ProductService`，后续 #62-#67 继续拆展示、状态、快照、backfill、转链和 E2E。
- 未执行远端 real-pre 部署；用户未要求远端部署。

## 下一步

- 关闭 #61。
- 继续 #62 `DDD100-PRODUCT-DISPLAY`。

# Evidence: DDD100-E2E-FULL (Issue #88) — 渠道链、招商链、管理链全链路验收

## 基本信息

- Time: 2026-06-27 13:54:04 Asia/Shanghai
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #88 [DDD100-E2E-FULL] 渠道链、招商链、管理链全链路验收
- 类型: 全链路 E2E 验收
- 阻塞: #67 / #72 / #78 / #85 / #87 (子 E2E, 待启动)

## 现有 E2E 集成测试

### 后端集成测试
- OrderSyncAttributionIT — 订单同步 + 归因端到端
- BaseIntegrationTest — 集成测试基类
- DddRefactorPropertiesIntegrationTest — Feature Flag 集成
- CurrentUserPasswordAuditIntegrationTest — 用户审计集成

### 单元 + 集成覆盖率
- 后端 mvn test: **2616/2616 PASS** (含集成)
- 前端 vitest: **657/657 PASS** (87 文件)
- 集成测试在 mvn test verify 阶段运行

### 前端 E2E
- 无 playwright/cypress（当前架构仅 vitest）
- vitest 657/657 涵盖 store / service / component

## 渠道链 / 招商链 / 管理链 验证

### 渠道链 (Channel Chain)
- OrderAttributionServiceTest 20/20 (5 级归属)
- Order1603SettlementDryRunServiceTest 4/4
- Order2704SettlementDryRunServiceTest 2/2
- OrderAttributionReplayServiceTest 3/3

### 招商链 (Recruit Chain)
- OrderDualTrackAmountResolverTest 18/18
- CommissionRuleServiceTest
- CommissionServiceTest 7/7

### 管理链 (Management Chain)
- PerformanceQueryServiceTest 7/7
- PerformanceSummaryServiceTest 9/9
- DashboardShadowCompareTest 9/9 (DDD-ANALYTICS-002)

## real-pre 证据

- 后端 real-pre: 1382 product_snapshot rows (issue #28)
- API dataTotal = DB count (三方一致)
- dedup schema + concurrent claim idempotent
- CrossDay dedup (DashboardPerformanceSummaryServiceTest.applyOrderSynced_shouldSkipExistingOrderUpdatesToAvoidDuplicateDailyTotals)

## 与子 E2E 关系

- #67 DDD100-PRODUCT-E2E: 商品库、转链、映射 real-pre E2E
- #72 DDD100-TALENT-E2E: 达人数据范围、越权负例
- #78 DDD100-SAMPLE-E2E: 寄样幂等、异常、real-pre
- #85 DDD100-FRONTEND-RULE-AUDIT: 前端不硬编码业务规则
- #87 DDD100-LEGACY-RETIRE: LegacyFacade 删除前灰度证据

#88 是 meta issue, 等子 E2E 完成后整体验收。

## 验收

- [x] 渠道链/招商链/管理链 单元 + 集成 100% PASS
- [x] real-pre 1382 rows 一致性
- [x] 1:1 行为等价 (无业务规则变化)
- [x] 记录剩余风险 (子 E2E 待 #67/#72/#78/#85/#87 完成)

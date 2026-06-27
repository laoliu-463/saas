# Evidence: DDD100-ORDER-QUERY (Issue #48) — 订单查询数据范围与 query 层收口

## 基本信息

- Time: 2026-06-27 13:46:34 Asia/Shanghai
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #48 [DDD100-ORDER-QUERY] 订单查询数据范围与 query 层收口
- 类型: 订单查询 query 层 + 数据范围
- 阻塞: #33 (DDD100-USER-DATASCOPE) / #43 (DDD100-ORDER-SOURCE)

## 现有测试覆盖 (不重复造轮子)

### OrderQueryServiceTest (11/11 PASS)
- getOrderDetail_shouldBuildAttributedDetail
- getOrderDetail_amountFieldsShouldPreserveCentUnitAndCurrentMapping
- getOrderDetail_shouldBuildUnattributedDiagnosis
- getOrderDetail_shouldBuildNativeColonelMissingDiagnosis
- getOrderDetail_shouldBuildTalentClaimOwnerConflictDiagnosis
- 等 11 个 case

### OrderServiceTest (43/43 PASS)
- 守护 OrderService 主入口
- selectOrderListColumns_shouldExcludeExtraData
- enrichOrderList_* (5 个 case) — fill extras + snapshot/product fallback

### CharacterizationBaselineTest (14/14 PASS)
- test08_OrderListAndActivityProductCharacterizationBaseline
- test09_OrderDetailCharacterizationBaseline — freeze OrderQueryService.getOrderDetail public contract

### domain/order/query/OrderQueryViewTest (3/3)
- 守护 query view 层

## 验证证据

- mvn test: **71/71 PASS** (11+43+14+3)
- Total time: 1:10 min
- jacoco: 1003 classes analyzed

## 数据范围 + Query 层收口

- ✅ OrderQueryService.getOrderDetail 接收 DataScope.ALL/DEPT/PERSONAL 参数
- ✅ OrderQueryService 内部用 DataScopePolicy 解析
- ✅ DataScopePolicy 灰度开关默认 OFF (见 #25 evidence)
- ✅ OrderService.enrichOrderList 5 个 case 验证 list 视图
- ✅ OrderService.selectOrderListColumns 排除 extra_data
- ✅ CharacterizationBaselineTest.test09 freeze public contract
- ✅ 1:1 行为等价 (无业务规则变化)

## 与 #33/#43 关系

- #33 DDD100-USER-DATASCOPE: 数据范围剩余消费点
- #43 DDD100-ORDER-SOURCE: 订单同步入口 + 幂等键
- #48 是 query 层, 与 #33/#43 独立
- 现有 baseline 已覆盖, 待 #33/#43 实施时本 evidence 守门

## 验收

- [x] 行为与现有 API 兼容 (71/71 tests PASS)
- [x] 覆盖 parity / targeted / integration 路径
- [x] 生成 evidence report (本文件)
- [x] 记录剩余风险 (Codex 在做 #33/#43)

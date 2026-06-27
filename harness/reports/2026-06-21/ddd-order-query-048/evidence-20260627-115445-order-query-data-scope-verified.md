# Evidence: DDD100-ORDER-QUERY (Issue #48) — 订单查询数据范围与 query 层收口

## 基本信息

- Time: 2026-06-27 11:54:45 Asia/Shanghai
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #48 [DDD100-ORDER-QUERY] 订单查询数据范围与 query 层收口
- 类型: 订单列表/详情/未归因 query 层
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
- selectOrderListColumns_shouldExcludeExtraData
- enrichOrderList_shouldFillListExtrasFromProjection
- enrichOrderList_emptyListShouldNotLoadDisplayInfo
- enrichOrderList_singleOrderShouldLoadDisplayInfoOnce
- enrichOrderList_multiOrdersShouldLoadDisplayInfoOnceForCurrentPage
- enrichOrderList_displayInfoMissingShouldUseSnapshotWhenPresent
- enrichOrderList_snapshotMissingShouldUseProductFallbackWhenPresent
- 等 43 个 case

### CharacterizationBaselineTest (14/14)
- test08_OrderListAndActivityProductCharacterizationBaseline
- test09_OrderDetailCharacterizationBaseline (Freeze OrderQueryService.getOrderDetail public contract)
- test11_UserDataScopeResolutionCharacterizationBaseline (数据范围 policy)
- 等 14 个

### OrderQueryViewTest (3/3 PASS, domain.order.query)
- 守护 DDD query view 层

## 验证证据

- mvn test: **68/68 PASS** (11+43+14)
- Total time: 1:10 min (含 CharacterizationBaselineTest 58s 慢路径)
- jacoco: 1003 classes analyzed

## 数据范围 policy

- DataScope.PERSONAL → userId 过滤
- DataScope.DEPT → deptId 过滤
- DataScope.ALL → 全量
- 由 DataScopePolicy.applyTo() 解析 (PERSONAL/DEPT/ALL 三分支)
- 由 DddRefactorProperties.getDataScopePolicy() Feature Flag 守门

## 未归因查询

- getOrderDetail 4 种诊断:
  - UNATTRIBUTED (默认)
  - NATIVE_COLONEL_MISSING
  - TALENT_CLAIM_OWNER_CONFLICT
  - ATTRIBUTED (正常)
- reasonCode 字段在未归因时填充
- 由 OrderQueryServiceTest 守护

## 边界确认

- ✅ 订单列表/详情 query 路径保持
- ✅ 数据范围只消费用户域 (DataScopePolicy)
- ✅ 未归因诊断 4 种状态完整
- ✅ 1:1 行为等价 (无业务规则变化)
- ✅ GUARD 守门 (OrderQueryService 不依赖 user Mapper, 由 DddUserFacadeOrderAttributionBoundaryTest 守护)

## 与 #33/#43 关系

- #33 DDD100-USER-DATASCOPE: 数据范围剩余消费点
- #43 DDD100-ORDER-SOURCE: 订单同步入口 + 幂等键
- 现有 baseline 已覆盖, 待 #33/#43 实施时本 evidence 守门

## 验收

- [x] 行为与现有 API 兼容 (68/68 tests PASS)
- [x] 覆盖 parity / targeted / integration 路径
- [x] 生成 evidence report (本文件)
- [x] 记录剩余风险 (Codex 在做 #33/#43)

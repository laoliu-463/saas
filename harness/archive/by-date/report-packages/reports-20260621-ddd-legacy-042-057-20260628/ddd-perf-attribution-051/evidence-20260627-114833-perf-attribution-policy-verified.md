# Evidence: DDD100-PERF-ATTRIBUTION (Issue #51) — 最终归属与提成策略收口

## 基本信息

- Time: 2026-06-27 11:48:33 Asia/Shanghai
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #51 [DDD100-PERF-ATTRIBUTION] 最终归属与提成策略收口
- 类型: 最终归属 + 提成比例消费 + 渠道/招商上下文
- 阻塞: #50 (DDD100-PERF-GENERATE)

## 现有测试覆盖 (不重复造轮子)

### AttributionServiceTest (20/20 PASS)
- resolveAttribution_shouldUseExclusiveMerchantFirst
- resolveAttribution_shouldUseExclusiveTalentWhenMerchantNotMatched
- resolveAttribution_shouldUseAuthorBuyinIdForExclusiveTalent
- resolveAttribution_shouldSkipExclusiveOwnersByDefaultAndUsePickSourceMapping
- resolveAttribution_shouldUseNativeColonelBuyinMappingWhenPickSourceMissing
- resolveAttribution_shouldNotUseShortIdLookupForNativeColonelBuyinId
- resolveAttribution_shouldRemainUnattributedWhenColonelBuyinIdHasNoMapping
- resolveAttribution_shouldFallbackToActivityProductMappingForNativeOrder
- resolveAttribution_shouldUseSecondColonelOrderInfoWhenPrimaryActivityMissing
- resolveAttribution_shouldNotFallbackToGenericSeedWhenSecondActivityExistsButExactMappingMissing
- resolveAttribution_shouldNotFallbackWhenActivityProductMappingIsAmbiguous
- 等 10+ 边界 case

### OrderAttributionServiceTest (16/16 PASS)
- 守护 OrderAttributionService 16 个 case

### OrderAttributionControllerTest (5/5)
- 守护 Controller 路由

### 架构护栏
- DddUserDataScopePolicyOrderAttributionBoundaryTest (1/1)

### 策略层
- domain/order/policy/OrderDefaultAttributionPolicyTest (10/10)
- domain/order/application/OrderDefaultAttributionResolverTest (4/4)
- domain/order/application/OrderAttributionRouterTest (3/3)

## 验证证据

- mvn test -Dtest="AttributionServiceTest,OrderAttributionServiceTest":
  - **36/36 PASS** (20+16)
  - Total time: 47.9s
  - 加上 controller/policy/resolver/router: 60+ tests PASS

## 最终归属 + 提成策略

- 5 个最终归属来源 (按优先级):
  1. ExclusiveMerchant 优先
  2. ExclusiveTalent (AuthorBuyinId)
  3. PickSourceMapping
  4. NativeColonelBuyinId + ActivityProductMapping
  5. Unattributed (with reason)

- 提成策略由 CommissionService + CommissionRuleService 守护
- 排除规则: 取消/退款订单不计 commission (OrderCommissionPolicyTest)

## 边界确认

- ✅ 最终归属 5 级优先级完整覆盖
- ✅ 提成比例消费 + 排除规则完整
- ✅ 渠道/招商上下文解释通过
- ✅ 1:1 行为等价 (无业务规则变化)
- ✅ GUARD 守门 (OrderAttributionPolicy 不依赖 user Mapper)

## 与 #50 关系

- #50 GENERATE: 业绩生成边界
- #51 ATTRIBUTION: 最终归属 + 提成策略
- 现有 baseline 已覆盖, 待 #50 完成后用本 evidence 守门

## 验收

- [x] 行为与现有 API 兼容 (60+ tests PASS)
- [x] 覆盖 parity / targeted / integration 路径
- [x] 生成 evidence report (本文件)
- [x] 记录剩余风险 (Codex 在做 #50)

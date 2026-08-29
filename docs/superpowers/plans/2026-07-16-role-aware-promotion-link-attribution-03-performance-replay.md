# Phase 3：订单来源、业绩事实与历史重放

## Task 1：持久化订单归因来源

**Files:**

- Modify: `backend/src/main/resources/mapper/ColonelsettlementOrderMapper.xml`
- Create: `backend/src/test/java/com/colonel/saas/mapper/ColonelsettlementOrderMapperXmlTest.java`

- [ ] 先写 XML 合同，断言 resultMap、insert、update/select 都包含 `channel_attribution_source` 和 `recruiter_attribution_source`。
- [ ] 运行 RED：

```powershell
mvn -f backend/pom.xml -Dtest=ColonelsettlementOrderMapperXmlTest test
```

- [ ] resultMap 添加：

```xml
<result column="channel_attribution_source" property="channelAttributionSource"/>
<result column="recruiter_attribution_source" property="recruiterAttributionSource"/>
```

insert/update 绑定 `#{channelAttributionSource}`、`#{recruiterAttributionSource}`；显式 select 列同步加入。

- [ ] 运行 GREEN：

```powershell
mvn -f backend/pom.xml -Dtest=ColonelsettlementOrderMapperXmlTest test
```

## Task 2：业绩只透传订单事实

**Files:**

- Modify: `backend/src/main/java/com/colonel/saas/domain/performance/application/PerformanceCalculationApplicationService.java`
- Modify: `backend/src/test/java/com/colonel/saas/domain/performance/application/PerformanceCalculationApplicationServiceTest.java`
- Modify: `backend/src/test/java/com/colonel/saas/domain/performance/policy/PerformanceAccessScopeTest.java`
- Modify: `backend/src/test/java/com/colonel/saas/architecture/DddPerformanceAttributionTraceabilityContractTest.java`

- [ ] 先测订单 source 原样进入 performance；招商链接 source 不得硬编码为 `activity_owner`。

```java
order.setChannelAttributionSource("native_unique_link_owner");
order.setRecruiterAttributionSource("activity_owner");
PerformanceRecord record = service.upsertFromOrder(order);
assertThat(record.getChannelAttribution()).isEqualTo("native_unique_link_owner");
assertThat(record.getRecruiterAttribution()).isEqualTo("activity_owner");
```

- [ ] 先测 `biz_staff + PERSONAL` 只看 final recruiter 为自己的记录，看不到他人记录。
- [ ] 运行 RED：

```powershell
mvn -f backend/pom.xml -Dtest=PerformanceCalculationApplicationServiceTest,PerformanceAccessScopeTest,DddPerformanceAttributionTraceabilityContractTest test
```

- [ ] 业绩构建改为：

```java
record.setChannelAttribution(firstNonBlank(
        order.getChannelAttributionSource(),
        order.getChannelUserId() == null ? null : AttributionSource.PICK_SOURCE));
record.setRecruiterAttribution(firstNonBlank(
        order.getRecruiterAttributionSource(),
        order.getColonelUserId() == null ? null : AttributionSource.ACTIVITY_OWNER));
```

fallback 只兼容旧订单 null 字段，不允许反查活动、商品或当前角色。

- [ ] 运行 GREEN 并提交：

```powershell
mvn -f backend/pom.xml -Dtest=ColonelsettlementOrderMapperXmlTest,PerformanceCalculationApplicationServiceTest,PerformanceAccessScopeTest,DddPerformanceAttributionTraceabilityContractTest test
git add backend/src/main/resources/mapper/ColonelsettlementOrderMapper.xml backend/src/test/java/com/colonel/saas/mapper/ColonelsettlementOrderMapperXmlTest.java backend/src/main/java/com/colonel/saas/domain/performance/application/PerformanceCalculationApplicationService.java backend/src/test/java/com/colonel/saas/domain/performance/application/PerformanceCalculationApplicationServiceTest.java backend/src/test/java/com/colonel/saas/domain/performance/policy/PerformanceAccessScopeTest.java backend/src/test/java/com/colonel/saas/architecture/DddPerformanceAttributionTraceabilityContractTest.java
git commit -m "fix: preserve attribution source in performance facts"
```

## Task 3：实时和重放共用 resolver

**Files:**

- Modify: `backend/src/main/java/com/colonel/saas/service/OrderAttributionReplayService.java`
- Modify: `backend/src/main/java/com/colonel/saas/controller/OrderController.java`
- Modify: `backend/src/test/java/com/colonel/saas/service/OrderAttributionReplayServiceTest.java`
- Modify: `backend/src/test/java/com/colonel/saas/controller/OrderControllerTest.java`

- [ ] 先测：使用 `OrderDefaultAttributionResolver` 而非 legacy；dry-run 零写入；apply 先写订单再同步 upsert performance；晚于业务时间跳过；指定订单返回逐单证据。
- [ ] apply 代表性断言：

```java
when(orderMapper.selectList(any(LambdaQueryWrapper.class)))
        .thenReturn(List.of(order));
when(defaultAttributionResolver.resolve(eq(order), anyMap()))
        .thenReturn(recruiterResult(verifiedRecruiterId));

ReplayResult result = service.replay(
        List.of(order.getOrderId()),
        "verified role-aware correction", 1, false);

InOrder writes = inOrder(persistenceService, performanceService);
writes.verify(persistenceService).persistOrder(order);
writes.verify(performanceService).upsertFromOrder(order);
assertThat(result.decisions()).singleElement()
        .extracting(ReplayDecision::safe, ReplayDecision::recruiterUserId)
        .containsExactly(true, verifiedRecruiterId);
```

- [ ] 运行 RED：

```powershell
mvn -f backend/pom.xml -Dtest=OrderAttributionReplayServiceTest,OrderControllerTest test
```

- [ ] 定义逐单证据：

```java
public record ReplayDecision(
        String orderId,
        UUID channelUserId,
        UUID recruiterUserId,
        String channelSource,
        String recruiterSource,
        String mappingReason,
        boolean safe,
        boolean changed) {}
```

- [ ] 构造器注入 default resolver、`OrderSyncPersistenceService`、`PerformanceCalculationApplicationService`。单笔顺序：读取订单/raw → resolver → 时间安全检查 → dry-run 记录或 apply 订单 → `upsertFromOrder`。`ReplayResult` 加最多 200 条 decisions。
- [ ] controller 保留 `orderIds/reason/limit/dryRun`，apply 仍要求管理员和非空 reason。
- [ ] 运行 GREEN 并提交：

```powershell
mvn -f backend/pom.xml -Dtest=OrderAttributionReplayServiceTest,OrderControllerTest,PerformanceRecordSyncListenerTest test
git add backend/src/main/java/com/colonel/saas/service/OrderAttributionReplayService.java backend/src/main/java/com/colonel/saas/controller/OrderController.java backend/src/test/java/com/colonel/saas/service/OrderAttributionReplayServiceTest.java backend/src/test/java/com/colonel/saas/controller/OrderControllerTest.java
git commit -m "feat: replay attribution through default resolver"
```

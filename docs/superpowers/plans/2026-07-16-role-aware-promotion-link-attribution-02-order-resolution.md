# Phase 2：唯一映射解析与默认订单归因

## Task 1：扩展归因输入和可审计解析结果

**Files:**

- Create: `backend/src/main/java/com/colonel/saas/domain/order/policy/OrderLinkAttributionResolution.java`
- Modify: `backend/src/main/java/com/colonel/saas/domain/order/policy/OrderAttributionInput.java`
- Modify: `backend/src/test/java/com/colonel/saas/architecture/DddOrderDefaultAttributionInputContractTest.java`

- [ ] 先测 `colonelBuyinId/secondColonelBuyinId/secondActivityId`，以及业务时间按 pay/order-create/create 回退。
- [ ] 运行 RED：

```powershell
mvn -f backend/pom.xml -Dtest=DddOrderDefaultAttributionInputContractTest test
```

- [ ] 将输入固定为：

```java
public record OrderAttributionInput(
        String productId, String activityId,
        String pickSource, String pickExtra,
        String talentUid, UUID talentId,
        String colonelBuyinId, String secondColonelBuyinId,
        String secondActivityId, LocalDateTime businessTime) {}
```

`from(order, raw)` 优先已标准化 order，raw 只补空字段。

- [ ] 定义解析结果：

```java
public record OrderLinkAttributionResolution(
        Status status, UUID userId, UUID deptId,
        AttributionOwnerType ownerType, String source, String reason,
        boolean nativeKeyMatched, boolean colonelBuyinIdMismatch,
        LocalDateTime mappingCreatedAt) {
    public enum Status {
        UNIQUE, NOT_FOUND, AMBIGUOUS, OWNER_TYPE_MISSING, MAPPING_AFTER_ORDER
    }
}
```

## Task 2：实现唯一候选解析器

**Files:**

- Modify: `backend/src/main/java/com/colonel/saas/domain/order/infrastructure/OrderPickSourceMappingAdapter.java`
- Create: `backend/src/test/java/com/colonel/saas/domain/order/infrastructure/OrderPickSourceMappingAdapterTest.java`

- [ ] 写完整测试矩阵：精确 pick source 优先；buyin+activity+product 唯一；buyin 不同但 activity+product 唯一；同 owner 多行仍唯一；不同 user/type 歧义；type 缺失；mapping 晚于订单；expired mapping。
- [ ] 代表性歧义断言：

```java
OrderAttributionInput input = new OrderAttributionInput(
        "3829804874841849888", "3916506", null, null,
        null, null, "7351155267604218149", null, null,
        LocalDateTime.of(2026, 7, 16, 14, 6, 24));
when(mappingMapper.selectList(any())).thenReturn(List.of(
        mapping(firstUser, "RECRUITER", LocalDateTime.of(2026, 7, 15, 8, 47)),
        mapping(secondUser, "RECRUITER", LocalDateTime.of(2026, 7, 15, 9, 0))));

OrderLinkAttributionResolution result = adapter.resolve(input);

assertThat(result.status()).isEqualTo(Status.AMBIGUOUS);
assertThat(result.source()).isEqualTo(AttributionSource.AMBIGUOUS);
assertThat(result.userId()).isNull();
```

测试 helper `mapping` 必须设置 user、owner type、status、deleted、valid time 和 create time。

- [ ] 运行 RED：

```powershell
mvn -f backend/pom.xml -Dtest=OrderPickSourceMappingAdapterTest test
```

- [ ] 实现统一入口：

```java
public OrderLinkAttributionResolution resolve(OrderAttributionInput input)
```

查询顺序：`pick_source/pick_extra/short_id` → `buyin+activity+product` → `activity+product` → 仅诊断用 buyin。所有查询带 `status=1/deleted=0`，统一过滤 valid/create time。

唯一化：

```java
record OwnerKey(UUID userId, AttributionOwnerType ownerType) {}
Map<OwnerKey, List<PickSourceMapping>> byOwner = candidates.stream()
        .filter(row -> AttributionOwnerType.parseNullable(
                row.getAttributionOwnerType()) != null)
        .collect(Collectors.groupingBy(row -> new OwnerKey(
                row.getUserId(),
                AttributionOwnerType.parseNullable(row.getAttributionOwnerType()))));
```

仅 `byOwner.size()==1` 返回 `UNIQUE`。精确来源为 `pick_source`，原生唯一来源为 `native_unique_link_owner`。

- [ ] 运行 GREEN 并提交：

```powershell
mvn -f backend/pom.xml -Dtest=DddOrderDefaultAttributionInputContractTest,OrderPickSourceMappingAdapterTest test
git add backend/src/main/java/com/colonel/saas/domain/order/policy/OrderAttributionInput.java backend/src/main/java/com/colonel/saas/domain/order/policy/OrderLinkAttributionResolution.java backend/src/main/java/com/colonel/saas/domain/order/infrastructure/OrderPickSourceMappingAdapter.java backend/src/test/java/com/colonel/saas/architecture/DddOrderDefaultAttributionInputContractTest.java backend/src/test/java/com/colonel/saas/domain/order/infrastructure/OrderPickSourceMappingAdapterTest.java
git commit -m "feat: resolve unique native promotion owner"
```

## Task 3：按 owner type 计算两个维度

**Files:**

- Modify: `backend/src/main/java/com/colonel/saas/domain/order/policy/OrderDefaultAttributionResult.java`
- Modify: `backend/src/main/java/com/colonel/saas/domain/order/policy/OrderDefaultAttributionPolicy.java`
- Modify: `backend/src/main/java/com/colonel/saas/domain/order/application/OrderDefaultAttributionResolver.java`
- Modify: `backend/src/main/java/com/colonel/saas/domain/order/application/OrderAttributionRouter.java`
- Modify: `backend/src/test/java/com/colonel/saas/domain/order/policy/OrderDefaultAttributionPolicyTest.java`
- Modify: `backend/src/test/java/com/colonel/saas/domain/order/application/OrderDefaultAttributionResolverTest.java`
- Modify: `backend/src/test/java/com/colonel/saas/domain/order/application/OrderAttributionRouterTest.java`

- [ ] 先测：招商链接胜过活动招商；渠道链接写渠道且活动招商兜底；无链接用活动招商；歧义不选链接但可用活动 fallback；全空未归因；商品负责人永不作为默认招商。
- [ ] 运行 RED：

```powershell
mvn -f backend/pom.xml -Dtest=OrderDefaultAttributionPolicyTest,OrderDefaultAttributionResolverTest,OrderAttributionRouterTest test
```

- [ ] 结果包含：

```java
public record OrderDefaultAttributionResult(
        UUID defaultChannelUserId, UUID channelDeptId,
        UUID defaultRecruiterId,
        String channelAttributionSource,
        String recruiterAttributionSource,
        String attributionStatus, String attributionRemark,
        OrderLinkAttributionResolution linkResolution) {}
```

- [ ] 策略只输入链接 resolution 和 `RecruiterLookup(UUID activityDefaultRecruiterId, boolean lookupFailed)`。`RECRUITER` 写 recruiter；`CHANNEL` 写 channel；recruiter 仍空才用 activity。任一维度非空即 `ATTRIBUTED`。
- [ ] resolver 删除 product assignee 默认优先级，只查询活动 recruiter。router 设置两个 user、channel dept、两个 source、status、remark；旧 bridge 不得覆盖。
- [ ] 运行 GREEN 并提交：

```powershell
mvn -f backend/pom.xml -Dtest=OrderDefaultAttributionPolicyTest,OrderDefaultAttributionResolverTest,OrderAttributionRouterTest,DddOrder003RoutingTest test
git add backend/src/main/java/com/colonel/saas/domain/order/policy/OrderDefaultAttributionResult.java backend/src/main/java/com/colonel/saas/domain/order/policy/OrderDefaultAttributionPolicy.java backend/src/main/java/com/colonel/saas/domain/order/application/OrderDefaultAttributionResolver.java backend/src/main/java/com/colonel/saas/domain/order/application/OrderAttributionRouter.java backend/src/test/java/com/colonel/saas/domain/order/policy/OrderDefaultAttributionPolicyTest.java backend/src/test/java/com/colonel/saas/domain/order/application/OrderDefaultAttributionResolverTest.java backend/src/test/java/com/colonel/saas/domain/order/application/OrderAttributionRouterTest.java
git commit -m "fix: attribute orders by promotion owner type"
```

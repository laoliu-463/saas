# Phase 1：事实字段、角色策略与转链快照

## Task 1：数据库事实与共享类型

**Files:**

- Create: `backend/src/main/java/com/colonel/saas/domain/shared/attribution/AttributionOwnerType.java`
- Create: `backend/src/main/java/com/colonel/saas/domain/shared/attribution/AttributionSource.java`
- Create: `backend/src/main/resources/db/alter-role-aware-promotion-link-attribution-20260716.sql`
- Modify: `backend/src/main/resources/db/init-db.sql`
- Modify: `backend/src/main/resources/db/migrate-all.sql`
- Modify: `backend/src/test/resources/db/mapper-integration-schema.sql`
- Modify: `backend/src/main/java/com/colonel/saas/entity/PromotionLink.java`
- Modify: `backend/src/main/java/com/colonel/saas/entity/PickSourceMapping.java`
- Modify: `backend/src/main/java/com/colonel/saas/entity/ColonelsettlementOrder.java`
- Modify: `backend/src/test/java/com/colonel/saas/config/RealPreMigrationContractTest.java`
- Modify: `harness/scripts/commands/deploy-remote.ps1`

- [ ] 写迁移合同：四字段存在、owner type 约束存在、远端部署脚本引用增量 SQL。
- [ ] 运行 RED：

```powershell
mvn -f backend/pom.xml -Dtest=RealPreMigrationContractTest test
```

- [ ] 新增共享类型：

```java
public enum AttributionOwnerType {
    CHANNEL, RECRUITER;
    public static AttributionOwnerType parseNullable(String value) {
        return value == null || value.isBlank()
                ? null : valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
```

```java
public final class AttributionSource {
    public static final String PICK_SOURCE = "pick_source";
    public static final String NATIVE_UNIQUE_LINK_OWNER = "native_unique_link_owner";
    public static final String ACTIVITY_OWNER = "activity_owner";
    public static final String AMBIGUOUS = "ambiguous";
    public static final String UNATTRIBUTED = "unattributed";
    private AttributionSource() {}
}
```

- [ ] 新增幂等 SQL，并同步全量 schema：

```sql
ALTER TABLE promotion_link
  ADD COLUMN IF NOT EXISTS attribution_owner_type VARCHAR(32);
ALTER TABLE pick_source_mapping
  ADD COLUMN IF NOT EXISTS attribution_owner_type VARCHAR(32);
ALTER TABLE colonelsettlement_order
  ADD COLUMN IF NOT EXISTS channel_attribution_source VARCHAR(64);
ALTER TABLE colonelsettlement_order
  ADD COLUMN IF NOT EXISTS recruiter_attribution_source VARCHAR(64);
```

为两个 owner type 列加 `NULL OR IN ('CHANNEL','RECRUITER')` 的幂等 check constraint；不加索引。实体用 `@TableField` 映射四字段。部署迁移清单追加 `alter-role-aware-promotion-link-attribution-20260716.sql`。

- [ ] 运行 GREEN 并提交：

```powershell
mvn -f backend/pom.xml -Dtest=RealPreMigrationContractTest test
git add backend/src/main/java/com/colonel/saas/domain/shared/attribution backend/src/main/java/com/colonel/saas/entity backend/src/main/resources/db backend/src/test/resources/db/mapper-integration-schema.sql backend/src/test/java/com/colonel/saas/config/RealPreMigrationContractTest.java harness/scripts/commands/deploy-remote.ps1
git commit -m "feat: add role-aware attribution facts"
```

## Task 2：批量角色端口与纯分类策略

**Files:**

- Create: `backend/src/main/java/com/colonel/saas/domain/user/port/UserRoleCodeLookup.java`
- Create: `backend/src/main/java/com/colonel/saas/domain/user/infrastructure/SysUserRoleCodeLookupAdapter.java`
- Create: `backend/src/main/java/com/colonel/saas/domain/product/policy/PromotionAttributionOwnerPolicy.java`
- Modify: `backend/src/main/java/com/colonel/saas/domain/user/facade/UserDomainFacade.java`
- Modify: `backend/src/main/java/com/colonel/saas/domain/user/facade/LegacyUserDomainFacade.java`
- Create: `backend/src/test/java/com/colonel/saas/domain/product/policy/PromotionAttributionOwnerPolicyTest.java`
- Modify: `backend/src/test/java/com/colonel/saas/domain/user/facade/LegacyUserDomainFacadeTest.java`

- [ ] 先测：渠道角色→`CHANNEL`；招商角色→`RECRUITER`；两类同时存在→稳定错误 `ATTRIBUTION_OWNER_TYPE_AMBIGUOUS`；无业务角色→empty。
- [ ] 运行 RED：

```powershell
mvn -f backend/pom.xml -Dtest=PromotionAttributionOwnerPolicyTest test
```

- [ ] 实现接口和 facade：

```java
public interface UserRoleCodeLookup {
    Map<UUID, Set<String>> findActiveRoleCodesByUserIds(Collection<UUID> userIds);
}
```

```java
Map<UUID, Set<String>> loadActiveRoleCodesByUserIds(Collection<UUID> userIds);
```

适配器各批量调用一次 `SysUserRoleMapper.findByUserIds`、`SysRoleMapper.selectBatchIds`，只保留 `status=1/deleted=0`。商品域只能经 `UserDomainFacade` 使用。

- [ ] 实现策略签名：

```java
public Optional<AttributionOwnerType> resolve(Set<String> roleCodes)
```

- [ ] 运行 GREEN 并提交：

```powershell
mvn -f backend/pom.xml -Dtest=PromotionAttributionOwnerPolicyTest,LegacyUserDomainFacadeTest,DddUserFacadeProductServiceBoundaryTest test
git add backend/src/main/java/com/colonel/saas/domain/user backend/src/main/java/com/colonel/saas/domain/product/policy backend/src/test/java/com/colonel/saas/domain/user backend/src/test/java/com/colonel/saas/domain/product/policy
git commit -m "feat: classify promotion attribution owner by role"
```

## Task 3：转链时固化快照

**Files:**

- Modify: `backend/src/main/java/com/colonel/saas/service/ProductService.java`
- Modify: `backend/src/main/java/com/colonel/saas/service/PickSourceMappingService.java`
- Modify: `backend/src/main/java/com/colonel/saas/controller/ColonelActivityProductController.java`
- Modify: `backend/src/main/java/com/colonel/saas/controller/ProductController.java`
- Modify: `backend/src/test/java/com/colonel/saas/service/PickSourceMappingServiceTest.java`
- Modify: `backend/src/test/java/com/colonel/saas/service/ProductServiceColonelBuyinIdTest.java`
- Modify: `backend/src/test/java/com/colonel/saas/controller/ColonelActivityProductControllerCopyPromotionTest.java`
- Modify: `backend/src/test/java/com/colonel/saas/controller/ProductControllerTest.java`

- [ ] 先测链接与 mapping 同为 `RECRUITER/CHANNEL`、冲突角色失败、无业务角色禁止、两个接口均允许渠道与招商角色。
- [ ] 运行 RED：

```powershell
mvn -f backend/pom.xml -Dtest=ProductServiceColonelBuyinIdTest,PickSourceMappingServiceTest,ColonelActivityProductControllerCopyPromotionTest,ProductControllerTest test
```

- [ ] 在外部 API 调用前解析 owner type：

```java
Set<String> roles = userDomainFacade.loadActiveRoleCodesByUserIds(List.of(userId))
        .getOrDefault(userId, Set.of());
AttributionOwnerType ownerType = ownerPolicy.resolve(roles)
        .orElseThrow(() -> new BusinessException(
                ResultCode.FORBIDDEN.getCode(),
                "当前角色不能创建可归因推广链接",
                "PROMOTION_ATTRIBUTION_ROLE_REQUIRED", null));
link.setAttributionOwnerType(ownerType.name());
```

- [ ] 给 `PickSourceMappingService` 最完整 overload 末尾增加 `String attributionOwnerType`；旧 overload 传 null；insert/update/冲突恢复/日志物化都复制。两个 controller 的 `@RequireRoles` 包含 `CHANNEL_LEADER/CHANNEL_STAFF/BIZ_LEADER/BIZ_STAFF`。
- [ ] 运行 GREEN 并提交：

```powershell
mvn -f backend/pom.xml -Dtest=ProductServiceColonelBuyinIdTest,PickSourceMappingServiceTest,ColonelActivityProductControllerCopyPromotionTest,ProductControllerTest,PromotionLinkCopyIntegrationTest test
git add backend/src/main/java/com/colonel/saas/service/ProductService.java backend/src/main/java/com/colonel/saas/service/PickSourceMappingService.java backend/src/main/java/com/colonel/saas/controller/ColonelActivityProductController.java backend/src/main/java/com/colonel/saas/controller/ProductController.java backend/src/test/java/com/colonel/saas/service/PickSourceMappingServiceTest.java backend/src/test/java/com/colonel/saas/service/ProductServiceColonelBuyinIdTest.java backend/src/test/java/com/colonel/saas/controller/ColonelActivityProductControllerCopyPromotionTest.java backend/src/test/java/com/colonel/saas/controller/ProductControllerTest.java
git commit -m "feat: snapshot promotion attribution owner"
```

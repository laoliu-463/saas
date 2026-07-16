# Phase 4：历史分类、前端边界与领域合同

## Task 1：历史映射 owner type 分类

**Files:**

- Create: `backend/src/main/java/com/colonel/saas/service/AttributionOwnerReconciliationService.java`
- Create: `backend/src/main/java/com/colonel/saas/controller/AttributionAdminController.java`
- Create: `backend/src/test/java/com/colonel/saas/service/AttributionOwnerReconciliationServiceTest.java`
- Create: `backend/src/test/java/com/colonel/saas/controller/AttributionAdminControllerTest.java`

- [ ] 先测：缺省 dry-run 零写入；apply 需 `confirm=true`；只分类 null；招商/渠道正确分类；冲突零写入；mapping/link 同事务；二次 apply 幂等；limit 最大 200。
- [ ] confirm 门禁代表性测试：

```java
ReconcileRequest request = new ReconcileRequest(
        List.of(UUID.randomUUID()), 200, false, false);
assertThatThrownBy(() -> service.reconcile(request))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("confirm=true");
verifyNoInteractions(mappingMapper, promotionLinkMapper);
```

- [ ] controller 只允许 ADMIN，接口固定：

```text
POST /api/order-attribution/admin/mapping-owner-reconcile
```

- [ ] 运行 RED：

```powershell
mvn -f backend/pom.xml -Dtest=AttributionOwnerReconciliationServiceTest,AttributionAdminControllerTest test
```

- [ ] 定义请求/结果：

```java
public record ReconcileRequest(
        List<UUID> userIds, Integer limit,
        Boolean dryRun, Boolean confirm) {}
public record ReconcileItem(
        UUID mappingId, UUID promotionLinkId, UUID userId,
        String proposedOwnerType, long potentialOrderCount,
        String status, String reason) {}
public record ReconcileResult(
        int scanned, int classifiable, int conflicts,
        int updated, boolean dryRun,
        List<ReconcileItem> items) {}
```

- [ ] `@Transactional reconcile` 只扫描 `owner_type IS NULL AND deleted=0`，批量读角色并复用 owner policy。potential order 按 activity/product/有效时间统计，仅诊断。apply 同事务更新 mapping 与关联 link。
- [ ] 用现有操作日志机制记录操作者、userIds、dryRun、confirm、统计；不记录 token/upstream payload。
- [ ] 运行 GREEN 并提交：

```powershell
mvn -f backend/pom.xml -Dtest=AttributionOwnerReconciliationServiceTest,AttributionAdminControllerTest,OperationLogInterceptorTest test
git add backend/src/main/java/com/colonel/saas/service/AttributionOwnerReconciliationService.java backend/src/main/java/com/colonel/saas/controller/AttributionAdminController.java backend/src/test/java/com/colonel/saas/service/AttributionOwnerReconciliationServiceTest.java backend/src/test/java/com/colonel/saas/controller/AttributionAdminControllerTest.java
git commit -m "feat: reconcile historical promotion owner types"
```

## Task 2：前端权限与招商展示

**Files:**

- Modify: `frontend/src/views/product/ProductLibrary.vue`
- Modify: `frontend/src/views/product/ProductLibrary.test.ts`
- Modify: `frontend/src/views/product/product-actions.ts`
- Modify: `frontend/src/views/product/product-actions.test.ts`
- Modify: `frontend/src/views/data/OrderDetailTab.vue`
- Modify: `frontend/src/views/data/OrderDetailTab.test.ts`

- [ ] 先测 `biz_leader/biz_staff` 可转链，非业务角色不可；recruiter 为空且 colonel 非空时招商显示 `-`。

```ts
it.each(['biz_leader', 'biz_staff'])(
  'allows %s to copy an attributable promotion link',
  role => expect(canCopyPromotionLinkForRoles([role])).toBe(true),
)
```

```ts
expect(readRecruiterDisplay({
  recruiterName: null,
  colonelName: '招商组长测试',
  colonelUserName: 'leader-test',
})).toBe('-')
```

- [ ] 运行 RED：

```powershell
npm --prefix frontend run test -- --run src/views/product/ProductLibrary.test.ts src/views/product/product-actions.test.ts src/views/data/OrderDetailTab.test.ts
```

- [ ] 允许角色集合：

```ts
const PROMOTION_LINK_ROLES = new Set([
  'channel_leader', 'channel_staff', 'biz_leader', 'biz_staff',
])
```

提示改为“仅渠道或招商角色可生成可归因推广链接”。招商显示只读 `recruiterName/recruiter_name`，删除 colonel fallback。`frontend/src/views/orders/components/OrderDetailModal.vue` 的商品/活动团长上下文不改。

- [ ] 运行 GREEN 并提交：

```powershell
npm --prefix frontend run test -- --run src/views/product/ProductLibrary.test.ts src/views/product/product-actions.test.ts src/views/data/OrderDetailTab.test.ts src/views/orders/components/OrderDetailModal.test.ts
git add frontend/src/views/product/ProductLibrary.vue frontend/src/views/product/ProductLibrary.test.ts frontend/src/views/product/product-actions.ts frontend/src/views/product/product-actions.test.ts frontend/src/views/data/OrderDetailTab.vue frontend/src/views/data/OrderDetailTab.test.ts
git commit -m "fix: align promotion permissions and recruiter display"
```

## Task 3：领域合同和 real-pre 手册

**Files:**

- Modify: `docs/00-V1范围冻结说明.md`
- Modify: `docs/02-V1业务流程与领域设计.md`
- Modify: `docs/03-项目剩余事项与任务看板.md`
- Modify: `docs/04-上线验收清单.md`
- Modify: `docs/领域/订单域.md`
- Modify: `docs/领域/业绩域.md`
- Modify: `docs/验收/real-pre联调手册.md`
- Modify: `harness/rules/instructions/domain/order-domain.md`
- Modify: `harness/rules/instructions/domain/performance-domain.md`

- [ ] 搜索旧规则：

```powershell
rg -n "活动招商|商品负责人|招商组长|默认招商|activity_owner|pick_source" docs harness/rules/instructions/domain
```

- [ ] 订单合同写明：创建时快照；RECRUITER 写招商，CHANNEL 写渠道；活动招商仅 fallback；原生键必须业务时间内 owner key 唯一。
- [ ] 业绩合同写明：只读订单 final user/source；招商 PERSONAL 按 final recruiter；不得重算。
- [ ] real-pre 顺序：部署健康 → 角色纠正审计 → 分类 dry-run → 人工核对 → confirm apply → 单单重放 dry-run → 人工核对 → 单单 apply → 四方验证。
- [ ] 搜索冲突并提交：

```powershell
rg -n "商品负责人.*默认招商|招商组长.*最终归属|分析模块.*重算" docs harness/rules/instructions/domain
git add docs/00-V1范围冻结说明.md docs/02-V1业务流程与领域设计.md docs/03-项目剩余事项与任务看板.md docs/04-上线验收清单.md docs/领域/订单域.md docs/领域/业绩域.md docs/验收/real-pre联调手册.md harness/rules/instructions/domain/order-domain.md harness/rules/instructions/domain/performance-domain.md
git commit -m "docs: define role-aware attribution contract"
```

Expected: 无有效旧口径冲突；历史描述如保留，明确标注已废弃。

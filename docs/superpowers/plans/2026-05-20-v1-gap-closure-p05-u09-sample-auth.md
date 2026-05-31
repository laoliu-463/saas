# V1 Gap Closure (P-05 / U-09 / Sample Permissions / ops_staff) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the four confirmed V1 gaps without expanding scope, and reconcile the plan with the codebase reality as of 2026-05-20.

**Architecture:** Reuse the current Spring Boot + Vue main chain instead of re-implementing features from scratch. `P-05` and sample permission handling already have backend foundations in place, so the plan focuses on front-end exposure, seed/migration correction, regression coverage, and document alignment.

**Tech Stack:** Spring Boot 3.2, MyBatis-Plus, PostgreSQL SQL scripts, Vue 3, Naive UI, Maven/JUnit, Playwright

---

## Reality Check

Before touching code, align on the current repo facts:

- `P-05` is **not** fully greenfield anymore:
  - `ProductOperationState` already contains `pinnedAt` / `pinnedUntil`.
  - `ProductService.pinProduct()` / `unpinProduct()` already exist.
  - `ColonelActivityProductController` already exposes `POST /{productId}/pin` and `DELETE /{productId}/pin`.
  - `ProductService.getSelectedLibraryPage()` already sorts active pins first.
  - Missing closure is mainly **front-end wiring**, API client exposure, and task-board/document freshness.
- `biz_leader` sample audit permission is already present in code:
  - `SampleController.ensureActionRolePermission()` already allows `BIZ_LEADER`.
  - `SampleController.getSamplePage()` and `requireSample()` already apply dept-scoped leader access.
  - Existing tests already assert `biz_leader` is **not** exempt from the 7-day apply limit.
- `ops_staff` is already no longer force-promoted to `dataScope=3` in `AuthService`; the live gap is the **seed/migration state** in `init-db.sql`.
- `docs/03-项目剩余事项与任务看板.md` is stale in at least two places:
  - It still says `P-05` has no backend implementation.
  - It says `biz_leader` gets the 7-day exemption, which conflicts with `docs/01`, `docs/02`, current code, and `SampleControllerTest`.

## File Map

### Product pin (`P-05`)

- Modify: `D:\Projects\SAAS\frontend\src\api\activityProduct.ts`
- Modify: `D:\Projects\SAAS\frontend\src\views\product\ProductLibrary.vue`
- Modify: `D:\Projects\SAAS\frontend\src\views\product\ProductDetail.vue`
- Modify: `D:\Projects\SAAS\frontend\src\views\product\components\ProductCard.vue`
- Verify only unless env audit says otherwise:
  - `D:\Projects\SAAS\backend\src\main\resources\db\alter-product-main-chain.sql`
  - `D:\Projects\SAAS\backend\src\main\resources\db\alter-test-existing-volumes-20260504.sql`
- Regression already present but re-run:
  - `D:\Projects\SAAS\backend\src\test\java\com\colonel\saas\service\ProductServiceTest.java`
  - `D:\Projects\SAAS\backend\src\test\java\com\colonel\saas\controller\ColonelActivityProductControllerTest.java`

### Multi-role user form (`U-09`)

- Modify: `D:\Projects\SAAS\frontend\src\views\system\UserList.vue`

### Sample permissions (`biz_leader`)

- Verify / possibly extend tests:
  - `D:\Projects\SAAS\backend\src\test\java\com\colonel\saas\controller\SampleControllerTest.java`
- Modify production code **only if** verification reveals a missing path:
  - `D:\Projects\SAAS\backend\src\main\java\com\colonel\saas\controller\SampleController.java`

### `ops_staff` scope normalization

- Modify: `D:\Projects\SAAS\backend\src\main\resources\db\init-db.sql`
- Create or modify existing idempotent patch script for old DBs:
  - `D:\Projects\SAAS\backend\src\main\resources\db\alter-ops-staff-data-scope-20260520.sql`
- Extend regression:
  - `D:\Projects\SAAS\backend\src\test\java\com\colonel\saas\auth\service\AuthServiceTest.java`

### Docs closure

- Modify: `D:\Projects\SAAS\docs\03-项目剩余事项与任务看板.md`
- Modify: `D:\Projects\SAAS\docs\04-上线验收清单.md`

---

### Task 1: Fix U-09 Front-end Multi-role Editing

**Files:**
- Modify: `D:\Projects\SAAS\frontend\src\views\system\UserList.vue`
- Verify: `D:\Projects\SAAS\frontend\package.json`

- [ ] **Step 1: Replace single-value role state with array state**

```ts
const selectedRoleIds = ref<string[]>([])

const visibleRoleSelectOptions = computed(() =>
  roleSelectOptions.value.filter(
    (role) => role.roleCode !== 'admin' || selectedRoleIds.value.includes(role.value)
  )
)

watch(selectedRoleIds, (value) => {
  formData.roleIds = [...value]
})
```

- [ ] **Step 2: Change the form select to multi-select**

```vue
<n-select
  v-model:value="selectedRoleIds"
  :options="visibleRoleSelectOptions"
  value-field="value"
  label-field="label"
  placeholder="请选择身份"
  filterable
  multiple
  clearable
/>
```

- [ ] **Step 3: Stop truncating edited users to one role**

```ts
Object.assign(formData, {
  id: row.id,
  username: row.username,
  realName: row.realName,
  phone: row.phone,
  email: row.email,
  roleIds: normalizedRoleIds,
  status: row.status
})
selectedRoleIds.value = normalizedRoleIds
```

- [ ] **Step 4: Make the selected-role summary render multiple labels**

```ts
const selectedRoleLabels = computed(() =>
  selectedRoleIds.value
    .map((id) => roleSelectOptions.value.find((role) => role.value === id)?.label || id)
    .filter(Boolean)
)
```

```vue
<div class="role-picker__footer">
  <n-tag
    v-for="label in selectedRoleLabels"
    :key="label"
    type="success"
    size="small"
    round
  >
    {{ label }}
  </n-tag>
</div>
```

- [ ] **Step 5: Verify the role table still renders multi-role rows correctly**

Run:

```bash
npm --prefix frontend run build
```

Expected: `vue-tsc -b && vite build` passes without `selectedRoleId` reference errors.

---

### Task 2: Normalize `ops_staff` Scope to “Sample-only all”

**Files:**
- Modify: `D:\Projects\SAAS\backend\src\main\resources\db\init-db.sql`
- Create: `D:\Projects\SAAS\backend\src\main\resources\db\alter-ops-staff-data-scope-20260520.sql`
- Modify: `D:\Projects\SAAS\backend\src\test\java\com\colonel\saas\auth\service\AuthServiceTest.java`

- [ ] **Step 1: Keep AuthService unchanged unless a regression test proves otherwise**

Current code already does the right thing for non-admin roles:

```java
int dataScope = roles.stream()
    .map(SysRole::getDataScope)
    .filter(scope -> scope != null && scope > 0)
    .max(Integer::compareTo)
    .orElse(1);

if (roleCodes.contains(RoleCodes.ADMIN)) {
    dataScope = 3;
}
```

Do **not** re-edit `AuthService.java` unless a failing test shows a hidden force-all branch.

- [ ] **Step 2: Fix fresh-db seed data**

Change the first `ops_staff` seed from `3` to `1`:

```sql
INSERT INTO sys_role (role_code, role_name, data_scope, status) VALUES
    ('admin',       '超级管理员',     3, 1),
    ('biz_leader',  '招商组长',       2, 1),
    ('biz_staff',   '招商专员',       1, 1),
    ('channel_leader','渠道组长',     2, 1),
    ('channel_staff', '渠道专员',     1, 1),
    ('ops_staff',     '运营专员',     1, 1),
    ('colonel_leader','上校负责人',   3, 1)
ON CONFLICT (role_code) DO NOTHING;
```

- [ ] **Step 3: Add an idempotent patch for existing databases**

Create a dedicated SQL patch instead of relying on seed-only behavior:

```sql
UPDATE sys_role
SET data_scope = 1
WHERE role_code = 'ops_staff' AND COALESCE(data_scope, 0) <> 1;
```

- [ ] **Step 4: Add refresh-path regression coverage**

Add a test alongside the existing login test so `refreshToken()` also respects configured scope:

```java
@Test
@DisplayName("刷新令牌 - ops_staff 保持角色配置的数据范围")
void refreshToken_opsStaff_shouldKeepConfiguredDataScope() {
    // arrange roles with ops_staff dataScope=1
    // mock jwt claims for refresh token
    // assert generated access token uses dataScope=1
}
```

- [ ] **Step 5: Verify sample-only all is still provided by SampleController**

Run:

```bash
cd backend
mvn -Dtest=AuthServiceTest,SampleControllerTest test
```

Expected:
- `AuthServiceTest` keeps `ops_staff` at `1`
- `SampleControllerTest` still proves `ops_staff` uses unscoped sample queries where intended

---

### Task 3: Verify `biz_leader` Sample Audit Permissions and Keep 7-day Rule Closed

**Files:**
- Verify / modify: `D:\Projects\SAAS\backend\src\main\java\com\colonel\saas\controller\SampleController.java`
- Modify: `D:\Projects\SAAS\backend\src\test\java\com\colonel\saas\controller\SampleControllerTest.java`

- [ ] **Step 1: Treat this as a verification-first task, not a blind code edit**

Current code already contains:

```java
case "PENDING_SHIP", "REJECTED" -> {
    if (!hasAnyRole(roleCodes, RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.COLONEL_LEADER, RoleCodes.BIZ_STAFF)) {
        throw new ForbiddenException("仅招商角色可以审核寄样");
    }
}
```

Current 7-day exemption code already excludes `biz_leader`:

```java
private boolean isLeaderRoleCode(String roleCode) {
    return RoleCodes.CHANNEL_LEADER.equals(roleCode);
}
```

- [ ] **Step 2: Add regression tests for every required path, including detail access**

Keep or add tests covering:

```java
assertThat((Boolean) method.invoke(sampleController, List.of(RoleCodes.BIZ_LEADER))).isFalse();
```

and add a direct detail-scope test:

```java
@Test
void getSampleById_shouldAllowBizLeaderOnlyForOwnDeptProduct() {
    // sample -> product -> product_operation_state -> assignee dept matches biz leader dept
    // expect success
}
```

- [ ] **Step 3: Add the negative-path detail/export checks if missing**

```java
@Test
void getSampleById_shouldRejectBizLeaderForOtherDeptProduct() {
    // same structure as existing page-scope negative test
    // expect ForbiddenException
}
```

```java
@Test
void exportSamples_shouldAllowBizLeaderButStillRespectDeptScopedRecordCollection() {
    // if export path is not already covered, add a focused controller test
}
```

- [ ] **Step 4: Only edit production code if the tests expose an uncovered path**

Likely outcome: keep `SampleController.java` unchanged and let this task close through tests + docs.

- [ ] **Step 5: Verify**

Run:

```bash
cd backend
mvn -Dtest=SampleControllerTest test
```

Expected: leader audit allowed for own dept, blocked for other dept, `biz_leader` still not exempt from the 7-day rule.

---

### Task 4: Close the Remaining P-05 Gap Through Front-end Wiring

**Files:**
- Modify: `D:\Projects\SAAS\frontend\src\api\activityProduct.ts`
- Modify: `D:\Projects\SAAS\frontend\src\views\product\ProductLibrary.vue`
- Modify: `D:\Projects\SAAS\frontend\src\views\product\ProductDetail.vue`
- Modify: `D:\Projects\SAAS\frontend\src\views\product\components\ProductCard.vue`

- [ ] **Step 1: Do not add redundant backend code that already exists**

Current backend already has:

```java
public Map<String, Object> pinProduct(...)
public Map<String, Object> unpinProduct(...)
```

and controller routes:

```java
@PostMapping("/{productId}/pin")
@DeleteMapping("/{productId}/pin")
```

So the remaining implementation is the front-end surface plus any doc/schema reconciliation.

- [ ] **Step 2: Add API client helpers**

```ts
export const pinActivityProduct = (activityId: string | number, productId: string | number) =>
  request.post(`/colonel/activities/${activityId}/products/${productId}/pin`)

export const unpinActivityProduct = (activityId: string | number, productId: string | number) =>
  request.delete(`/colonel/activities/${activityId}/products/${productId}/pin`)
```

- [ ] **Step 3: Add library-card pin entry points**

Extend `ProductCard.vue` with optional emits and library-mode actions:

```ts
defineEmits<{
  toggle: [productId: string | null]
  detail: [product: any]
  pin: [product: any]
  unpin: [product: any]
  // existing emits...
}>()
```

```vue
<n-button
  v-if="libraryMode && product.selectedToLibrary && canPin"
  size="small"
  quaternary
  type="warning"
  @click.stop="$emit(product.pinned ? 'unpin' : 'pin', product)"
>
  {{ product.pinned ? '取消置顶' : '置顶 24h' }}
</n-button>
```

- [ ] **Step 4: Wire ProductLibrary page actions**

In `ProductLibrary.vue`, add role gating plus handlers:

```ts
const canPinProduct = computed(() =>
  hasAccess(authStore.roleCodes, [ROLE_CODES.BIZ_STAFF, ROLE_CODES.BIZ_LEADER, ROLE_CODES.COLONEL_LEADER])
)

const mutatePin = async (row: any, pinned: boolean) => {
  const activityId = String(row?.sourceActivityId || row?.activityId || '')
  const productId = String(row?.productId || '')
  if (!activityId || !productId) {
    message.warning('商品缺少来源活动信息，暂时无法操作置顶')
    return
  }
  if (pinned) await pinActivityProduct(activityId, productId)
  else await unpinActivityProduct(activityId, productId)
  await refreshProducts()
}
```

- [ ] **Step 5: Add pin/unpin actions to ProductDetail drawer**

In `ProductDetail.vue`, extend `canDo()` and the action bar:

```ts
if (action === 'pin') {
  return hasAccess(roles, ['biz_staff', 'biz_leader', 'colonel_leader']) && businessReady.value
}
```

```ts
const isPinned = computed(() => Boolean(detail.value?.pinned))
const pinnedUntilText = computed(() => detail.value?.pinnedUntil || '')
```

```vue
<n-button
  v-if="libraryMode && canDo('pin')"
  type="warning"
  size="small"
  secondary
  @click="handleAction(isPinned ? 'unpin' : 'pin')"
>
  {{ isPinned ? '取消置顶' : '置顶 24h' }}
</n-button>
```

- [ ] **Step 6: Reuse existing backend coverage and verify front-end compilation**

Run:

```bash
cd backend
mvn -Dtest=ProductServiceTest,ColonelActivityProductControllerTest test
npm --prefix ..\\frontend run build
```

Expected:
- backend pin tests stay green
- front-end build passes with the new pin API and emit surface

- [ ] **Step 7: Run targeted E2E smoke**

Run:

```bash
npx playwright test tests/e2e/03-product.spec.ts tests/e2e/04-activity-product.spec.ts tests/e2e/09-full-user-journey.spec.ts
```

Expected: no regression on product library/detail flows and user-management edit flow.

---

### Task 5: Reconcile Docs With the Frozen Contract

**Files:**
- Modify: `D:\Projects\SAAS\docs\03-项目剩余事项与任务看板.md`
- Modify: `D:\Projects\SAAS\docs\04-上线验收清单.md`

- [ ] **Step 1: Correct the `P-05` task-board status**

Replace statements like “全仓无置顶字段/API/前端” with a status that matches the repo:

```md
- 当前状态：后端实体 / Service / Controller / 排序 / 回归测试已具备；前端商品库与详情页尚未接入置顶操作，文档状态待回写。
```

- [ ] **Step 2: Correct `biz_leader` 7-day exemption wording**

Align `docs/03` to `docs/01`, `docs/02`, and test reality:

```md
| 7天限制豁免 | ❌ | ✅ | ❌ | ❌ | ✅ | ❌ |
```

- [ ] **Step 3: Update the `ops_staff` task note to reflect the real gap**

```md
- 当前状态：AuthService 已按角色配置返回 dataScope；当前风险在于 init-db.sql 仍把 ops_staff 种子写成 3，需要通过 seed + alter SQL 收口。
```

- [ ] **Step 4: Update acceptance wording in `docs/04`**

Add or refine acceptance bullets for:

```md
- U-09：员工编辑表单支持多选角色，保存后角色并集生效。
- P-05：商品库和商品详情均可触发置顶/取消置顶，超出 10 个时返回明确提示。
- S-03：招商组长仅可审核本组寄样单，且不享受 7 天申请豁免。
- U-04 / ops_staff：寄样场景可看全部，非寄样模块不自动获得 all 数据范围。
```

- [ ] **Step 5: Final verification**

Run:

```bash
cd backend
mvn -Dtest=ProductServiceTest,ColonelActivityProductControllerTest,AuthServiceTest,SampleControllerTest test
npm --prefix ..\\frontend run build
```

Expected: regression suite green, docs consistent with `docs/01` and `docs/02`, and no stale task-board statements remain.

---

## Recommended Execution Order

1. `U-09` front-end multi-role fix
2. `ops_staff` seed/migration correction
3. sample permission verification + regression
4. `P-05` front-end exposure
5. docs closure

This order closes the P0 permission risks first, then lands the product-library contract item, and finishes with doc normalization once the code facts are stable.

## Self-Review

- **Spec coverage:** `P-05`, `U-09`, `biz_leader` sample audit scope, and `ops_staff` scoped access are all mapped to explicit tasks.
- **Placeholder scan:** No `TODO` / `TBD` placeholders remain; every task has named files and commands.
- **Type consistency:** The plan uses existing code names (`pinProduct`, `unpinProduct`, `isExemptFromSevenDaysLimit`, `roleIds`, `selectedRoleIds`) instead of inventing new ones.

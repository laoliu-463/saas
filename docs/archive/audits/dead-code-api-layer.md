# 前端 API 层死代码分析

**日期**：2026-05-24
**分支**：`feature/auth-system`
**方法**：code-review-graph dead_code + `frontend/src` grep
**交叉审查**：见 [dead-code-review-20260524.md](./dead-code-review-20260524.md)

---

## 确认无 views 引用的 export（30+）

以下函数**仅在 `*.ts` 定义或测试中出现**，业务 Vue 页面未 import。

### activity.ts (3)

- `getActivityPage`
- `getActivityDouyinDetail`
- `syncColonelActivity`

活动主链已走 `api/activityProduct.ts` + `GET /colonel/activities`。

### activityProduct.ts (2)

- `getPinnedProducts`
- `followActivityProduct`

### douyin.ts (3)

- `getDouyinActivityDetail`
- `getDouyinProductsByActivity`
- `createOrUpdateDouyinActivity`

抖音运营页 `DouyinIntegration.vue` 使用 OAuth 专用 API，未接上述三个。

### order.ts (3)

- `getUnattributedOrders`
- `getOrderFilterOptions`
- `triggerOrderSync`

订单页使用 `order.ts` 内其他 export（列表/详情/stats）。

### product.ts (4) — **legacy 双轨，优先清理**

- `getProductDetail`
- `bindProductActivity`
- `convertProductLink`
- `followProduct`

主链已迁移：`getActivityProductDetail`、`convertActivityProductLink` 等（`activityProduct.ts`）。
**仍在用**：`getProducts`、`getProductLibraryCategories`、`applyQuickSample`、`listPartners`。

### ruleCenter.ts (1)

- `updateRuleCenterGroup` — 规则中心页使用 `batchUpdateRuleCenter`

### sample.ts (3)

- `deleteSample`
- `searchSampleTalents`
- `getSampleBoard`

**仍在用**：`syncSampleLogistics`（`SampleDetail.vue`）、`syncAllSampleLogistics`（admin 路径 + 单测）。

### sys.ts (7)

| 函数 | 审查 |
|---|---|
| `getAssignableUserOptions` | 无引用；候选分配已用 `getUserMasterRecruiters` 等 |
| `getUserById` | 无 views 引用 |
| `getRoleById` | 无 views 引用 |
| `getDeptList` | 无引用；`DeptList.vue` 用 `getDeptTree` |
| `getDeptById` | 无 views 引用 |
| `getConfigGrouped` | 无引用；`ConfigList.vue` 用 `getConfigPage` |
| `getConfigById` | 无 views 引用 |

**仍在用**：`getUserPage`、`getRolePage`、`getRoleAll`、`addDeptGroupMembers`、`removeDeptGroupMembers` 等。

### talent.ts (7)

- `getTalentList` — 纯别名，指向 `getTalentPage`
- `getTalentPublic`
- `resolveTalentProfile`
- `deleteTalent`
- `refreshTalent`
- `manualFillTalent`
- `exclusiveCheck`

**仍在用**：`getTalentPage`、`getTalentById`、`syncTalentProfile`（`TalentDetailModal.vue`）、`claimTalent`、`releaseTalent` 等。

---

## 图工具已知误报（有 Vue 调用）

- `centToYuan`
- `assignActivityProduct`
- `assignActivityProductAuditOwner`
- `syncActivityProducts`（前端 POST 无后端；实际 sync 用 `?refresh=true`）
- `updateTalent`

---

## 审查修正后的优先级

| 优先级 | 项 | 动作 |
|---|---|---|
| **P0** | `product.ts` legacy 4 export | 删除或 `@deprecated`，文档指向 `activityProduct.ts` |
| **P0** | `getTalentList` 别名 | 删除别名，统一 `getTalentPage` |
| **P1** | `updateRuleCenterGroup` | 删除冗余 export |
| **P1** | `sample.ts` 未用 3 export | 删或补 UI（看 V1 是否要删除/看板） |
| **P1** | `setGlobalPermissionHint` | 删除或改 `requestError` 统一调用 |
| **P2** | `douyin.ts` / `activity.ts` 全系列 | V2 或 ops 专用，暂保留文件加注释 |
| **待产品** | `deleteTalent` / `deleteSample` | 后端有能力、前端无入口 — 补 UI 或明确 V1 不做 |

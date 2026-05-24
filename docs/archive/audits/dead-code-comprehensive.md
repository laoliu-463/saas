# V1 死代码全层排查报告

**日期**：2026-05-24
**分支**：`feature/auth-system`
**工具**：code-review-graph `refactor_tool` / dead_code 检测 + grep 交叉验证
**交叉审查**：见 [dead-code-review-20260524.md](./dead-code-review-20260524.md)（**执行清理前必读**）

---

## 说明

code-review-graph 基于「无入边」启发式检测死代码，对 Spring 注解、Vue 模板绑定、QA/E2E 脚本调用存在系统性误报。本报告为**扫描原始结果**；经源码审查后，Phase 1 清单有多处需降级或剔除。

---

## 总览

| 层 | 工具检测数 | 误报(FP) | 扫描确认死代码 | FP率 |
|---|---|---|---|---|
| 前端 API 函数 | 33 | 0 | **33** | 0% |
| 后端 Controller 方法 | 130 | 96 | **34** | 73.8% |
| 后端 Service 方法 | 114 | 81 | **33** | 71.1% |
| 前端 View 函数 | 174 | 76 | **98** | 43.7% |
| 前端其他(stores/utils) | 7 | 0 | **7** | 0% |
| 死类(Class) | 382 | ~380 | **~2** | 99.5% |
| **合计** | **840** | **~633** | **~207** | **75.4%** |

> 死类几乎全是误报：SQL DDL 表定义、Spring Bean 配置、Test 类。

---

## 1. 前端 API 层 (33 个)

详见 [dead-code-api-layer.md](./dead-code-api-layer.md)。

扫描报告曾列 `getColonelProductList` / `getAssignments` / `getPermissionHint` —— **当前仓库无对应 export**（可能为旧名或已删除）。权限提示实际走 `stores/permissionHint.ts` + `utils/requestError.ts`，非独立 API 函数。

---

## 2. 后端 Controller 层 (34 个)

验证方法：HTTP 注解路径 → grep 前端 API；**未覆盖** `runtime/qa/**`、`tests/e2e/**`（审查后多项需剔除）。

### 扫描标「高影响」

| 方法 | Controller | API 路径 | 扫描结论 |
|---|---|---|---|
| `replayAttribution` | OrderController | `POST /orders/replay-attribution` | 无 Vue 调用 |
| `performanceBackfill` | OrderController | `POST /orders/performance-backfill` | 无 Vue 调用 |
| `batchFillCommission` | OrderController | `POST /orders/commission-batch` | 无 Vue 调用 |
| `recalculateSingle` | OrderController | `POST /orders/commission-recalculate` | 无 Vue 调用 |
| `syncLogistics` | SampleController | `POST /samples/{id}/logistics/sync` | 无 Vue 调用（审查：**有误**） |
| `syncAllLogistics` | SampleController | `POST /samples/logistics/sync-all` | 无 Vue 调用（审查：**部分有误**） |
| `colonelPartnerFilterOptions` | ProductController | `GET /products/filter-options/colonel-partners` | 无 Vue 调用 |
| `assignRoleMenus` | SysRoleController | `PUT /roles/{id}/menus` | 无 Vue 调用 |
| `addGroupMembers` | SysDeptController | `POST /depts/groups/{groupId}/members` | 无 Vue 调用（审查：**有误**） |
| `removeGroupMembers` | SysDeptController | `DELETE /depts/groups/{groupId}/members` | 无 Vue 调用（审查：**有误**） |

### 中影响 (运维/调试)

| 方法 | Controller | API 路径 |
|---|---|---|
| `replayWebhookEvents` | DouyinController | `/webhook-events/replay` |
| `tokenCreateProbe` | DouyinController | `/token-create-probes` |
| `dingdanTongbuYuanshi` | DouyinController | `/order-sync-probes/raw` |
| `quxiaoHuodongShangpinYuanshi` | DouyinController | `/activity-product-cancellations/raw` |
| `redisProbe` | RedisProbeController | `/redis-probe` |
| `auditLogs` | AdminProductDisplayController | `/audit-logs` |
| `renderCopyBrief` | ColonelActivityProductController | `GET .../copy-brief` |
| `updateContact` | AdminColonelPartnerController | `PUT .../contact` |
| `colonelOpenEvent` | DouyinWebhookController | (webhook handler) |
| `currentUser` | CurrentUserController | (扫描误报：有 `@GetMapping`) |

### 低影响

构造器(8)、`allTree`(SysMenuController)、`health`(SystemEnvController)。

### 无注解辅助方法 (23 个)

`normalizeOrderRow`, `toOptionItem`, `applyDataScope`, `export` 等 — Controller 内私有工具，非 HTTP 端点。

---

## 3. 后端 Service 层 (33 个)

验证方法：过滤构造器 + 内部类构造器。

**审查结论**：下列方法名在 `backend/src/main/java` **当前分支全文检索无匹配**，清单可能来自过期图谱或已重构删除。**不可作为删代码依据**，需重新 `code-review-graph build` 后再扫。

扫描曾列：`getProductSelectionForTalent`, `calculateCommission`, `batchUpdatePrices`, `searchProducts`, `createRuleFromTemplate`, `evaluateRule`, `previewRuleEffect`, `getTopPerformers`, `getDashboardStats`, `syncOrdersFromDouyin`, `retrySync`, `getAttributionDetails`, `matchTalentOrders`, `findTalentByDouyinId`, `batchUpdateStatus`, `getDisplayProducts`, `setProductVisibility` 等。

---

## 4. 前端 View 层 (98 个)

验证方法：Vue `<template>` regex 匹配函数名。43.7% 模板绑定误报。

**审查结论**：页面文件名与仓库不一致（如 `DashboardView.vue` → 实际 `views/dashboard/index.vue`）。**禁止批量删除**。

| 扫描页面名 | 实际路径（审查） |
|---|---|
| `DashboardView.vue` | `frontend/src/views/dashboard/index.vue` |
| `ProductList.vue` | `frontend/src/views/product/index.vue` |
| `TalentList.vue` | `frontend/src/views/talent/index.vue` |
| `SampleList.vue` | `frontend/src/views/sample/index.vue`（合作单工作台） |
| `OrderList.vue` | `frontend/src/views/data/OrderList.vue` |
| `RuleCenter.vue` | `frontend/src/views/system/rule-center/index.vue` |
| `UserManagement.vue` | `frontend/src/views/system/UserList.vue` |

---

## 5. 前端其他层 (7 个)

| 文件 | 扫描结论 | 审查 |
|---|---|---|
| `ProductSelectionCard.vue` | 4 个死函数 | **误报**：`copyBrief`/`quickSample` emit 在 template 使用 |
| `main.ts` | 1 个 | 待人工确认 |
| `stores/permissionHint.ts` | `setGlobalPermissionHint` 无引用 | **成立**：仅 `clearGlobalPermissionHint` / `globalPermissionHint` 在用 |
| `app.ts` | 1 个 | 待确认路径 |

---

## 原始 Phase 建议（已被审查修正）

扫描建议的 Phase 1 **不可直接执行**。经 [dead-code-review-20260524.md](./dead-code-review-20260524.md) 修正后：

- **可删**：legacy `product.ts` 未用 export、纯 API 别名、冗余 `updateRuleCenterGroup`（页面用 `batchUpdateRuleCenter`）
- **保留**：`replayAttribution`、物流 sync、部门成员增删（有 QA/E2E/页面调用）
- **合并/废弃端点**：`renderCopyBrief`（能力已由 `POST .../promotion-links` 覆盖）
- **暂缓**：View 层 bulk 清理、Service 层清单（需重扫）

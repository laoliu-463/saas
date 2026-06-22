LABEL=ready-for-agent
TITLE=[P1-URGENT] [PRODUCT-FIX-001] /product/manage/products 无 query 时 fallback 到 assigned[0] 导致数据归属错位
---
## Parent

- PRD: `docs/决策/PRD-PRODUCT-MANAGE-FALLBACK-FIX.md`
- ADR: `docs/决策/ADR-007-活动列表与商品库入口路由统一.md`
- 引入 commit: `e440f5ca` (fix: product manage page uses activity product source, 2026-06-22 22:58)

## What to build

**Tracer bullet**：从「路由进入」到「空态展示」到「banner 渲染」的端到端薄切片。

具体行为变更：

1. `frontend/src/views/product/index.vue::ensureActivityId()` 在 `/product/manage/products` 路径上**移除** `assignedActivityOptions[0]` fallback 逻辑（line 589-602）。
2. `frontend/src/views/product/index.vue::fetchProducts()` 在 `selectedActivityId` 为空时**直接渲染空态**，不发 `/colonel/activities/{id}/products` 请求（line 749-754 加短路）。
3. 新增 `frontend/src/views/product/product-page-data-source.ts::resolveActivityContextForManageProductsPath(route, assignedOptions, role)` 纯函数，返回 `{ status: 'ready' | 'empty' | 'forbidden' | 'loading', activityId?, activityName? }`。
4. 新增 `frontend/src/views/product/components/CurrentActivityBanner.vue`：根据 status 渲染「当前活动: XXX (ID)」或「请先选择活动」或「无权限」提示条。
5. `filters.value` **不再被 `ensureActivityId()` 改写**：只有用户在活动下拉里选择时才改 `recruitActivityId`。

## Acceptance criteria

- [ ] 直接访问 `/product/manage/products`（无 query）渲染"请先选择活动"空态，Network 不出现 `/colonel/activities/.../products` 请求
- [ ] 访问 `/product/manage/products?activityId=3916506` 渲染"当前活动: XXX (3916506)"banner，items 正常加载
- [ ] 访问 `/product/manage/products?activityId=99999999`（非 assigned 列表）渲染"无权限"状态
- [ ] 从 `/product/manage` 活动列表点活动行 → URL 变成 `/product/manage/products?activityId=X` 且保留 query
- [ ] 单测 `product-page-data-source.test.ts` 新增 6+ case 覆盖四种 status（ready/empty/forbidden/loading）
- [ ] `index.vue` 现有测试补充 2 case（空态 + banner）
- [ ] 不破坏 ADR-007 的 query 入口契约
- [ ] 不引入新的 fallthrough 路径（不得再 fallback 到任何活动）

## Blocked by

None — can start immediately

## Context (read first)

Per `ask-matt` context hygiene, this issue must be self-contained — the implementer is in a fresh session.

**Required reading order:**
1. `AGENTS.md` (project protocol + Agent skills section)
2. `CONTEXT.md` (domain glossary)
3. `docs/决策/PRD-PRODUCT-MANAGE-FALLBACK-FIX.md` (this PRD)
4. `docs/决策/ADR-007-活动列表与商品库入口路由统一.md` (route unification ADR)
5. `harness/engineering/issues-index.md` (related issues state)

**Files this slice touches (absolute paths):**
- `D:\Projects\SAAS\frontend\src\views\product\index.vue` (ensureActivityId, fetchProducts)
- `D:\Projects\SAAS\frontend\src\views\product\product-page-data-source.ts` (新增纯函数)
- `D:\Projects\SAAS\frontend\src\views\product\product-page-data-source.test.ts` (新增单测)
- `D:\Projects\SAAS\frontend\src\views\product\components\CurrentActivityBanner.vue` (新增组件)
- `D:\Projects\SAAS\frontend\src\views\product\index.test.ts` (如存在, 补充单测)

**Related issues:**
- #3 (parent PRD: DDD-MIGRATION-100, 不直接关联)
- #25 (最近 P1-URGENT, 独立 sprint 工作)

**Commits / 历史背景:**
- `e440f5ca` 修复了"商品管理页用商品库数据"问题（数据源切换到活动商品），但引入了 `assignedActivityOptions[0]` fallback，是本 bug 的根因。
- `840f9b` 前后 ADR-007 把 `/product/manage/:activityId` 重定向到 `/product/manage/products?activityId=X` 统一入口。

**已知 constraints:**
- ADR-007: 「活动列表与商品库入口路由统一」—— 必须保持 query 入口契约
- 不得破坏 `assignedActivityOptions` 的现有加载逻辑 (`assigned-activity-options.ts`)
- 不得改后端 API 行为

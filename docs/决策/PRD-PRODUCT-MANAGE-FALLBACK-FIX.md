# PRD: 商品管理页 fallback activityId 修正（PRODUCT-MANAGE-FALLBACK-FIX）

> **状态**：V1 必做（活动商品业务正确性阻塞）
> **作者**：Hermes/M3
> **日期**：2026-06-23
> **关联**：ADR-007（活动列表与商品库入口路由统一）、e440f5ca（数据源切换 commit）、CONTEXT.md「活动/招商活动」术语

## Problem Statement

`/product/manage/products` 路径（路由 component = `views/product/index.vue`）在用户**未带 `?activityId=...` query 直接访问**时，页面 fallback 到 `assignedActivityOptions.value[0]` 作为当前活动 ID（`index.vue:589-602`）。

带来三个真实用户可见问题：

1. **数量对不上**：BIZ_STAFF 在「活动查询」选活动 A（如 3916506，200 条商品），切到「商品管理」页后看到的是**分配给他的第一个活动 X**（50 条商品）的数据。两份数字对应不同活动，运营对账时直接误判。
2. **数据归属错位**：活动 X 的商品被误推到「商品管理」工作流，操作日志里 `recruitActivityId = X`、用户心智里是 A。
3. **沉默失败**：URL 不会变成 `?activityId=X`，但 `filters.value` 被悄悄改写（`recruitActivityId: X`），用户**没有任何视觉提示**说明当前数据来源不是他选的活动。

触发场景：

- 用户从 `/product/manage`（活动列表）进来但**没点「商品信息」链接**（只点导航栏）
- 直接收藏 `/product/manage/products` 链接打开
- 旧书签/分享链接缺 query

## Solution

**核心**：在 ADR-007 框架下，补齐"无 query 时路径必须有显式活动上下文"的契约。具体做法：

1. **`/product/manage/products` 无 query 时显示「请先选择活动」空态**，不再 fallback 第一个 assigned 活动。
2. 在页面顶部渲染「当前活动: XXX（或未选择）」状态条，让用户**一眼看到数据归属**。
3. 保留 `/product/manage/products?activityId=XXX` 显式入口，与 ADR-007 的 query 入口契约一致。

后续演进：在 `/product/manage/products` 路径加活动选择器组件（V2 范围），本次 PRD 范围**不包含**。

## User Stories

### 主要 user stories

1. As a **BIZ_STAFF 运营**，I want 在打开 `/product/manage/products` 看到清晰的"请先选择活动"提示 so that 我不会把别的活动的商品误当成我关注活动的商品。
2. As a **BIZ_STAFF 运营**，I want 页面顶部显示"当前活动: XXX (ID)"状态条 so that 即使数据加载出来后我也能一眼确认当前在看哪个活动。
3. As a **BIZ_LEADER 组长**，I want 直接用 `/product/manage/products?activityId=3916506` 分享链接给组员 so that 大家看到的是同一份数据（按 ADR-007 的 query 入口）。
4. As a **BIZ_STAFF 运营**，I want 在「活动查询」点活动行后**保留**那个活动上下文 so that 切换到「商品管理」时数据归属一致。
5. As a **架构师**，I want `/product/manage/products` 路径的 fallback 行为有单测覆盖 so that 后续重构不会再悄悄引入数据归属错位。
6. As a **QA**，I want e440f5ca 这类数据源切换 commit 有对应的回归 case so that "商品管理页必须按活动商品数据展示"的契约不被破坏。
7. As a **BIZ_LEADER 组长**，I want 显式选择活动前的页面是**只读空态**（没有操作按钮）so that 我不会误点同步/审核/入库等破坏性操作。

### 边缘 cases

8. As a **运营**，I want 当 `assignedActivityOptions` 加载失败时，页面不应该 fallback 到任意活动 so that 错误状态可见（错误提示条）。
9. As a **运营**，I want 当 query 带 activityId 但该活动不在我的 assigned 列表里时，页面应该展示「无权限」状态 so that 权限边界清晰。
10. As a **运营**，I want 当前活动被改为"未选择"后，filters 里残留的 `recruitActivityId` 应该被清空 so that 后续操作不会用到旧活动 ID。
11. As a **架构师**，I want ADR-007 的统一入口契约对**所有商品管理路径**都生效 so that 路由与 query 语义一致。

## Implementation Decisions

### 模块改动

- **`frontend/src/views/product/index.vue`** — `ensureActivityId()` 行为重构：移除 `assignedActivityOptions[0]` fallback；新增空态分支；filters 不再被悄悄改写。
- **`frontend/src/views/product/product-page-data-source.ts`** — 新增纯函数 `resolveActivityContextForManageProductsPath(route, assignedOptions)`，把"路径 → 活动上下文"的判定从 component 提到可单测模块。
- **`frontend/src/views/product/components/`** — 新增 `CurrentActivityBanner.vue`：渲染"当前活动: XXX (ID)"或"请先选择活动"提示条；data-testid 与 ADR-007 风格一致。
- **`frontend/src/views/product/index.vue`** — `applyFilters` 调用点增加 `currentActivityId.value == null` 短路：没有显式活动上下文时直接渲染空态，不发请求。

### 接口契约

- 路由 `/product/manage/products` 行为：
  - 无 query → 渲染「请先选择活动」空态（不再发请求）
  - 有 query `?activityId=XXX` 且 XXX 命中 assigned 列表 → 正常加载
  - 有 query `?activityId=XXX` 但 XXX 不命中 → 渲染「无权限访问该活动」状态
- filters 不再被 `ensureActivityId()` 改写：`recruitActivityId` 只由用户在下拉里选择时改写。
- `fallbackActivityId.value` 仅作为「历史未拆干净路径」的兜底，**不在 `/product/manage/products` 路径上使用**。

### 测试 seam

- 纯函数 seam：`resolveActivityContextForManageProductsPath(route, assignedOptions, userRoles)` 返回 `{ status: 'ready' | 'empty' | 'forbidden' | 'loading', activityId?, activityName? }`
- 单测 seam：`product-page-data-source.test.ts` 新增 6+ case 覆盖四种 status。
- E2E seam：`tests/e2e/39-product-manage-fallback.spec.ts` 新增 4 case：直接进 / 带错 ID / 带对 ID / 切换活动后 query 同步。

### 不在本次范围

- ❌ 改造商品管理页加活动选择器 UI（V2 任务）
- ❌ 修改 ADR-007（保留现有 query 入口契约）
- ❌ 后端 `/colonel/activities/{activityId}/products` 行为改动
- ❌ 修正 `applyActivityProductsPage` 内部 `applyFilters` 的字段映射问题（如果是另一个独立 bug，单独立项）
- ❌ Codex race 根因治理（独立 PRD）

## Testing Decisions

### 测什么

只测**外部行为**，不测实现细节：

- 用户不带 query 访问：看到"请先选择活动"空态，不发请求
- 用户带合法 query 访问：看到"当前活动: XXX"，items 渲染正确
- 用户带无权 query 访问：看到"无权限"状态
- 用户在「活动查询」点活动行后 URL 变成 `/product/manage/products?activityId=X`，刷新页面仍保留

### 哪些模块测

- `product-page-data-source.test.ts`：纯函数单测 6+ case（必测，是 se.am）
- `index.vue` 现有测试：补充 2 case（空态 + 当前活动 banner）
- E2E：`tests/e2e/39-product-manage-fallback.spec.ts`：跨页面状态切换

### 测试金字塔位置

- **大量**单元（纯函数）
- **中等**E2E（4 case）
- **少量**手测（视觉确认 banner 样式）

## Out of Scope

- ❌ 商品管理页加活动选择器 UI 改造（V2）
- ❌ 后端 BizStatusFilter EMPTY mode 触发 0 条的另一独立问题
- ❌ 前端 `applyFilters` 字段映射 bug（如果验证是独立 bug）
- ❌ Codex CLI race condition 治理
- ❌ 性能优化（DB 同步翻页上限）

## Further Notes

### 与现有 ADR 的关系

- **ADR-007**：本次修复是 ADR-007 框架内的"无 query 时路径行为"补全，**不违反** ADR-007
- **ADR-008**：本次修复与状态口径无关
- **PRD-DDD-MIGRATION-100**：本次修复属"产品前端 bug 修复"，不直接关联 DDD 进度

### DDD 视角

`ensureActivityId()` 当前把"活动上下文解析"逻辑直接写在 View 层，违反"业务规则不下沉到 UI 层"原则。本次修复把判定提到 `product-page-data-source.ts`（已是纯函数模块），是符合 DDD 边界的清理动作。**不需要新增 Policy 类**，因为这只是路径→上下文的解析，不是业务规则。

### 风险

- **风险 1**：移除 fallback 后，旧用户直接收藏 `/product/manage/products` 打开会看到空态。**缓解**：状态条明确提示「请先选择活动」，并附「去活动列表」按钮（指向 `/product/manage`）。
- **风险 2**：如果 `/product/manage/products?activityId=X` 的 X 是用户无权访问的活动，原本 fallback 会让他看 assigned 列表第一个活动（也是有权限的）。**缓解**：本次修复改为显式「无权限」状态，更安全。
- **风险 3**：可能影响灰度开关测试。**缓解**：本次修复前先在 feature 分支验证。

### 验收

- [V1 必做] 单测 248+ 通过
- [V1 必做] E2E 4 case 全过
- [V1 必做] 直接访问 `/product/manage/products` 看到空态，不发请求
- [V1 必做] `/product/manage/products?activityId=3916506` 正常加载并显示 banner
- [V1 必做] `/product/manage/products?activityId=99999999`（无权）显示禁止状态
- [V1 必做] 从 `/product/manage` 点活动行后 URL query 保留
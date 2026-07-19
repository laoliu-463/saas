# Real-Pre Frontend E2E Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose the real-pre Douyin integration status in the frontend and verify the full user-facing path with an automated browser report.

**Architecture:** Keep the frontend behind internal `/api` endpoints only. Add an admin-visible integration page that summarizes token, authorization subject, activity/product refresh, order sync, dashboard metrics, and the known shop-order permission blocker without changing Gateway DTO contracts.

**Tech Stack:** Vue 3, Vite, Naive UI, existing Axios request wrapper, existing Playwright runtime scripts in `runtime/qa`.

---

### Task 1: Add Real-Pre Frontend E2E Test

**Files:**
- Create: `runtime/qa/real-pre-douyin-frontend-e2e.cjs`

- [x] **Step 1: Write the failing browser test**

Create a Playwright script that logs in as `admin`, opens `http://localhost:3001/system/douyin`, clicks the page's "一键刷新联调状态" action, and expects these visible texts:

```text
Token 正常
授权主体正常
活动商品已刷新
订单同步成功
Dashboard 已读取真实订单
店铺侧订单权限待补齐
```

- [x] **Step 2: Run test to verify it fails**

Run:

```powershell
node runtime/qa/real-pre-douyin-frontend-e2e.cjs
```

Expected before implementation: fail because `/system/douyin` is not routed or the expected status texts are missing.

### Task 2: Wire Frontend Route And Menu

**Files:**
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/views/layout/Sider.vue`

- [x] **Step 1: Add route**

Add an admin route:

```text
/system/douyin -> frontend/src/views/ops/DouyinIntegration.vue
```

- [x] **Step 2: Add system menu item**

Add `抖店联调` under the existing system menu and map it as part of the system section.

- [x] **Step 3: Run build check**

Run:

```powershell
cd frontend
npm run build
```

Expected: `vue-tsc` and `vite build` complete successfully.

### Task 3: Build Integration Status Panel

**Files:**
- Modify: `frontend/src/api/douyin.ts`
- Modify: `frontend/src/views/ops/DouyinIntegration.vue`

- [x] **Step 1: Add API wrappers**

Add wrappers for:

```text
GET /douyin/institution-info
GET /douyin/activity-product-list
POST /douyin/promotion-link-probes/raw
```

- [x] **Step 2: Add page workflow**

Add one action named `一键刷新联调状态` that calls:

```text
GET /api/douyin/tokens
GET /api/douyin/institution-info
GET /api/douyin/activities
GET /api/douyin/activity-product-list?activityId=3916506&count=20
GET /api/colonel/activities/3916506/products?count=20&refresh=true
POST /api/orders/sync
GET /api/orders?page=1&size=5
GET /api/dashboard/metrics?timeField=createTime
POST /api/douyin/promotion-link-probes/raw with method=order.searchList
```

Use the current local timestamp to build a recent 30-minute order sync window. Treat `order.searchList` returning `30001 / isv.app-permissions-insufficient` as a visible warning state rather than a page failure.

- [x] **Step 3: Run the E2E test to verify green**

Run:

```powershell
node runtime/qa/real-pre-douyin-frontend-e2e.cjs
```

Expected after implementation: report shows all cases passed and writes a markdown report under `runtime/qa/out/real-pre-douyin-frontend-*`.

### Task 4: Update Evidence Docs

**Files:**
- Modify: `docs/archive/records/20-2026-05-08-新授权码三方全流程联调报告.md`
- Modify: `docs/04-开发进度.md`

- [x] **Step 1: Append frontend E2E result**

Record the frontend route, E2E report path, and pass count.

- [x] **Step 2: Re-run final verification**

Run:

```powershell
cd frontend
npm run build
node ../runtime/qa/real-pre-douyin-frontend-e2e.cjs
```

Expected: build succeeds and E2E report passes.

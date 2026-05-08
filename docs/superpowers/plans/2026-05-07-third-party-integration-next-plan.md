# Third Party Integration Next Plan Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Subagent-driven development is not the default here because AGENTS.md says real-pre integration closure favors centralized context unless task boundaries are very clear.

**Goal:** Continue real-pre third-party integration from the current proven state: token, activity, promotion, and order main sync are reachable; next close activity product freshness, product detail samples, attribution source, dashboard time semantics, and order detail/decryption permissions.

**Architecture:** Keep `test` / `local-mock` stable and let Real Gateway absorb upstream differences. Use raw probes only to discover upstream structure, then move stable mappings into Gateway or service DTO boundaries. Record every real-pre fact in `docs/09-真实SDK联调准备清单.md`, `docs/04-开发进度.md`, and `docs/archive/records/18-real-pre-API联调记录.md`.

**Tech Stack:** Spring Boot 3, Java 17, MyBatis Plus, Vue 3, Naive UI, Maven, Docker Compose real-pre on `3001/8081/5433/6380`.

---

## Current Facts To Preserve

- `real-pre` backend on `8081` is the current real upstream probe target.
- Do not start a second `3001` Vite or extra `8080` backend.
- `POST /api/orders/sync` already pulled 10 real orders and inserted 10 rows.
- `buyin.instituteOrderColonel` requires `yyyy-MM-dd HH:mm:ss` strings for `start_time` / `end_time`.
- `/api/orders` and `/api/dashboard/metrics?timeField=createTime` see the new real orders.
- `/api/data/orders` and default `settleTime` metrics do not see un-settled real orders.
- `order.orderDetail` and `order.searchList` currently fail with `30001 / isv.app-permissions-insufficient`.

## File Map

- Modify: `backend/src/main/java/com/colonel/saas/controller/ColonelActivityController.java`
  - Accept explicit activity product snapshot refresh requests and reuse existing service upsert/view methods.
- Modify: `backend/src/main/java/com/colonel/saas/gateway/douyin/real/RealDouyinProductGateway.java`
  - Harden product detail / SKU mapping if raw samples reveal field drift.
- Modify: `backend/src/main/java/com/colonel/saas/controller/DataController.java`
  - Make dashboard/data order time-field semantics explicit for un-settled real orders.
- Modify: `frontend/src/views/data/index.vue`
  - Keep the time-field control obvious and aligned with backend semantics.
- Modify: `frontend/src/views/data/OrderList.vue`
  - If chosen, allow `createTime` / `settleTime` switching for order detail list.
- Modify: `backend/src/main/java/com/colonel/saas/gateway/douyin/real/RealDouyinOrderGateway.java`
  - Add any additional attribution-field aliases discovered from real samples.
- Modify: `backend/src/main/java/com/colonel/saas/service/AttributionService.java`
  - Only extend attribution lookup after evidence identifies a stable upstream field.
- Modify: `backend/src/test/java/com/colonel/saas/gateway/douyin/real/RealDouyinOrderGatewayTest.java`
  - Add mapping tests for any new attribution/order fields.
- Modify: `backend/src/test/java/com/colonel/saas/controller/DataControllerTest.java`
  - Add tests for create-time vs settle-time data page behavior if changed.
- Modify docs:
  - `docs/03-Test与Real网关契约.md`
  - `docs/04-开发进度.md`
  - `docs/09-真实SDK联调准备清单.md`
  - `docs/10-V2.2场景覆盖矩阵.md`
  - `docs/archive/records/18-real-pre-API联调记录.md`

---

### Task 1: Activity Product Snapshot Freshness Decision

**Files:**
- Modify: `backend/src/main/java/com/colonel/saas/service/ProductService.java`
- Test: `backend/src/test/java/com/colonel/saas/controller/ColonelActivityControllerTest.java`
- Docs: `docs/09-真实SDK联调准备清单.md`, `docs/archive/records/18-real-pre-API联调记录.md`

- [x] **Step 1: Reproduce the current mismatch**

Run a real-pre API comparison without writing new data:

```powershell
$login = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8081/api/auth/login' -ContentType 'application/json' -Body '{"username":"admin","password":"admin123"}'
$headers = @{ Authorization = "Bearer $($login.data.token)" }
Invoke-RestMethod -Method Get -Uri 'http://127.0.0.1:8081/api/douyin/activity-product-list?activityId=3916506&count=20' -Headers $headers
Invoke-RestMethod -Method Get -Uri 'http://127.0.0.1:8081/api/colonel/activities/3916506/products?count=20' -Headers $headers
```

Expected: raw upstream returns 20 rows with `next_cursor`; business endpoint currently returns local snapshot rows.

- [x] **Step 2: Choose one freshness rule**

Use this rule unless product owner says otherwise:

```text
If real-pre requests an activity product business endpoint with refresh=true, fetch upstream, upsert snapshots, then return the refreshed business view. Without refresh=true, keep current snapshot-first behavior.
```

- [x] **Step 3: Write the failing test**

Added a controller test for the actual business endpoint path. The final assertion is:

```java
verify(productService, never()).hasActivitySnapshots("100018");
verify(productService).upsertSnapshots(eq("100018"), eq(gatewayResult.items()));
```

Expected before implementation: fail because `refresh` is not wired into the business endpoint path.

- [x] **Step 4: Implement minimal refresh behavior**

Modify only the controller/service path that serves `/api/colonel/activities/{activityId}/products` so it accepts an explicit refresh option and calls existing `upsertSnapshots(activityId, result.items())` before building the business view.

- [x] **Step 5: Verify**

Run:

```powershell
cd D:\Projects\SAAS\backend
mvn "-Dtest=ColonelActivityControllerTest" test
```

Result: `4 tests, 0 failures, 0 errors`.

- [x] **Step 6: Real-pre smoke**

Run:

```powershell
$login = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8081/api/auth/login' -ContentType 'application/json' -Body '{"username":"admin","password":"admin123"}'
$headers = @{ Authorization = "Bearer $($login.data.token)" }
Invoke-RestMethod -Method Get -Uri 'http://127.0.0.1:8081/api/colonel/activities/3916506/products?count=20&refresh=true' -Headers $headers
```

Result: refresh smoke passed. Evidence directory: `runtime/qa/out/activity-product-refresh-real-20260507-210819`; default business view moved from 10 rows to 20 rows after `refresh=true`.

---

### Task 2: Real Product Detail And SKU Sample

**Files:**
- Modify: `backend/src/main/java/com/colonel/saas/gateway/douyin/real/RealDouyinProductGateway.java`
- Test: `backend/src/test/java/com/colonel/saas/gateway/douyin/real/RealDouyinProductGatewayTest.java`
- Docs: `docs/09-真实SDK联调准备清单.md`, `docs/archive/records/18-real-pre-API联调记录.md`

- [x] **Step 1: Pick a known product id**

Used `3780271777075298337` from the refreshed upstream sample and `3810562766247428542` because it already generated a real promotion link.

- [x] **Step 2: Call existing detail path**

Run the product detail endpoint used by the app, not a new page-only shortcut:

```powershell
$login = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8081/api/auth/login' -ContentType 'application/json' -Body '{"username":"admin","password":"admin123"}'
$headers = @{ Authorization = "Bearer $($login.data.token)" }
Invoke-RestMethod -Method Get -Uri 'http://127.0.0.1:8081/api/products/3810562766247428542' -Headers $headers
```

Result: activity product business detail returns HTTP 200 / `code=200` for both product IDs, but this is a snapshot/business-state view and does not contain SKU detail.

- [x] **Step 3: Add mapping tests only if field drift appears**

No mapping test added yet. Raw `product.detail` probes returned `30001 / isv.app-permissions-insufficient`, so no upstream detail/SKU payload is available to fixture.

```java
assertThat(result.productId()).isEqualTo("3810562766247428542");
assertThat(result.productName()).isNotBlank();
assertThat(result.skus()).isNotNull();
```

- [x] **Step 4: Implement only observed aliases**

No code change: no observed upstream aliases. Current result is a permission gap, not field drift.

- [x] **Step 5: Verify**

Run:

```powershell
cd D:\Projects\SAAS\backend
mvn "-Dtest=RealDouyinProductGatewayTest,ProductControllerTest,ProductServiceTest" test
```

Result: no mapper change needed. Evidence directories: `runtime/qa/out/product-detail-real-20260507-210924` and `runtime/qa/out/product-detail-raw-probes-20260507-210941`.

---

### Task 3: Order Attribution Evidence Pass

**Files:**
- Modify: `backend/src/main/java/com/colonel/saas/gateway/douyin/real/RealDouyinOrderGateway.java`
- Modify: `backend/src/main/java/com/colonel/saas/service/AttributionService.java`
- Test: `backend/src/test/java/com/colonel/saas/gateway/douyin/real/RealDouyinOrderGatewayTest.java`
- Test: `backend/src/test/java/com/colonel/saas/service/AttributionServiceTest.java`
- Docs: `docs/archive/records/18-real-pre-API联调记录.md`

- [x] **Step 1: Inspect raw payload for attribution candidates**

Use already stored payloads first:

```powershell
Get-Content -Raw D:\Projects\SAAS\runtime\qa\out\orders-sync-real-20260507-203422\orders-page-after-sync.json
```

Result: stored real orders contain no `pick_source / pick_extra`, but do contain stable `author_buyin_id / author_account / author_short_id`. Evidence directory: `runtime/qa/out/orders-attribution-evidence-20260507-211000`.

- [x] **Step 2: If no field exists, document no-code result**

No channel attribution candidate exists. The no-channel-code conclusion is:

```text
Current buyin.instituteOrderColonel samples do not contain pick_source/pick_extra/equivalent channel attribution fields. Channel attribution requires order detail permission, another upstream report/export, or a confirmed upstream parameter echo.
```

- [x] **Step 3: If a field exists, write mapper test**

Added a `RealDouyinOrderGatewayTest` case using `author_buyin_id / author_account` and an `AttributionServiceTest` case for exclusive talent lookup:

```java
assertThat(item.talentId()).isEqualTo("7137334329718292775");
assertThat(item.talentName()).isEqualTo("哆咪哆零食");
```

- [x] **Step 4: Implement alias**

Implemented only observed aliases:

```text
author_buyin_id -> talentUid / talentId candidate
author_account -> talentName
```

Also fixed repeat-sync persistence so `extra_data` updates cast to jsonb and `talent_name` is included in the custom insert/update SQL.

- [x] **Step 5: Verify**

Run:

```powershell
cd D:\Projects\SAAS\backend
mvn "-Dtest=RealDouyinOrderGatewayTest,AttributionServiceTest,OrderSyncServiceTest" test
```

Result: `mvn "-Dtest=OrderSyncPersistenceServiceTest,RealDouyinOrderGatewayTest,AttributionServiceTest" test` passed (`13 tests, 0 failures, 0 errors`). Real-pre replay of the same order window returned `updated=10 / failed=0`, with 10 real `talentName` values visible; channel attribution remains `UNATTRIBUTED / NO_PICK_SOURCE` because no `pick_source / pick_extra` exists in the upstream payload.

---

### Task 4: M1.6 Dashboard Time Semantics

**Files:**
- Modify: `backend/src/main/java/com/colonel/saas/controller/DataController.java`
- Modify: `frontend/src/views/data/index.vue`
- Modify: `frontend/src/views/data/OrderList.vue`
- Test: `backend/src/test/java/com/colonel/saas/controller/DataControllerTest.java`
- Docs: `docs/04-开发进度.md`, `docs/10-V2.2场景覆盖矩阵.md`

- [ ] **Step 1: Lock the business rule**

Use this rule unless overridden:

```text
Dashboard overview defaults to createTime for operational real-time visibility. Settlement/commission-specific figures continue to expose settleTime as a selectable filter.
```

- [ ] **Step 2: Add backend test for timeField defaults**

In `DataControllerTest`, create one un-settled order with `create_time` today and `settle_time` null or outside today. Assert:

```java
mockMvc.perform(get("/dashboard/metrics"))
        .andExpect(jsonPath("$.data.todayOrderCount").value(1));

mockMvc.perform(get("/dashboard/metrics").param("timeField", "settleTime"))
        .andExpect(jsonPath("$.data.todayOrderCount").value(0));
```

- [ ] **Step 3: Implement backend default if test fails**

Change `resolveTimeColumn` default from `settle_time` to `create_time` only for endpoints where operational visibility is intended. Keep export/settlement views explicit.

- [ ] **Step 4: Align frontend labels**

In `frontend/src/views/data/index.vue`, label the default as “下单时间” and the alternate as “结算时间”. Avoid explanatory body text; use concise control labels.

- [ ] **Step 5: Verify**

Run:

```powershell
cd D:\Projects\SAAS\backend
mvn "-Dtest=DataControllerTest" test
cd D:\Projects\SAAS\frontend
npm run build
```

Expected: backend test passes and frontend build succeeds.

---

### Task 5: Order Detail And Decryption Permission Runbook

**Files:**
- Modify: `docs/09-真实SDK联调准备清单.md`
- Modify: `docs/archive/records/18-real-pre-API联调记录.md`
- Modify: `docs/06-部署与对接计划.md`

- [ ] **Step 1: List exact external asks**

Record these required platform items:

```text
1. 订单管理接口权限包
2. 店铺授权
3. 物流商授权
4. 敏感数据解密权限包
5. 可用于 order.batchDecrypt 的 cipher_infos 样本来源
```

- [ ] **Step 2: Prepare repeatable probes**

Keep these current probes as the permission verification suite:

```powershell
POST /api/douyin/promotion-link-probes/raw with method=order.orderDetail
POST /api/douyin/promotion-link-probes/raw with method=order.searchList
POST /api/orders/phone-decryptions
```

- [ ] **Step 3: Define success criteria**

Use:

```text
order.orderDetail or order.searchList returns receiver cipher fields, then order.batchDecrypt returns masked/virtual phone display data through /api/orders/phone-decryptions without logging plaintext.
```

- [ ] **Step 4: Do not change code until permissions change**

Expected: no backend code changes for this task unless platform permission results change from `30001` to a successful response.

---

### Task 6: Real-Pre Regression Gate

**Files:**
- Docs only unless regression fails:
  - `docs/04-开发进度.md`
  - `docs/archive/records/18-real-pre-API联调记录.md`

- [ ] **Step 1: Backend focused tests**

Run:

```powershell
cd D:\Projects\SAAS\backend
mvn "-Dtest=DouyinControllerTest,RealDouyinOrderGatewayTest,RealDouyinProductGatewayTest,ProductServiceTest,DataControllerTest,OrderSyncServiceTest,AttributionServiceTest" test
```

Expected: all targeted tests pass.

- [ ] **Step 2: Frontend build**

Run:

```powershell
cd D:\Projects\SAAS\frontend
npm run build
```

Expected: build succeeds.

- [ ] **Step 3: Real-pre narrow smoke**

Run only non-destructive reads unless explicitly testing a narrow sync window:

```powershell
GET /api/douyin/tokens
GET /api/douyin/institution-info
GET /api/colonel/activities/3916506/products?count=20
GET /api/orders?page=1&pageSize=5
GET /api/dashboard/metrics?timeField=createTime
GET /api/dashboard/metrics?timeField=settleTime
```

Expected: all return HTTP 200; differences between create time and settle time are documented.

- [ ] **Step 4: Documentation update**

Update the four canonical docs with exact evidence paths:

```text
docs/04-开发进度.md
docs/09-真实SDK联调准备清单.md
docs/10-V2.2场景覆盖矩阵.md
docs/archive/records/18-real-pre-API联调记录.md
```

---

## Execution Order

1. Task 1: Activity product snapshot freshness.
2. Task 2: Product detail/SKU sample.
3. Task 4: Dashboard time semantics.
4. Task 3: Order attribution evidence pass.
5. Task 5: Permission runbook for order detail/decryption.
6. Task 6: Regression gate.

This order keeps external permission blockers from stopping work that can be completed locally and in real-pre today.

## Self-Review

- Spec coverage: covers activity products, product details, order attribution, dashboard time semantics, order permission blockers, and regression.
- Placeholder scan: no `TBD`, no open-ended “add tests” without commands.
- Type consistency: all named files and endpoints exist in the current repo or recent real-pre evidence.

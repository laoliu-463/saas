# #154 Frontend API / Store Boundary Inventory

## Metadata

- Time: 2026-06-28 11:47:17 +08:00
- Issue: #154 `[DDD-COMPLETE-100-FRONTEND-01]`
- Scope: docs / inventory
- Environment: local repo, real-pre rules read
- Code changed: no

## Evidence Commands

- `mcp__code_review_graph.semantic_search_nodes`: no frontend API/store node hit; fell back to source scan.
- `rg --files frontend/src`
- `Select-String frontend/src/api/*.ts "request\\.(get|post|put|delete|patch)\\("`
- `Select-String frontend/src/stores/*.ts "defineStore|api/|request|localStorage|dataScope"`
- `rg "api/(activity|activityProduct|auth|commission|dashboard|data|douyin|merchant|order|performance|product|productManage|ruleCenter|sample|sys|talent)" frontend/src`
- `rg "fetch\\(|axios|request\\." frontend/src --glob '!frontend/src/api/**' --glob '!frontend/src/**/*.test.ts'`

## API Client Inventory

| Domain | API files | Main internal paths | Main consumers |
| --- | --- | --- | --- |
| Product / Activity | `activity.ts`, `activityProduct.ts`, `product.ts`, `productManage.ts`, `merchant.ts` | `/activities`, `/colonel/activities`, `/colonel/activities/*/products`, `/products`, `/products/manage`, `/colonel/partners` | `views/product/*`, quick sample modal |
| Order | `order.ts`, `data.ts` | `/orders`, `/orders/unattributed`, `/orders/stats`, `/orders/filter-options`, `/data/orders`, `/orders/exports` | `views/orders/*`, `views/data/OrderList.vue` |
| Analytics | `dashboard.ts`, `data.ts`, `performance.ts` | `/dashboard/summary`, `/dashboard/metrics`, `/performance/summary`, `/performance/export` | `views/dashboard`, `views/data` |
| Talent | `talent.ts` | `/talents`, `/talents/pools/*`, `/talents/*/shipping-address`, `/talents/*/claims`, `/talents/*/exclusive-status` | `views/talent/*`, sample/product modals |
| Sample | `sample.ts` | `/samples`, `/samples/filter-options`, `/samples/*/status`, `/samples/board`, `/samples/logistics/*` | `views/sample/*` |
| User / Auth | `auth.ts`, `sys.ts` | `/auth/*`, `/users`, `/users/current`, `/users/master-data/*`, `/roles`, `/depts` | `Login.vue`, router/layout, profile, system pages, user option helpers |
| Config | `sys.ts`, `commission.ts`, `ruleCenter.ts` | `/configs`, `/commission-rules`, `/rule-center/*`, `/operation-logs` | `views/system/*` |
| Douyin Ops Gateway | `douyin.ts` | `/douyin/*` under internal `/api` baseURL | `views/ops/DouyinIntegration.vue`, activity list institution probe |

## Store Inventory

- `stores/auth.ts`: auth token, refresh token, userInfo, normalized roleCodes and dataScope only. It persists auth context in `localStorage`.
- `stores/app.ts`: app UI state only.
- `stores/permissionHint.ts`: global permission hint ref only.
- No domain Pinia store exists for product, order, analytics, talent, sample, user management, or config. Domain data is currently held in page/component state and fetched through API modules.

## Boundary Findings

- `utils/request.ts` sets `baseURL: '/api'` for normal and refresh clients; API modules call internal backend paths, not external open-platform URLs directly.
- Direct non-wrapper calls are limited to internal endpoints: `useRuntimeEnvironment.ts` and `Header.vue` call `/api/system/env`, and `Header.vue` calls `/api/auth/logout`.
- `api/douyin.ts` calls internal `/douyin/**` gateway endpoints through the same `/api` base URL. It does not call open-platform hosts directly.
- External host strings are display/navigation/template examples, such as `buyin.jinritemai.com` activity detail/power-manage URLs and `v.douyin.com/example` template placeholders.
- Frontend still contains UI-side role/status helpers (`constants/rbac.ts`, router meta roles, product/sample status display helpers). They gate UI and display, but final authority must remain backend-side; cleanup belongs to #155/#158.

## Follow-up Routing

- #155: remove or classify frontend hardcoded business rules, permission rules, and state machines.
- #156: align product/order/analytics pages to domain API contracts.
- #157: align talent/sample pages to domain API contracts.
- #158: make permission/data-scope UI explicitly backend-authoritative.
- #159: produce Playwright E2E evidence across frontend domains.

## Conclusion

#154 inventory is complete. Current evidence proves frontend API clients and stores are inventoried by domain, with risks routed to the remaining frontend issues. This report does not claim the hardcoded UI rules are already cleaned.

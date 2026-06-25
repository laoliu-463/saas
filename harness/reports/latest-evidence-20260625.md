# Evidence Report - 2026-06-25

## Scope
- Env: local `real-pre`
- Branch: `feature/product-manage-fallback-fix-20260623`
- Base commit at validation: `69c445ed`
- Validated changes: current product-library working tree changes on top of `69c445ed`
- Validated changes were later committed in `926c4df7`
- Remote deploy: not executed

## Problem
- Product library page requested `/api/products?page=1&size=500`.
- Before local redeploy, backend returned HTTP `200` with business `code=400`.
- Error message was `must be less than or equal to 100`, so the frontend showed no product cards.

## Root Cause Evidence
- Browser QA reproduced the failure before redeploy:
  - URL: `/api/products?page=1&size=500&sortBy=default`
  - business code: `400`
  - message: `must be less than or equal to 100`
- Extracted running container jar showed `ProductController.page(...)` parameter `size` still had `@Max(100)`.
- Current source has removed the main `/api/products` `@Max(100)`; remaining `@Max(100)` annotations are on other endpoints such as pick page and promotion-link history.

## Actions
- Backend package:
  - `mvn -f backend/pom.xml -DskipTests package`
  - Result: `BUILD SUCCESS`
- Backend image/container:
  - `docker build -t colonel-saas/backend:real-pre ./backend`
  - `docker compose --env-file .env.real-pre -f docker-compose.real-pre.yml up -d --no-deps --force-recreate backend-real-pre`
  - Result: backend `healthy`
- Frontend build/container:
  - `npm --prefix frontend run build`
  - `docker compose --env-file .env.real-pre -f docker-compose.real-pre.yml up -d --no-deps --build --force-recreate frontend-real-pre`
  - Result: frontend `healthy`

## Validation
- Backend targeted test:
  - `mvn -f backend/pom.xml "-Dtest=ProductControllerTest#selectedLibraryPageSize_shouldNotUseHundredRowValidationLimit" test`
  - Result: tests `1`, failures `0`, errors `0`
- Frontend targeted tests:
  - `node node_modules/vitest/vitest.mjs run src/views/product/ProductLibrary.test.ts`
  - Result: tests `3`, passed `3`
  - `node node_modules/vitest/vitest.mjs run src/views/product/product-filters.test.ts`
  - Result: tests `33`, passed `33`
- Frontend production build:
  - `npm --prefix frontend run build`
  - Result: success
- Browser QA:
  - Page: `http://127.0.0.1:3001/product`
  - Login: `admin`, success
  - Product API: `/api/products?page=1&size=500`
  - HTTP status: `200`
  - business code: `200`
  - records length: `500`
  - total: `15013`
  - page text: `已加载 500 / 15013 件`
  - no `must be less than or equal to 100` / `不超过100` error
  - product library sort control not visible
- Final evidence files:
  - `runtime/qa/out/product-library-final-redeploy-2026-06-25T043102202Z/product-page.png`
  - `runtime/qa/out/product-library-final-redeploy-2026-06-25T043102202Z/summary.json`

## Additional Test Signal
- `ProductDisplayPolicyTest` passed `22` tests.
- Expanded `ProductServiceFilterTest` run has one failing activity-product status-4 assertion:
  - `buildActivityProductListViewFromDb_shouldReturnEmptyForUnsupportedPromotionStatusFour`
  - This is outside the verified `/api/products` product library main-list path and must not be reported as a full backend-suite pass.

## Conclusion
- Result: `PASS` for local `real-pre` product library 100-limit removal and page display validation.
- Cause: frontend had been updated to request `size=500`, but the running local backend container still used an old jar with `@Max(100)`.
- Remaining risk: broader backend `ProductServiceFilterTest` status-4 behavior needs a separate decision/fix before claiming full backend regression coverage.

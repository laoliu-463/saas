# ORDER-PRODUCT-INFO-FULLSTACK-001 Evidence

## Metadata

- Time: 2026-06-04 16:05 +08:00
- Environment: local real-pre
- Branch: feature/auth-system
- Implementation commit: f601a70c
- Latest pushed head before final evidence cleanup: 981bd60f
- Remote deploy: not requested
- Scope: full
- Conclusion: PASS

## Scope Control

- Kept changes inside order product-info display chain and channel wording.
- Did not add database columns or migrations.
- Did not change Docker or `.env` files.
- Did not change order sync persistence, settlement amount calculation, commission calculation, or performance attribution rules.
- Reverted the prior out-of-scope `OrderSyncService` product field persistence and `migrate-all.sql` column additions from the working diff.

## Changed Areas

- Backend `/orders`: `OrderController`, `OrderService`, `ColonelsettlementOrderMapper`, `ColonelsettlementOrderMapper.xml`, `ColonelsettlementOrder`.
- Backend `/data/orders`: `OrderVO`, `DataApplicationService`.
- Frontend `/orders`: `frontend/src/views/orders/index.vue`, `frontend/src/views/orders/index.test.ts`.
- Tests: order controller/service focused tests and order page vitest coverage.

## Verification

| Check | Result | Evidence |
| --- | --- | --- |
| code-review-graph minimal context | PASS | 9852 nodes, risk low |
| backend focused tests | PASS | `mvn -f backend/pom.xml clean "-Dtest=OrderServiceTest,OrderControllerTest,OrderSyncControllerTest" test` -> 48/48 |
| frontend order page tests | PASS | `npm --prefix frontend run test -- --run src/views/orders/index.test.ts` -> 32/32 |
| backend package | PASS | `mvn -f backend/pom.xml -DskipTests package` |
| frontend typecheck | PASS | `npm --prefix frontend run typecheck` |
| frontend build | PASS | `npm --prefix frontend run build` |
| Harness full flow | PASS | `agent-do.ps1 -Env real-pre -Scope full` |
| Docker restart | PASS | backend/frontend real-pre rebuilt and recreated |
| Health check | PASS | backend `/api/system/health` 200, frontend `/healthz` 200 |
| real-pre preflight | PASS | `runtime/qa/out/real-pre-preflight-20260604-160009/report.md` |
| `/api/orders` field probe | PASS | code=200, total=545, first row has product image/title/id/shop/quantity/rates |
| `/orders` page smoke | PASS | `runtime/qa/out/qa-page-smoke-20260604-160450-367/report.md` |

## Runtime Field Evidence

`GET http://127.0.0.1:8081/api/orders?page=1&size=5` returned:

- `code=200`
- `total=545`
- first row `orderId=6953409338543314892`
- product fields present: `productId`, `productName`, `productTitle`, `productPic`, `productImage`, `shopName`, `itemNum`, `productQuantity`, `commissionRate`, `serviceFeeRate`

The `/orders` page smoke body excerpt included:

- `商品信息`
- `商品ID`
- `店铺`
- `商品数量`
- `佣金率`
- `服务费率`
- `渠道`

## Residual Risks

- Page smoke still records a Google Fonts CSP console warning. It does not block `/orders` content rendering and is outside this task.
- `npm ci` during Harness reported 2 critical dependency vulnerabilities. This task did not touch dependency versions.
- `evidence-20260604-160012.md` and `evidence-20260604-160333.md` were generated before their corresponding automatic commits, so their metadata commit hash can lag the pushed HEAD. The implementation commit emitted by Harness is `f601a70c`; `981bd60f` only added follow-up Harness reports.

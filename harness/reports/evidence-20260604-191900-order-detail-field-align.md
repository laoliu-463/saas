# Evidence Report: order detail field alignment

## Metadata

- Time: 2026-06-04 19:19 +08:00
- Environment: local `real-pre`
- Branch: `feature/auth-system`
- Code commit: `abf3f9eb fix: align order detail table fields`
- Remote deploy: not requested

## Scope

- Convert data platform order display from date-level summary-only usage to an order-level detail table while preserving the existing summary module.
- Align frontend, backend DTO/query/export fields to the 16 target headers.
- Rename visible/order-page wording from “媒介” to “渠道”.
- Keep V1 gross profit hidden by default on the order data page.

## Verification

- Backend targeted tests: `mvn -f .\backend\pom.xml "-Dtest=DataControllerTest,OrderControllerTest,OrderServiceTest,PerformanceRecordMapperTest" test` -> PASS, 99 tests.
- Frontend targeted tests: `npm run test -- OrderDetailTab.test.ts OrderList.test.ts order-list-query.test.ts orders/index.test.ts` -> PASS, 43 tests.
- Backend package: `mvn -f .\backend\pom.xml -DskipTests package` -> BUILD SUCCESS.
- Frontend build: `npm run build` -> PASS; Vite chunk-size warning only.
- Naming regression: `rg -n "媒介" frontend/src backend/src docs --glob '!docs/归档/**' --glob '!docs/archive/**' -S` -> no active-source hits.
- Legacy media field regression: `rg -n "mediaName|mediatorName|media_id|mediaUser|media_user|mediator" frontend/src/views/orders backend/src/main/java backend/src/test/java -S` -> no hits.
- Harness full run: `powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\agent-do.ps1 -Env real-pre -Scope full -Message "fix: align order detail table fields"` -> PASS.
- Harness generated evidence: `harness/reports/evidence-20260604-191102.md`.
- Harness generated retro: `harness/reports/retro-20260604-191127.md`.

## Page Smoke

- Tooling: Playwright via local Node, because the in-app Browser navigation tool was not exposed in this session.
- URL: `http://127.0.0.1:3001/data/orders`.
- Login role: admin.
- Detail API: `/api/data/orders/detail?page=1&size=20` -> HTTP 200.
- API sample: total `738`, records `20`.
- Page result: PASS.
- Smoke result JSON: `runtime/qa/out/order-detail-page-smoke-20260604-191711/result.json`.
- Screenshot: `runtime/qa/out/order-detail-page-smoke-20260604-191711/order-detail-table.png`.

## Page Assertions

- Required headers all visible: 订单ID、活动信息、商品信息、合作方信息、推广者、渠道、招商、订单状态、订单额、服务费收入、技术服务费、服务费支出、服务费收益、招商提成、渠道提成、订单时间.
- Product information visible with image/name/id.
- Export button visible.
- Custom column button visible.
- Page does not contain “媒介”.
- Page does not contain “毛利”.
- Unsettled sample `6953418877360936032` displays `结算：-`.
- Critical failed browser requests: none.

## Non-blocking Browser Noise

- Google Fonts request was blocked by CSP. This is existing external-font noise and did not affect local order-detail API calls or table rendering.

## Conclusion

PASS. The local `real-pre` order detail table is deployed, healthy, and browser-smoke verified. Remote deployment was not requested.

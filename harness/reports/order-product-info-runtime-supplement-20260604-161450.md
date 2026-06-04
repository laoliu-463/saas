# ORDER-PRODUCT-INFO-FULLSTACK-001 Runtime Supplement

## Metadata

- Time: 2026-06-04 16:14 +08:00
- Environment: local real-pre
- Branch: feature/auth-system
- Current pushed head before this supplement: 6feb81c5
- Remote deploy: not requested

## Purpose

This supplement records targeted runtime probes that were executed after the main Harness evidence report.
Tokens were used only in-memory and were not printed.

## `/api/orders` Probe

- Request: `GET http://127.0.0.1:8081/api/orders?page=1&size=5`
- Result: `code=200`
- `total=545`
- `recordCount=5`
- first row `orderId=6953409338543314892`
- first row included:
  - `productId`
  - `productName`
  - `productTitle`
  - `productPic`
  - `productImage`
  - `shopName`
  - `itemNum`
  - `productQuantity`
  - `commissionRate`
  - `serviceFeeRate`

## `/api/data/orders` Probe

- Request: `GET http://127.0.0.1:8081/api/data/orders?page=1&size=20`
- Result: `code=200`
- `total=557`
- `recordCount=20`
- `productImageCount=20`
- `productQuantityCount=20`
- `commissionRateCount=20`
- `serviceFeeRateCount=0`
- `channelNameCount=0`

Interpretation:

- Data dashboard order rows return product image, quantity, and commission rate in the current real-pre sample set.
- `serviceFeeRate` and `channelName` are absent from the sampled JSON because the underlying values are null/empty in current real-pre data and the response omits null fields.
- This is not evidence of a display-chain compile failure; it is a data availability limitation in the sampled runtime data.

## `/orders` Page Smoke

- Script: `scripts/qa-page-smoke.ps1`
- `QA_FRONTEND=http://127.0.0.1:3001`
- Route: `/orders`
- Report: `runtime/qa/out/qa-page-smoke-20260604-160450-367/report.md`
- Result:
  - `opened=true`
  - `content=true`
  - `403=false`
  - `500=false`
  - `loadingStuck=false`
  - body excerpt includes `商品信息`, `商品ID`, `店铺`, `商品数量`, `佣金率`, `服务费率`, `渠道`

## Residual Runtime Notes

- Current real-pre sampled rows do not contain channel attribution values, so channel non-empty rendering remains covered by frontend unit tests rather than live data.
- Page smoke reported a Google Fonts CSP console warning; it did not block `/orders` rendering.

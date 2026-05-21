# E2E Mock Fixtures

这些 fixture 为 Playwright mock 提供稳定数据基线。每个域至少保留空态、单条或多条、边界、异常数据，新增用例优先通过 `tests/e2e/helpers/fixtures.ts` 读取，避免在 spec 内散落重复样本。

示例：

```ts
const summary = readFixture('dashboard', 'summary.json');
await page.route('**/api/dashboard/summary**', route => route.fulfill({ json: { code: 200, data: summary } }));
```

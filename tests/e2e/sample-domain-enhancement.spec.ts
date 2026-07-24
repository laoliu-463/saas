import { test, expect } from '@playwright/test';
import { gotoApp } from './helpers/page-ready';

/**
 * 寄样域 V2.0 增强项 E2E（filter-options / 领域事件 / LOCAL_FALLBACK / 物流诊断）。
 * 默认 skip：需 test 或 real-pre 后端与测试账号。
 */
test.describe('Sample domain enhancement', () => {
  test.skip(true, 'Enable when backend + auth fixtures are available');

  test('loads filter-options on sample page', async ({ page }) => {
    await gotoApp(page, '/sample');
    const response = await page.waitForResponse((res) =>
      res.url().includes('/samples/filter-options') && res.status() === 200
    );
    const body = await response.json();
    expect(body.data.statuses?.length).toBeGreaterThan(0);
  });
});

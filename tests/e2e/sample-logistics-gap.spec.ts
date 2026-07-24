import { test, expect } from '@playwright/test';
import { gotoApp } from './helpers/page-ready';

/**
 * 寄样域 V2.0 物流/导入/快速寄样 fallback 专项（test）。
 * 依赖运营账号与寄样测试数据；与主 E2E 套件解耦。
 */
test.describe('Sample logistics gap (mock)', () => {
  test.skip(true, 'Requires seeded ops user and sample fixtures — run in test or real-pre harness');

  test('ops can open logistics import modal', async ({ page }) => {
    await gotoApp(page, '/ops/shipping');
    await page.getByTestId('ops-logistics-import').click();
    await expect(page.getByTestId('sample-logistics-import-modal')).toBeVisible();
    await expect(page.getByTestId('logistics-import-template')).toBeVisible();
  });
});

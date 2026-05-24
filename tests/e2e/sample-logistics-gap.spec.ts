import { test, expect } from '@playwright/test';

/**
 * 寄样域 V2.0 物流/导入/快速寄样 fallback 专项（local-mock）。
 * 依赖运营账号与寄样测试数据；与主 E2E 套件解耦。
 */
test.describe('Sample logistics gap (mock)', () => {
  test.skip(true, 'Requires seeded ops user and sample fixtures — run in real-pre/local-mock harness');

  test('ops can open logistics import modal', async ({ page }) => {
    await page.goto('/ops/shipping');
    await page.getByTestId('ops-logistics-import').click();
    await expect(page.getByTestId('sample-logistics-import-modal')).toBeVisible();
    await expect(page.getByTestId('logistics-import-template')).toBeVisible();
  });
});

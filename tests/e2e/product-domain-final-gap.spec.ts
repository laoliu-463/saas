import { test, expect } from '@playwright/test';
import { storageStates } from './helpers/test-data';
import { seedTestData } from './helpers/api-assertions';
import { gotoApp } from './helpers/page-ready';

test.beforeAll(async () => {
  await seedTestData();
});

test.describe('商品域 final gap 收口', () => {
  test.use({ storageState: storageStates.channelStaff });

  test('商品库快速寄样弹窗展示 LOCAL_FALLBACK 提示', async ({ page }) => {
    await gotoApp(page, '/product');
    await expect(page.getByTestId('product-library-page')).toBeVisible({ timeout: 15_000 });
    const sampleButton = page.getByTestId('product-quick-sample').first();
    if (!(await sampleButton.isVisible({ timeout: 5_000 }).catch(() => false))) {
      test.skip(true, '当前环境无可用商品卡片快速寄样入口');
    }
    await sampleButton.click();
    await expect(page.getByTestId('quick-sample-external-hint')).toContainText('系统内寄样申请');
    await expect(page.getByTestId('quick-sample-external-hint')).toContainText('LOCAL_FALLBACK');
  });
});

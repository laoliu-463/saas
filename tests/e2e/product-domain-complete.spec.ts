import { test, expect } from '@playwright/test';
import { storageStates } from './helpers/test-data';
import { seedTestData } from './helpers/api-assertions';
import { testIds } from './helpers/selectors';

test.beforeAll(async () => {
  await seedTestData();
});

test.describe('商品域完整链路', () => {
  test.use({ storageState: storageStates.channelLeader });

  test('商品库筛选与快速寄样入口可见', async ({ page }) => {
    await page.goto('/product');
    await expect(page.getByTestId(testIds.productLibraryPage)).toBeVisible({ timeout: 15_000 });
    await expect(page.getByTestId('product-library-checkbox-filters')).toBeVisible({ timeout: 15_000 });
    await expect(page.getByTestId('filter-colonel-name')).toBeVisible();
    const card = page.getByTestId(testIds.productCard).first();
    await expect(card).toBeVisible({ timeout: 30_000 });
    await card.hover();
    await expect(page.getByTestId('product-quick-sample')).toBeVisible();
  });

  test('快速寄样弹窗可打开', async ({ page }) => {
    await page.goto('/product');
    await expect(page.getByTestId(testIds.productGrid)).toBeVisible({ timeout: 30_000 });
    const card = page.getByTestId(testIds.productCard).first();
    await card.hover();
    await page.getByTestId('product-quick-sample').click();
    await expect(page.getByTestId('quick-sample-modal')).toBeVisible({ timeout: 10_000 });
    await expect(page.getByTestId('quick-sample-talents')).toBeVisible();
  });
});

test.describe('规则中心复制模板', () => {
  test.use({ storageState: storageStates.admin });

  test('管理员可见复制讲解模板编辑器', async ({ page }) => {
    await page.goto('/system/rule-center');
    await expect(page.getByTestId('rule-center-page')).toBeVisible({ timeout: 15_000 });
    await expect(page.getByTestId('promotion-template-editor')).toBeVisible({ timeout: 15_000 });
  });
});

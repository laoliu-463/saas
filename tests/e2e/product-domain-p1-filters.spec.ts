import { test, expect } from '@playwright/test';
import { storageStates } from './helpers/test-data';
import { seedTestData } from './helpers/api-assertions';
import { testIds } from './helpers/selectors';

test.beforeAll(async () => {
  await seedTestData();
});

test.describe('商品域 P1 筛选', () => {
  test.use({ storageState: storageStates.bizStaff });

  test('商品库动态类目与 P1 筛选项可见', async ({ page }) => {
    await page.goto('/product');
    await expect(page.getByTestId(testIds.productLibraryPage)).toBeVisible({ timeout: 15_000 });
    await expect(page.getByTestId('filter-library-categories')).toBeVisible();
    await expect(page.getByTestId('filter-colonel-name')).toBeVisible();
    await expect(page.getByTestId('filter-published')).toBeVisible();
    await expect(page.getByTestId('filter-listed')).toBeVisible();
    await expect(page.getByTestId('filter-free-sample')).toBeVisible();
    await expect(page.getByTestId('filter-material-download')).toBeVisible();
    await expect(page.getByTestId('filter-hand-card')).toBeVisible();
    await expect(page.getByTestId('filter-double-commission')).toBeVisible();
  });

  test('重置筛选恢复默认列表', async ({ page }) => {
    await page.goto('/product');
    await expect(page.getByTestId(testIds.productGrid)).toBeVisible({ timeout: 30_000 });
    await page.getByTestId('filter-colonel-name').fill('测试团长');
    await page.getByRole('button', { name: '查询' }).click();
    await page.getByRole('button', { name: '重置' }).click();
    await expect(page.getByTestId('filter-colonel-name')).toHaveValue('');
  });
});

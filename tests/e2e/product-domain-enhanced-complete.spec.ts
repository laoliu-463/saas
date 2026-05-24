import { test, expect } from '@playwright/test';
import { storageStates } from './helpers/test-data';
import { seedTestData } from './helpers/api-assertions';
import { testIds } from './helpers/selectors';

/**
 * 商品域增强完整验收（展示规则 / 团长主数据 / 全量筛选 / Outbox / 快速寄样）。
 * 需本地后端 8080 + 前端 3000 已启动，且 test profile 数据已 seed。
 */
test.beforeAll(async () => {
  await seedTestData();
});

test.describe('商品域增强 - 商品库与筛选', () => {
  test.use({ storageState: storageStates.bizStaff });

  test('全量筛选项 UI 可见', async ({ page }) => {
    await page.goto('/product');
    await expect(page.getByTestId(testIds.productLibraryPage)).toBeVisible({ timeout: 15_000 });
    await expect(page.getByTestId('product-library-checkbox-filters')).toBeVisible();
    await expect(page.getByTestId('filter-colonel-name')).toBeVisible();
  });
});

test.describe('商品域增强 - 快速寄样', () => {
  test.use({ storageState: storageStates.channelLeader });

  test('快速寄样弹窗提交或降级提示', async ({ page }) => {
    await page.goto('/product');
    const card = page.getByTestId(testIds.productCard).first();
    await expect(card).toBeVisible({ timeout: 30_000 });
    await card.hover();
    await page.getByTestId('product-quick-sample').click();
    await expect(page.getByTestId('quick-sample-modal')).toBeVisible({ timeout: 10_000 });
    await expect(page.getByTestId('quick-sample-external-hint')).toBeVisible();
  });
});

test.describe('商品域增强 - 管理员展示规则', () => {
  test.use({ storageState: storageStates.admin });

  test('展示规则审计 API 可访问', async ({ request }) => {
    const res = await request.get('/api/admin/products/display/audit-logs?page=1&size=5');
    expect(res.ok()).toBeTruthy();
  });
});

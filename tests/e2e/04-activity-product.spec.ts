import { test, expect } from '@playwright/test';
import { storageStates } from './helpers/test-data';
import { capturePage } from './helpers/screenshot';
import { testIds } from './helpers/selectors';
import { seedTestData } from './helpers/api-assertions';

test.use({ storageState: storageStates.bizLeader });

test.beforeAll(async () => {
  await seedTestData();
});

const loadedActivityProducts = /已加载\s+[1-9]\d*\s*个商品/;

test('活动列表和活动商品页可展示', async ({ page }, testInfo) => {
  await page.goto('/product/manage');
  await expect(page.getByTestId(testIds.activityListPage)).toBeVisible();
  await expect(page.getByRole('heading', { name: '活动列表' })).toBeVisible();
  await capturePage(page, testInfo, '04-activity-list');

  await page.getByTestId(testIds.activityViewProducts).first().click();
  await expect(page).toHaveURL(/\/product\/manage\/.+/);
  await expect(page.getByTestId('activity-product-page')).toBeVisible();
  await expect(page.getByTestId('activity-product-workbench')).toBeVisible();
  await expect(page.locator('.activity-workbench-subtitle')).toContainText(loadedActivityProducts, { timeout: 20_000 });
  await expect(page.getByTestId('product-table')).toBeVisible({ timeout: 20_000 });
  await expect(page.getByTestId('product-action-assign-audit-owner').first()).toBeVisible({ timeout: 20_000 });
  await expect(page.getByTestId('product-action-detail').first()).toBeVisible({ timeout: 20_000 });
  await expect(page.getByTestId('product-action-logs').first()).toBeVisible({ timeout: 20_000 });
  await capturePage(page, testInfo, '04-activity-products');
});

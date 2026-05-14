import { test, expect } from '@playwright/test';
import { storageStates } from './helpers/test-data';
import { capturePage } from './helpers/screenshot';
import { testIds } from './helpers/selectors';

test.use({ storageState: storageStates.bizLeader });

test('活动列表和活动商品页可展示', async ({ page }, testInfo) => {
  await page.goto('/product/manage');
  await expect(page.getByTestId(testIds.activityListPage)).toBeVisible();
  await expect(page.getByText(/活动列表/)).toBeVisible();
  await capturePage(page, testInfo, '04-activity-list');

  await page.getByTestId(testIds.activityViewProducts).first().click();
  await expect(page).toHaveURL(/\/product\/manage\/.+/);
  await expect(page.getByTestId('activity-product-page')).toBeVisible();
  await expect(page.getByTestId(testIds.productCard).first()).toBeVisible();
  await capturePage(page, testInfo, '04-activity-products');
});

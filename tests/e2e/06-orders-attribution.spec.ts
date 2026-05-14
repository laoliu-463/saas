import { test, expect } from '@playwright/test';
import { storageStates } from './helpers/test-data';
import { capturePage } from './helpers/screenshot';
import { testIds } from './helpers/selectors';

test.use({ storageState: storageStates.bizLeader });

test('订单归因页可展示真实订单与归因字段', async ({ page }, testInfo) => {
  await page.goto('/orders');
  await expect(page.getByTestId(testIds.ordersPage)).toBeVisible();
  await expect(page.getByTestId(testIds.orderRow).first()).toBeVisible();
  await expect(page.getByTestId(testIds.orderAttributionStatus).first()).toBeVisible();
  await expect(page.getByTestId(testIds.orderChannel).first()).toBeVisible();
  await capturePage(page, testInfo, '06-orders-attribution');
});

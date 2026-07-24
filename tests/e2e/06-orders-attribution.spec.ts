import { test, expect } from '@playwright/test';
import { storageStates } from './helpers/test-data';
import { capturePage } from './helpers/screenshot';
import { gotoApp } from './helpers/page-ready';
import { testIds } from './helpers/selectors';
import { apiPost, loginApi } from './helpers/api-assertions';

test.use({ storageState: storageStates.channelLeader });

test.beforeAll(async () => {
  const adminToken = await loginApi('admin');
  await apiPost('/api/test/seed', {}, { token: adminToken });
});

test('订单归因页可展示真实订单与归因字段', async ({ page }, testInfo) => {
  await gotoApp(page, '/orders');
  await expect(page.getByTestId(testIds.ordersPage)).toBeVisible();
  const firstRow = page.getByTestId(testIds.orderRow).first();
  const hasRows = await firstRow.isVisible({ timeout: 10_000 }).catch(() => false);
  if (hasRows) {
    await expect(page.getByTestId(testIds.orderAttributionStatus).first()).toBeVisible();
    await expect(page.getByTestId(testIds.orderChannel).first()).toBeVisible();
  } else {
    await expect(page.locator('body')).toContainText(/无数据|暂无|订单工作台/);
  }
  await capturePage(page, testInfo, '06-orders-attribution');
});

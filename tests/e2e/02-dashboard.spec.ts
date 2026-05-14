import { test, expect } from '@playwright/test';
import { storageStates } from './helpers/test-data';
import { capturePage } from './helpers/screenshot';
import { testIds } from './helpers/selectors';

test.use({ storageState: storageStates.admin });

test('数据平台指标卡片正常展示', async ({ page }, testInfo) => {
  await page.goto('/data');
  await expect(page.getByTestId(testIds.dashboardPage)).toBeVisible();
  await expect(page.getByTestId(testIds.dashboardMetricCards)).toBeVisible();
  await expect(page.locator('body')).toContainText(/订单|服务费|毛利|明细/);
  await capturePage(page, testInfo, '02-dashboard-fullpage');
});

import { test, expect } from '@playwright/test';
import { storageStates } from './helpers/test-data';
import { capturePage } from './helpers/screenshot';
import { testIds } from './helpers/selectors';

test.use({ storageState: storageStates.channelLeader });

test('寄样台可以展示状态与详情入口', async ({ page }, testInfo) => {
  await page.goto('/sample');
  await expect(page.getByTestId(testIds.samplePage)).toBeVisible();
  await expect(page.getByText(/待审核|待发货|快递中|已完成|已关闭/)).toBeVisible();
  await expect(page.getByTestId(testIds.sampleRow).first()).toBeVisible();
  await capturePage(page, testInfo, '07-sample-list');
});

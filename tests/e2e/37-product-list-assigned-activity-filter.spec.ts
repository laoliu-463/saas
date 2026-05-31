/**
 * 商品列表 / 商品库「已分配活动」筛选 UI 冒烟
 */
import { test, expect } from '@playwright/test';
import { gotoApp } from './helpers/page-ready';
import { storageStates } from './helpers/test-data';

test.describe('assigned activity product filter', () => {
  test('商品推进池展示招商活动下拉', async ({ browser }) => {
    const context = await browser.newContext({ storageState: storageStates.bizLeader });
    const page = await context.newPage();
    await gotoApp(page, '/product/manage/products');
    await expect(page.getByTestId('filter-assigned-activity')).toBeVisible();
    await context.close();
  });

  test('商品库展示招商活动下拉', async ({ browser }) => {
    const context = await browser.newContext({ storageState: storageStates.channelLeader });
    const page = await context.newPage();
    await gotoApp(page, '/product');
    await expect(page.getByTestId('filter-assigned-activity')).toBeVisible();
    await context.close();
  });
});

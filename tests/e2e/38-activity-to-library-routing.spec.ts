/**
 * ADR-003：活动列表 → 商品库 入口路由契约一致性
 *
 * 目标（用户原始需求第 10 条）：
 *   "从活动列表点击商品信息进入商品库，URL 与商品库活动筛选后的 URL 一致"
 *
 * 覆盖三件事：
 *   1. 活动列表「商品信息」按钮跳到 /product/library?activityId=xxx（query 参数 + 商品库）
 *   2. 商品库页面读到 query.activityId 后展示「已按活动筛选」提示
 *   3. 历史 path-param 入口（/product/manage/:activityId、/product/activity/:activityId）
 *      也被重定向到统一的 query 入口
 */
import { expect, test } from '@playwright/test';

import { gotoApp } from './helpers/page-ready';
import { testIds } from './helpers/selectors';
import { storageStates } from './helpers/test-data';

test.describe('activity-to-library routing contract (ADR-003)', () => {
  test('活动列表「商品信息」点击后跳转到 /product/library?activityId=...', async ({ browser }) => {
    const context = await browser.newContext({ storageState: storageStates.bizLeader });
    const page = await context.newPage();

    await gotoApp(page, '/product/manage');
    await expect(page.getByTestId(testIds.activityListPage)).toBeVisible();

    // 找到第一个「商品信息」按钮，捕获它的 data-row 用来取 activityId
    const firstViewButton = page.getByTestId(testIds.activityViewProducts).first();
    await expect(firstViewButton).toBeVisible();
    await firstViewButton.click();

    // 1) URL 必须是商品库（不是 /product/manage/:id 也不是商品推进池）
    await expect(page).toHaveURL(/\/product\/library\?activityId=.+/);

    // 2) 商品库页面已挂载
    await expect(page.getByTestId(testIds.productLibraryPage)).toBeVisible();

    // 3) 「已按活动筛选」提示条出现
    const banner = page.getByTestId(testIds.productLibraryActivityFilterApplied);
    await expect(banner).toBeVisible();

    // 4) 提示条上的 activityId 与 URL 上的一致
    const url = new URL(page.url());
    const urlActivityId = url.searchParams.get('activityId') || '';
    expect(urlActivityId).toBeTruthy();
    await expect(banner).toContainText(urlActivityId);

    await context.close();
  });

  test('直接打开 /product/library?activityId=xxx 也会展示「已按活动筛选」提示', async ({ browser }) => {
    const context = await browser.newContext({ storageState: storageStates.bizLeader });
    const page = await context.newPage();

    await gotoApp(page, '/product/library?activityId=act-E2E-1001');
    await expect(page.getByTestId(testIds.productLibraryPage)).toBeVisible();
    await expect(page.getByTestId(testIds.productLibraryActivityFilterApplied)).toContainText('act-E2E-1001');

    await context.close();
  });

  test('点击「清除筛选」会清掉 URL 上的 activityId 并隐藏提示条', async ({ browser }) => {
    const context = await browser.newContext({ storageState: storageStates.bizLeader });
    const page = await context.newPage();

    await gotoApp(page, '/product/library?activityId=act-E2E-2002');
    const banner = page.getByTestId(testIds.productLibraryActivityFilterApplied);
    await expect(banner).toBeVisible();

    await page.getByTestId(testIds.productLibraryActivityFilterClear).click();
    // 清除后 URL 不再带 activityId
    await expect(page).not.toHaveURL(/activityId=/);
    // 提示条消失
    await expect(banner).toHaveCount(0);

    await context.close();
  });

  test('历史 /product/manage/:activityId 重定向到统一入口', async ({ browser }) => {
    const context = await browser.newContext({ storageState: storageStates.bizLeader });
    const page = await context.newPage();

    await gotoApp(page, '/product/manage/act-E2E-3003');
    await expect(page).toHaveURL(/\/product\/library\?activityId=act-E2E-3003/);
    await expect(page.getByTestId(testIds.productLibraryActivityFilterApplied)).toContainText('act-E2E-3003');

    await context.close();
  });

  test('历史 /product/activity/:activityId 也重定向到统一入口', async ({ browser }) => {
    const context = await browser.newContext({ storageState: storageStates.bizLeader });
    const page = await context.newPage();

    await gotoApp(page, '/product/activity/act-E2E-4004');
    await expect(page).toHaveURL(/\/product\/library\?activityId=act-E2E-4004/);
    await expect(page.getByTestId(testIds.productLibraryActivityFilterApplied)).toContainText('act-E2E-4004');

    await context.close();
  });
});

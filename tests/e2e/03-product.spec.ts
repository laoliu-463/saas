import { test, expect } from '@playwright/test';
import { storageStates } from './helpers/test-data';
import { capturePage } from './helpers/screenshot';
import { testIds } from './helpers/selectors';

test.use({ storageState: storageStates.channelLeader });

test('商品库可以加载真实商品并打开详情', async ({ page }, testInfo) => {
  await page.goto('/product');
  await expect(page.getByTestId('product-library-page')).toBeVisible();
  await expect(page.getByTestId(testIds.productCard).first()).toBeVisible();
  await capturePage(page, testInfo, '03-product-library');

  await page.getByTestId(testIds.productDetailButton).first().click();
  await expect(page.locator('body')).toContainText(/商品业务全貌|推广链接|基础信息/);
  await capturePage(page, testInfo, '03-product-detail');
});

test.describe('招商组长商品库', () => {
  test.use({ storageState: storageStates.bizLeader });

  test('招商组长可以查看共享商品库但不展示渠道转链动作', async ({ page }, testInfo) => {
    await page.goto('/product');
    await expect(page.getByTestId('product-library-page')).toBeVisible();
    await expect(page.getByTestId(testIds.productCard).first()).toBeVisible();
    await page.getByTestId(testIds.productCard).first().hover();
    await expect(page.getByTestId('product-copy-link')).toHaveCount(0);
    await capturePage(page, testInfo, '03-product-library-biz-leader-readonly');
  });
});

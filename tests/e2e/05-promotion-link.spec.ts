import { test, expect } from '@playwright/test';
import { storageStates } from './helpers/test-data';
import { capturePage } from './helpers/screenshot';
import { testIds } from './helpers/selectors';

test.use({ storageState: storageStates.channelLeader });

test('渠道组长可以对单个商品做最小转链验证', async ({ page }, testInfo) => {
  await page.goto('/product');
  const productCard = page.getByTestId(testIds.productCard).first();
  await expect(productCard).toBeVisible();
  await productCard.hover();
  await page.getByTestId(testIds.productCopyLink).first().click({ force: true });

  await expect(page.locator('body')).toContainText(/推广链接已复制|已复制|归因到当前渠道|推广链接生成失败/);
  await capturePage(page, testInfo, '05-promotion-link-success', { visual: false });
});

import { expect, test, type Page, type Route } from '@playwright/test';
import { readFixture } from './helpers/fixtures';
import { gotoApp } from './helpers/page-ready';
import { storageStates } from './helpers/test-data';
import { testIds } from './helpers/selectors';

test.use({ storageState: storageStates.channelLeader });

const productFixture = readFixture<{ records: any[]; total: number; page: number; size: number }>('product', 'single.json');

async function fulfillJson(route: Route, data: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(data)
  });
}

async function mockProductLibrary(page: Page) {
  await page.route('**/api/products**', async (route) => {
    await fulfillJson(route, {
      code: 200,
      data: productFixture
    });
  });
}

test('复制讲解会先转链并写入剪贴板', async ({ page }) => {
  await page.context().grantPermissions(['clipboard-read', 'clipboard-write']);
  await mockProductLibrary(page);
  await page.route('**/api/colonel/activities/activity-001/products/douyin-product-001/promotion-links', async (route) => {
    await fulfillJson(route, {
      code: 200,
      data: {
        shortLink: 'https://v.douyin.com/mock-success/',
        mappingId: 'mapping-001',
        pickSource: 'mock-pick-source',
        createdAt: '2026-05-21 14:00:00'
      }
    });
  });

  await gotoApp(page, '/product');
  await expect(page.getByTestId(testIds.productCard).first()).toBeVisible({ timeout: 30_000 });
  await page.getByTestId(testIds.productCard).first().hover();
  await page.getByTestId(testIds.productCopyLink).first().click();

  await expect(page.locator('body')).toContainText('已复制简介');
  const copied = await page.evaluate(() => navigator.clipboard.readText());
  expect(copied).toContain('【商品】夏季爆款水杯（清风小店）');
  expect(copied).toContain('【卖点】大容量、防漏');
  expect(copied).toContain('【链接】https://v.douyin.com/mock-success/');
});

test('转链失败时仍复制不含短链的讲解', async ({ page }) => {
  await page.context().grantPermissions(['clipboard-read', 'clipboard-write']);
  await mockProductLibrary(page);
  await page.route('**/api/colonel/activities/activity-001/products/douyin-product-001/promotion-links', async (route) => {
    await fulfillJson(route, { code: 'PROMOTION_FAILED', msg: 'mock promotion failed' }, 500);
  });

  await gotoApp(page, '/product');
  await expect(page.getByTestId(testIds.productCard).first()).toBeVisible({ timeout: 30_000 });
  await page.getByTestId(testIds.productCard).first().hover();
  await page.getByTestId(testIds.productCopyLink).first().click();

  await expect(page.locator('body')).toContainText('短链生成失败，已复制简介（不含短链）');
  const copied = await page.evaluate(() => navigator.clipboard.readText());
  expect(copied).toContain('【商品】夏季爆款水杯（清风小店）');
  expect(copied).not.toContain('【链接】');
});

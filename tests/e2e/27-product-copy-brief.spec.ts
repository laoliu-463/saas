import { expect, test, type Page, type Route } from '@playwright/test';
import { storageStates } from './helpers/test-data';

test.use({ storageState: storageStates.channelLeader });

const productRecord = {
  id: 'product-record-001',
  productId: 'douyin-product-001',
  activityId: 'activity-001',
  sourceActivityId: 'activity-001',
  title: '夏季爆款水杯',
  shopName: '清风小店',
  cover: '',
  priceText: '¥39.90',
  activityCosRatioText: '20%',
  sales30d: 1234,
  gmv30d: '49236.60',
  estimatedServiceFee: '984.73',
  bizStatus: 'APPROVED',
  selectedToLibrary: true,
  hasMaterial: true,
  hasSampleRule: true,
  assigneeName: '渠道负责人',
  auditSupplement: {
    sellingPoints: ['大容量', '防漏'],
    promotionScript: '主打夏季补水场景',
    sampleThresholdSales: 5000,
    sampleThresholdLevel: 3,
    exclusivePriceRemark: '达人专属券后价'
  },
  promotionMaterialPack: {
    outreachScript: '兜底话术'
  }
};

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
      data: {
        records: [productRecord],
        total: 1,
        page: 1,
        size: 12
      }
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

  await page.goto('/product');
  await expect(page.getByTestId('product-card').first()).toBeVisible({ timeout: 30_000 });
  await page.getByTestId('product-card').first().hover();
  await page.getByTestId('product-copy-link').click();

  await expect(page.locator('body')).toContainText('讲解 + 短链已复制');
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

  await page.goto('/product');
  await expect(page.getByTestId('product-card').first()).toBeVisible({ timeout: 30_000 });
  await page.getByTestId('product-card').first().hover();
  await page.getByTestId('product-copy-link').click();

  await expect(page.locator('body')).toContainText('短链生成失败，已复制讲解（不含短链）');
  const copied = await page.evaluate(() => navigator.clipboard.readText());
  expect(copied).toContain('【商品】夏季爆款水杯（清风小店）');
  expect(copied).not.toContain('【链接】');
});

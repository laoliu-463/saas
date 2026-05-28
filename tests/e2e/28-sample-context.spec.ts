import { expect, test, type Page, type Route } from '@playwright/test';
import { readFixture } from './helpers/fixtures';
import { storageStates } from './helpers/test-data';
import { testIds } from './helpers/selectors';

test.use({ storageState: storageStates.channelLeader });

const productFixture = readFixture<{ records: any[]; total: number; page: number; size: number }>('product', 'single.json');
const talentFixture = readFixture<{ records: any[]; total: number; page: number; size: number }>('talent', 'single.json');
const talentDetailFixture = readFixture('talent', 'detail.json');

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

async function mockSampleApplyLookups(page: Page) {
  await page.route('**/api/samples/product-candidates**', async (route) => {
    await fulfillJson(route, {
      code: 200,
      data: {
        records: [],
        total: 0
      }
    });
  });
}

async function mockTalentApis(page: Page) {
  await page.route('**/api/talents**', async (route) => {
    const url = new URL(route.request().url());
    if (url.pathname.endsWith('/api/talents/talent-record-001')) {
      await fulfillJson(route, {
        code: 200,
        data: talentDetailFixture
      });
      return;
    }

    if (url.pathname.endsWith('/api/talents')) {
      await fulfillJson(route, {
        code: 200,
        data: talentFixture
      });
      return;
    }

    await route.continue();
  });
}

test('从商品卡快速寄样会打开商品上下文弹窗', async ({ page }) => {
  await mockProductLibrary(page);
  await mockSampleApplyLookups(page);
  await mockTalentApis(page);

  await page.goto('/product');
  const card = page.getByTestId(testIds.productCard).first();
  await expect(card).toBeVisible({ timeout: 30_000 });
  await card.hover();
  await card.getByTestId('product-quick-sample').click();

  await expect(page.getByTestId('quick-sample-modal')).toBeVisible({ timeout: 10_000 });
  await expect(page.getByTestId('quick-sample-external-hint')).toBeVisible();
});

test('从达人详情快速寄样会带入达人和收货地址', async ({ page }) => {
  await mockSampleApplyLookups(page);
  await mockTalentApis(page);

  await page.goto('/talent?view=MY_TALENTS');
  await expect(page.getByTestId('talent-table')).toBeVisible({ timeout: 30_000 });
  await page.getByRole('button', { name: '查看详情' }).first().click();
  await expect(page.locator('body')).toContainText('达人详情');
  await page.getByRole('button', { name: '快速寄样' }).click();

  await expect(page).toHaveURL(/\/sample\/apply/);
  await expect(page).toHaveURL(/talentId=dy-uid-001/);
  await expect(page.locator('body')).toContainText('测品达人');
  await expect(page.locator('input[placeholder="请输入收货人"]')).toHaveValue('张三');
  await expect(page.locator('input[placeholder="请输入手机号"]')).toHaveValue('13800138000');
  await expect(page.locator('input[placeholder="请输入完整收货地址"]')).toHaveValue('上海市浦东新区测试路 1 号');
});

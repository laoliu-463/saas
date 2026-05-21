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
  assigneeName: '渠道负责人'
};

const talentRecord = {
  id: 'talent-record-001',
  nickname: '测品达人',
  douyinUid: 'dy-uid-001',
  douyinNo: 'dy-no-001',
  uid: 'dy-uid-001',
  fansCount: 52000,
  creditScore: 4.8,
  mainCategory: '家居',
  ipLocation: '上海',
  poolStatus: 'PRIVATE',
  ownerId: 'channel-leader-user',
  ownerName: '渠道组长',
  activeClaimCount: 1,
  sampleCount: 0,
  orderCount: 0,
  serviceFeeContribution: 0
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
        data: {
          talent: talentRecord,
          claim: {
            poolStatus: 'PRIVATE',
            ownerName: '渠道组长',
            activeClaimCount: 1,
            recipientName: '张三',
            recipientPhone: '13800138000',
            recipientAddress: '上海市浦东新区测试路 1 号'
          },
          samples: [],
          orders: []
        }
      });
      return;
    }

    if (url.pathname.endsWith('/api/talents')) {
      await fulfillJson(route, {
        code: 200,
        data: {
          records: [talentRecord],
          total: 1,
          page: 1,
          size: 20
        }
      });
      return;
    }

    await route.continue();
  });
}

test('从商品卡快速寄样会锁定商品上下文', async ({ page }) => {
  await mockProductLibrary(page);
  await mockSampleApplyLookups(page);
  await mockTalentApis(page);

  await page.goto('/product');
  await expect(page.getByTestId('product-card').first()).toBeVisible({ timeout: 30_000 });
  await page.getByTestId('product-card').first().hover();
  await page.getByTestId('product-quick-sample').click();

  await expect(page).toHaveURL(/\/sample\/apply/);
  await expect(page).toHaveURL(/productId=product-record-001/);
  await expect(page.locator('body')).toContainText('夏季爆款水杯');
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

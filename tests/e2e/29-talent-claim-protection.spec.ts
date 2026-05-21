import { expect, test, type Page, type Route } from '@playwright/test';
import { storageStates } from './helpers/test-data';

test.use({ storageState: storageStates.channelLeader });

const protectedTalent = {
  id: 'talent-record-claim',
  nickname: '保护期达人',
  douyinUid: 'dy-protected-001',
  douyinNo: 'dy-no-protected',
  uid: 'dy-protected-001',
  fansCount: 88000,
  likesCount: 120000,
  worksCount: 36,
  mainCategory: '家居',
  ipLocation: '上海',
  poolStatus: 'PUBLIC',
  ownerName: '',
  activeClaimCount: 0,
  sampleCount: 0,
  orderCount: 0,
  serviceFeeContribution: 0,
  naturalOrderTalent: false
};

async function fulfillJson(route: Route, data: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(data)
  });
}

async function mockTalentProtectionApis(page: Page) {
  await page.route('**/api/talents**', async (route) => {
    const url = new URL(route.request().url());
    const method = route.request().method();

    if (method === 'POST' && url.pathname.endsWith('/api/talents/talent-record-claim/claims')) {
      await fulfillJson(route, {
        code: 409,
        msg: '该达人在保护期内'
      }, 409);
      return;
    }

    if (method === 'GET' && url.pathname.endsWith('/api/talents')) {
      await fulfillJson(route, {
        code: 200,
        data: {
          records: [protectedTalent],
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

test('认领保护期达人时提示保护期冲突', async ({ page }) => {
  await mockTalentProtectionApis(page);

  await page.goto('/talent?view=TEAM_PUBLIC');
  await expect(page.getByTestId('talent-table')).toBeVisible({ timeout: 30_000 });
  await expect(page.locator('body')).toContainText('保护期达人');

  await page.getByRole('button', { name: '认领' }).first().click();
  await page.getByRole('button', { name: '确认认领' }).click();

  await expect(page.locator('body')).toContainText('该达人在保护期内');
});

import { expect, test, type Page, type Route } from '@playwright/test';
import { readFixture } from './helpers/fixtures';
import { storageStates } from './helpers/test-data';
import { gotoApp } from './helpers/page-ready';

test.use({ storageState: storageStates.channelLeader });

const protectedTalentFixture = readFixture<{ records: any[]; total: number; page: number; size: number }>('talent', 'protected.json');
const claimConflict = readFixture<{ status: number; body: unknown }>('talent', 'claim_conflict.json');

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
      await fulfillJson(route, claimConflict.body, claimConflict.status);
      return;
    }

    if (method === 'GET' && url.pathname.endsWith('/api/talents')) {
      await fulfillJson(route, {
        code: 200,
        data: protectedTalentFixture
      });
      return;
    }

    await route.continue();
  });
}

test('认领保护期达人时提示保护期冲突', async ({ page }) => {
  await mockTalentProtectionApis(page);

  await gotoApp(page, '/talent?view=TEAM_PUBLIC');
  await expect(page.getByTestId('talent-table')).toBeVisible({ timeout: 30_000 });
  await expect(page.locator('body')).toContainText('保护期达人');

  await page.getByRole('button', { name: '认领' }).first().click();
  await page.getByRole('button', { name: '确认认领' }).click();

  await expect(page.locator('body')).toContainText('该达人在保护期内');
});

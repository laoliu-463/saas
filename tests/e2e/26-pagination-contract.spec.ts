import { expect, test, type Page } from '@playwright/test';
import { gotoApp } from './helpers/page-ready';
import { testIds } from './helpers/selectors';
import { storageStates } from './helpers/test-data';

test.use({ storageState: storageStates.admin });
test.setTimeout(90_000);

async function assertDefaultPaginationRequest(
  page: Page,
  routePath: string,
  apiGlob: string,
  tableTestId: string,
  recordsKey = 'records'
) {
  let requestUrl = '';
  await page.route(apiGlob, async (route) => {
    requestUrl = route.request().url();
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        msg: 'success',
        data: {
          [recordsKey]: [],
          total: 0,
          page: 1,
          size: 20
        }
      })
    });
  });

  await gotoApp(page, routePath);
  await expect(page.getByTestId(tableTestId)).toBeVisible({ timeout: 15_000 });

  expect(requestUrl).toBeTruthy();
  const url = new URL(requestUrl);
  expect(url.searchParams.get('page')).toBe('1');
  expect(url.searchParams.get('size')).toBe('20');
}

test.describe('front-end pagination contract', () => {
  test('data order list requests the default 20 rows', async ({ page }) => {
    await assertDefaultPaginationRequest(
      page,
      '/data/orders',
      '**/api/data/orders**',
      testIds.dataOrdersTable
    );
  });

  test('sample desk requests the default 20 rows', async ({ page }) => {
    await assertDefaultPaginationRequest(
      page,
      '/sample',
      '**/api/samples**',
      testIds.sampleTable
    );
  });

  test('talent desk requests the default 20 rows', async ({ page }) => {
    await assertDefaultPaginationRequest(
      page,
      '/talent',
      '**/api/talents**',
      testIds.talentTable
    );
  });

  test('shipping desk requests the default 20 rows', async ({ page }) => {
    await assertDefaultPaginationRequest(
      page,
      '/ops/shipping',
      '**/api/samples**',
      testIds.opsShippingTable
    );
  });
});

import { type Page } from '@playwright/test';

export async function waitForAppReady(page: Page) {
  await page.waitForLoadState('domcontentloaded');
  const bootLoading = page.locator('#boot-loading');
  await bootLoading.waitFor({ state: 'detached', timeout: 15_000 }).catch(async () => {
    await bootLoading.waitFor({ state: 'hidden', timeout: 5_000 }).catch(() => {});
  });
}

export async function gotoApp(page: Page, path: string) {
  await page.goto(path);
  await waitForAppReady(page);
}

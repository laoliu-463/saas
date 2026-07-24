import { expect, type Page } from '@playwright/test';

type AppWaitUntil = 'commit' | 'domcontentloaded' | 'load' | 'networkidle';

export interface GotoAppOptions {
  timeout?: number;
  waitUntil?: AppWaitUntil;
}

export async function waitForAppReady(page: Page, timeout = 20_000): Promise<void> {
  await page.waitForLoadState('domcontentloaded');
  const bootLoading = page.locator('#boot-loading');
  await bootLoading.waitFor({ state: 'detached', timeout }).catch(async () => {
    await bootLoading.waitFor({ state: 'hidden', timeout: Math.min(timeout, 5_000) }).catch(() => undefined);
  });
}

export async function gotoApp(page: Page, path: string, options: GotoAppOptions = {}): Promise<void> {
  await page.goto(path, {
    timeout: options.timeout,
    waitUntil: options.waitUntil || 'domcontentloaded'
  });
  await waitForAppReady(page, options.timeout ?? 20_000);
}

export async function waitForRoute(page: Page, url: string | RegExp, timeout = 20_000): Promise<void> {
  await expect(page).toHaveURL(url, { timeout });
  await waitForAppReady(page, timeout);
}

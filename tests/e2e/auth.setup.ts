import fs from 'node:fs';
import path from 'node:path';
import { test as setup, expect, type Browser } from '@playwright/test';
import { accounts, storageStates } from './helpers/test-data';
import { testIds } from './helpers/selectors';

const authDir = path.join(process.cwd(), 'tests', 'e2e', '.auth');

setup.setTimeout(180_000);

setup.beforeAll(() => {
  fs.mkdirSync(authDir, { recursive: true });
});

async function loginAndSave(browser: Browser, username: string, password: string, statePath: string) {
  const context = await browser.newContext();
  const page = await context.newPage();
  try {
    await page.goto('/login');
    await page.getByTestId(testIds.loginUsername).locator('input').fill(username);
    await page.getByTestId(testIds.loginPassword).locator('input').fill(password);
    await page.getByTestId(testIds.loginSubmit).click();
    await expect(page).not.toHaveURL(/\/login$/, { timeout: 30_000 });
    await context.storageState({ path: statePath });
  } finally {
    await context.close();
  }
}

setup('prepare role storage states', async ({ browser }) => {
  await loginAndSave(browser, accounts.admin.username, accounts.admin.password, storageStates.admin);
  await loginAndSave(browser, accounts.bizLeader.username, accounts.bizLeader.password, storageStates.bizLeader);
  await loginAndSave(browser, accounts.channelLeader.username, accounts.channelLeader.password, storageStates.channelLeader);
  await loginAndSave(browser, accounts.channelStaff.username, accounts.channelStaff.password, storageStates.channelStaff);
  await loginAndSave(browser, accounts.ops.username, accounts.ops.password, storageStates.ops);
});

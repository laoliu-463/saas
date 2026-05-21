import { test, expect } from '@playwright/test';
import { accounts } from './helpers/test-data';
import { capturePage } from './helpers/screenshot';
import { gotoApp } from './helpers/page-ready';
import { testIds } from './helpers/selectors';

test('管理员可以通过浏览器登录系统', async ({ page }, testInfo) => {
  await gotoApp(page, '/login');
  await page.getByTestId(testIds.loginUsername).locator('input').fill(accounts.admin.username);
  await page.getByTestId(testIds.loginPassword).locator('input').fill(accounts.admin.password);
  await page.getByTestId(testIds.loginSubmit).click();

  await expect(page).toHaveURL(/\/(dashboard|data|system\/users|orders)/, { timeout: 20_000 });
  await expect(page.locator('body')).toContainText(/数据|商品|订单|寄样/);
  await capturePage(page, testInfo, '01-login-success');
});

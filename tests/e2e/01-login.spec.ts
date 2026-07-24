import { test, expect } from './fixtures';
import { capturePage } from './helpers/screenshot';
import { accounts } from './helpers/test-data';

test('管理员可以通过浏览器登录系统', async ({ page, loginPage }, testInfo) => {
  await loginPage.open();
  await loginPage.login(accounts.admin);

  await expect(page).toHaveURL(/\/(dashboard|data|system\/users|orders)/, { timeout: 20_000 });
  await expect(page.locator('body')).toContainText(/数据|商品|订单|寄样/);
  await capturePage(page, testInfo, '01-login-success');
});

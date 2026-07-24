import { test, expect } from './fixtures';
import { capturePage } from './helpers/screenshot';

test('登录页可访问并渲染核心品牌文案', async ({ page, loginPage }, testInfo) => {
  await loginPage.open();
  await loginPage.expectLoaded();
  await expect(page.getByText('抖音团长 SaaS')).toBeVisible();
  await expect(page.getByText('欢迎回来')).toBeVisible();
  await capturePage(page, testInfo, '00-health-login-page', { visual: false });
});

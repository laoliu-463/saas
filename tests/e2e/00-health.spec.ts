import { test, expect } from '@playwright/test';
import { capturePage } from './helpers/screenshot';
import { gotoApp } from './helpers/page-ready';

test('登录页可访问并渲染核心品牌文案', async ({ page }, testInfo) => {
  await gotoApp(page, '/login');
  await expect(page.getByText('抖音团长 SaaS')).toBeVisible();
  await expect(page.getByText('欢迎回来')).toBeVisible();
  await capturePage(page, testInfo, '00-health-login-page', { visual: false });
});

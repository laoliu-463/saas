import { test as base, expect } from '@playwright/test';
import { AppShellPage } from './pages/app-shell.page';
import { LoginPage } from './pages/login.page';

type E2EFixtures = {
  loginPage: LoginPage;
  appShell: AppShellPage;
};

/** 所有浏览器用例共享的页面对象入口；业务 spec 不再重复组装登录和壳层选择器。 */
export const test = base.extend<E2EFixtures>({
  loginPage: async ({ page }, use) => {
    await use(new LoginPage(page));
  },
  appShell: async ({ page }, use) => {
    await use(new AppShellPage(page));
  }
});

export { expect };

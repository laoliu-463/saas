import { expect, type Locator, type Page } from '@playwright/test';
import { gotoApp, waitForRoute } from '../helpers/page-ready';
import { testIds } from '../helpers/selectors';

export class AppShellPage {
  readonly sidebar: Locator;
  readonly dashboardNav: Locator;
  readonly productNav: Locator;
  readonly systemNav: Locator;
  readonly talentNav: Locator;

  constructor(private readonly page: Page) {
    this.sidebar = page.getByTestId(testIds.sidebarMenu);
    this.dashboardNav = page.getByTestId(testIds.navDashboard);
    this.productNav = page.getByTestId(testIds.navProduct);
    this.systemNav = page.getByTestId(testIds.navSystem);
    this.talentNav = page.getByTestId(testIds.navTalent);
  }

  async goto(path: string, timeout?: number): Promise<void> {
    await gotoApp(this.page, path, { timeout });
  }

  async clickNavigation(item: Locator, url: string | RegExp): Promise<void> {
    await item.click();
    await waitForRoute(this.page, url);
  }

  async expectShellVisible(): Promise<void> {
    await expect(this.sidebar).toBeVisible();
  }

  async expectNoRuntimeError(): Promise<void> {
    await expect(this.page.locator('body')).not.toContainText(
      /Unexpected Application Error|Application Error|Bad Gateway|Internal Server Error/i
    );
  }
}

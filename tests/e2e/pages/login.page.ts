import { expect, type Locator, type Page } from '@playwright/test';
import { accounts, type AccountCredential, type AccountKey } from '../helpers/test-data';
import { gotoApp, waitForAppReady } from '../helpers/page-ready';
import { testIds } from '../helpers/selectors';

export class LoginPage {
  readonly usernameInput: Locator;
  readonly passwordInput: Locator;
  readonly submitButton: Locator;

  constructor(private readonly page: Page) {
    this.usernameInput = page.getByTestId(testIds.loginUsername).locator('input');
    this.passwordInput = page.getByTestId(testIds.loginPassword).locator('input');
    this.submitButton = page.getByTestId(testIds.loginSubmit);
  }

  async open(): Promise<void> {
    await gotoApp(this.page, '/login');
    await expect(this.usernameInput).toBeVisible();
  }

  async expectLoaded(): Promise<void> {
    await expect(this.usernameInput).toBeVisible();
    await expect(this.passwordInput).toBeVisible();
    await expect(this.submitButton).toBeVisible();
  }

  async login(credentials: AccountCredential): Promise<void> {
    await this.usernameInput.fill(credentials.username);
    await this.passwordInput.fill(credentials.password);
    await this.submitButton.click();
    await expect(this.page).not.toHaveURL(/\/login(?:$|\?)/, { timeout: 20_000 });
    await waitForAppReady(this.page);
  }

  async loginAs(role: AccountKey): Promise<void> {
    await this.login(accounts[role]);
  }
}

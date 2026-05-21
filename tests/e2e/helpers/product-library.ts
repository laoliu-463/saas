import { expect, type Locator, type Page } from '@playwright/test';
import { testIds } from './selectors';

export async function waitForProductCard(page: Page): Promise<Locator> {
  const card = page.getByTestId(testIds.productCard).first();
  const refresh = page.getByTestId(testIds.productLibraryRefresh);

  for (const timeout of [20_000, 10_000, 10_000]) {
    if (await card.isVisible({ timeout }).catch(() => false)) {
      return card;
    }
    if (await refresh.isVisible({ timeout: 1_000 }).catch(() => false)) {
      await refresh.click();
    }
  }

  await expect(card).toBeVisible({ timeout: 10_000 });
  return card;
}

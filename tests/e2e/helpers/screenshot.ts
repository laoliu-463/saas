import { expect, type Page, type TestInfo } from '@playwright/test';

function shouldAssertVisuals() {
  return process.env.E2E_VISUAL_ASSERT === 'true' || process.argv.includes('--update-snapshots');
}

export async function capturePage(
  page: Page,
  testInfo: TestInfo,
  name: string,
  options: { fullPage?: boolean; visual?: boolean } = {}
) {
  const fullPage = options.fullPage ?? true;
  const outputPath = testInfo.outputPath(`${name}.png`);
  await page.screenshot({ path: outputPath, fullPage });

  if (options.visual !== false && shouldAssertVisuals()) {
    await expect(page).toHaveScreenshot(`${name}.png`, { fullPage });
  }

  await testInfo.attach(name, {
    path: outputPath,
    contentType: 'image/png'
  });
}

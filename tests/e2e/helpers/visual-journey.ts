import type { Locator, Page } from '@playwright/test';

function envNumber(name: string, fallback: number): number {
  const value = Number(process.env[name] || fallback);
  return Number.isFinite(value) ? value : fallback;
}

export function stepPauseMs(): number {
  return envNumber('PW_STEP_PAUSE_MS', 800);
}

export function afterActionPauseMs(): number {
  return envNumber('PW_AFTER_ACTION_PAUSE_MS', 600);
}

export async function waitForVisualIdle(page: Page, timeout = envNumber('E2E_REAL_PRE_NETWORK_IDLE_TIMEOUT_MS', 30_000)): Promise<void> {
  await page.waitForLoadState('networkidle', { timeout }).catch(() => undefined);
  await page.locator('.n-spin-body').waitFor({ state: 'hidden', timeout: 5_000 }).catch(() => undefined);
  await waitForVisualSettle(page, 500);
}

export async function waitForVisualSettle(page: Page, pauseMs = afterActionPauseMs()): Promise<void> {
  if (pauseMs > 0) {
    await page.waitForTimeout(pauseMs);
  }
}

export async function showStepBanner(page: Page, text: string, pauseMs = stepPauseMs()): Promise<void> {
  await page.evaluate((message) => {
    const old = document.getElementById('qa-step-banner');
    if (old) old.remove();

    const el = document.createElement('div');
    el.id = 'qa-step-banner';
    el.innerText = message;
    el.style.position = 'fixed';
    el.style.top = '12px';
    el.style.left = '12px';
    el.style.zIndex = '999999';
    el.style.background = 'rgba(0, 0, 0, 0.82)';
    el.style.color = '#fff';
    el.style.padding = '10px 14px';
    el.style.borderRadius = '8px';
    el.style.fontSize = '16px';
    el.style.fontWeight = '600';
    el.style.lineHeight = '1.45';
    el.style.maxWidth = '760px';
    el.style.whiteSpace = 'pre-line';
    el.style.boxShadow = '0 4px 16px rgba(0,0,0,.25)';
    document.body.appendChild(el);
  }, text);

  if (pauseMs > 0) {
    await page.waitForTimeout(pauseMs);
  }
}

export async function highlight(locator: Locator): Promise<void> {
  await locator.evaluate((node) => {
    const el = node as HTMLElement;
    const oldOutline = el.style.outline;
    const oldBoxShadow = el.style.boxShadow;
    const oldTransition = el.style.transition;

    el.style.transition = 'outline .12s ease, box-shadow .12s ease';
    el.style.outline = '4px solid #ff4d4f';
    el.style.boxShadow = '0 0 0 6px rgba(255,77,79,.25)';

    window.setTimeout(() => {
      el.style.outline = oldOutline;
      el.style.boxShadow = oldBoxShadow;
      el.style.transition = oldTransition;
    }, 1200);
  }).catch(() => undefined);
}

export async function visibleClick(
  page: Page,
  locator: Locator,
  stepName: string,
  options?: Parameters<Locator['click']>[0]
): Promise<void> {
  await showStepBanner(page, stepName);
  await locator.scrollIntoViewIfNeeded().catch(() => undefined);
  await highlight(locator);
  await page.waitForTimeout(500);
  await locator.click(options);
  await page.waitForTimeout(afterActionPauseMs());
}

export async function visibleFill(
  page: Page,
  locator: Locator,
  value: string,
  stepName: string,
  options?: {
    fillOptions?: Parameters<Locator['fill']>[1];
    displayValue?: string;
  }
): Promise<void> {
  await showStepBanner(page, `${stepName}\n填写：${options?.displayValue ?? value}`);
  await locator.scrollIntoViewIfNeeded().catch(() => undefined);
  await highlight(locator);
  await locator.fill(value, options?.fillOptions);
  await page.waitForTimeout(afterActionPauseMs());
}

export async function visiblePause(page: Page, text: string, pauseMs = stepPauseMs()): Promise<void> {
  await showStepBanner(page, text, pauseMs);
}

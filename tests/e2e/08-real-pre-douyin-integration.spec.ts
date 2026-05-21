import { test, expect } from '@playwright/test';
import { accounts } from './helpers/test-data';
import {
  apiLogin,
  getBackendApiBase,
  skuProbeAccept,
  verifyBackendApis
} from './helpers/real-pre-api';

const UI_CHECKS = [
  'Token 正常',
  '授权主体正常',
  '活动商品已刷新',
  '商品 SKU 已验证',
  '订单同步成功',
  'Dashboard 已读取真实订单',
] as const;
const REAL_PRE_UI_TIMEOUT_MS = Number(process.env.E2E_REAL_PRE_UI_TIMEOUT_MS || 120_000);

test('管理员在前端完成一键刷新联调状态（UI + 后端 API 双验证）', async ({ page, context }, testInfo) => {
  test.skip(!process.env.E2E_REAL_PRE, '设置 E2E_REAL_PRE=true 后运行（npm run e2e:real-pre）');
  test.setTimeout(15 * 60_000);

  const apiBase = getBackendApiBase();
  const auth = await apiLogin(apiBase, accounts.admin.username, accounts.admin.password);
  const apiResults = await verifyBackendApis(String(auth.token ?? ''));

  await testInfo.attach('backend-api-base', { body: apiBase, contentType: 'text/plain' });
  await testInfo.attach('backend-probes.json', {
    body: JSON.stringify(apiResults, null, 2),
    contentType: 'application/json'
  });

  if (!apiResults.token?.ok) {
    throw new Error(apiResults.token?.reason || 'REAL_PRE_TOKEN_PRECONDITION_BLOCKED: token cache is unavailable');
  }
  if (!apiResults.institution?.ok) {
    throw new Error(apiResults.institution?.reason || 'REAL_PRE_INSTITUTION_PRECONDITION_BLOCKED: institution probe failed');
  }

  await context.addInitScript((payload: Record<string, unknown>) => {
    localStorage.setItem('token', String(payload.token ?? ''));
    localStorage.setItem('refreshToken', String(payload.refreshToken ?? ''));
    localStorage.setItem('refreshExpiresIn', String(payload.refreshExpiresIn ?? ''));
    localStorage.setItem(
      'accessTokenExpiresIn',
      String(payload.accessTokenExpiresIn ?? payload.expiresIn ?? '')
    );
    localStorage.setItem('userInfo', JSON.stringify(payload));
  }, auth);

  await page.goto('/system/douyin', { waitUntil: 'domcontentloaded', timeout: REAL_PRE_UI_TIMEOUT_MS });
  await expect(page.getByRole('button', { name: '一键刷新联调状态' })).toBeVisible({ timeout: REAL_PRE_UI_TIMEOUT_MS });

  const pageErrors: string[] = [];
  page.on('pageerror', (err) => pageErrors.push(err.message));

  await page.getByRole('button', { name: '一键刷新联调状态' }).click();

  for (const text of UI_CHECKS) {
    await expect(page.getByText(text, { exact: true }).first()).toBeVisible({ timeout: REAL_PRE_UI_TIMEOUT_MS });
  }

  const fatalPageError = pageErrors.some((e) =>
    /Unexpected Application Error|Application Error|500|Bad Gateway/i.test(e)
  );
  expect(fatalPageError, `页面运行时错误: ${pageErrors.join('; ')}`).toBe(false);

  if (apiResults.token && !apiResults.token.error) {
    expect(apiResults.token.ok, 'GET /douyin/tokens').toBeTruthy();
  }
  if (apiResults.institution && !apiResults.institution.error) {
    expect(apiResults.institution.ok, 'GET /douyin/institution-info').toBeTruthy();
  }

  expect(skuProbeAccept(apiResults.skuProbe), 'POST /douyin/promotion-link-probes/raw（精选联盟 SKU 探针）').toBe(
    true
  );
});

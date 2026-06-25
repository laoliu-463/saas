/**
 * real-pre P0 / 34 / 业绩看板
 *
 * 校验 Dashboard、业绩相关接口字段和公式。
 * 当前真实订单为空 -> PENDING_NO_PERFORMANCE_SAMPLE。
 * 公式校验仅在样本可读时执行；允许小数误差 0.01。
 */
import { test, expect, request as playwrightRequest, type APIRequestContext, type Page } from '@playwright/test';
import { accounts } from './helpers/test-data';
import { apiLogin } from './helpers/real-pre-api';
import {
  createRealPreP0Step,
  ensureRealPreP0Env,
  markFail,
  markPending,
  persistStepSummary,
  setDetail,
  shouldRunRealPreP0,
  safeUnwrap
} from './helpers/real-pre-p0-step';

type JsonMap = Record<string, unknown>;
const FATAL_TEXT = /Unexpected Application Error|Application Error|Bad Gateway|Internal Server Error/i;
const FORMULA_EPSILON = 0.01;

test('real-pre P0 / 34 / 业绩看板', async ({ page }, testInfo) => {
  test.skip(!shouldRunRealPreP0('34-real-pre-performance-dashboard'), 'Run via npm run e2e:real-pre:p0 or set E2E_REAL_PRE_P0=true');
  test.setTimeout(10 * 60_000);
  ensureRealPreP0Env();

  const ctx = createRealPreP0Step('34-real-pre-performance-dashboard', 'real-pre-p0/34/performance-dashboard');
  const backend = (process.env.E2E_BACKEND_URL || 'http://localhost:8081').replace(/\/$/, '');
  const api = await playwrightRequest.newContext({ baseURL: backend, ignoreHTTPSErrors: true });

  try {
    const admin = await apiLogin(`${backend}/api`, accounts.admin.username, accounts.admin.password);
    const token = String(admin.token || '');

    // dashboard metrics
    const metrics = await rawApi(api, 'GET', '/api/dashboard/metrics', token);
    if (!metrics.ok || (metrics.body as JsonMap | undefined)?.code === 500) {
      markFail(ctx, `/api/dashboard/metrics 调用失败 HTTP ${metrics.status}`);
    }
    const metricsData = safeUnwrap<JsonMap>(metrics.body) || {};
    setDetail(ctx, 'dashboardMetrics', summarizeMetrics(metricsData));

    // dashboard summary (兼容旧版口径)
    const summary = await rawApi(api, 'GET', '/api/dashboard/summary', token);
    const summaryData = safeUnwrap<JsonMap>(summary.body) || {};
    setDetail(ctx, 'dashboardSummary', summarizeMetrics(summaryData));

    // performance summary（可选）
    const performance = await rawApi(api, 'GET', '/api/performance/summary', token);
    const performanceData = safeUnwrap<JsonMap>(performance.body) || {};
    setDetail(ctx, 'performanceSummary', performance.ok ? summarizeMetrics(performanceData) : { skipped: true, status: performance.status });

    // 公式校验：同时兼容 /performance/summary 的 estimate/effective 双轨，
    // 以及 /dashboard/metrics 的 estimate/settle 嵌套轨道。
    const formulaChecks = checkFormulas([metricsData, summaryData, performanceData]);
    setDetail(ctx, 'formulaChecks', formulaChecks);
    if (formulaChecks.violations.length) {
      markFail(ctx, `业绩公式不一致：${formulaChecks.violations.join(' | ')}`);
    }

    if (!formulaChecks.hasSample && ctx.summary.conclusion === 'PASS') {
      markPending(ctx, 'PENDING_NO_PERFORMANCE_SAMPLE: 当前 real-pre 无可读业绩样本，公式校验跳过');
    }

    // 页面 smoke
    await installAuth(page, admin);
    const pageChecks = [] as Array<{ route: string; runtimeError: boolean; finalPath: string }>;
    for (const route of ['/dashboard', '/data', '/orders']) {
      const pageCheck = await openAndAssertNoFatal(page, route);
      pageChecks.push(pageCheck);
      if (pageCheck.runtimeError) markFail(ctx, `${route} 出现运行时错误`);
    }
    setDetail(ctx, 'pageChecks', pageChecks);
  } catch (error) {
    markFail(ctx, error instanceof Error ? error.message : String(error));
  } finally {
    persistStepSummary(ctx);
    await api.dispose();
    await testInfo.attach('step-summary.json', {
      body: JSON.stringify(ctx.summary, null, 2),
      contentType: 'application/json'
    });
  }

  expect(
    ctx.summary.conclusion === 'FAIL' ? ctx.summary.failures.join('; ') : 'OK',
    `real-pre 34 业绩看板 conclusion=${ctx.summary.conclusion}`
  ).toBe('OK');
});

function summarizeMetrics(data: Record<string, unknown>): Record<string, unknown> {
  const keys = [
    'totalOrders', 'todayOrderCount', 'orderCount',
    'totalAmount', 'todayGmv', 'orderAmount',
    'serviceFeeIncome', 'estimateServiceFee', 'effectiveServiceFee',
    'techServiceFee', 'estimateTechServiceFee', 'effectiveTechServiceFee',
    'serviceFeeProfit',
    'recruiterCommission', 'channelCommission',
    'attributedOrderCount', 'unattributedOrderCount'
  ];
  const result: Record<string, unknown> = {};
  for (const key of keys) {
    if (data && Object.prototype.hasOwnProperty.call(data, key)) {
      result[key] = (data as Record<string, unknown>)[key];
    }
  }
  return result;
}

function checkFormulas(samples: Array<Record<string, unknown>>): { hasSample: boolean; violations: string[]; checked: Array<Record<string, unknown>> } {
  const violations: string[] = [];
  const checked: Array<Record<string, unknown>> = [];
  for (const candidate of collectFormulaCandidates(samples)) {
    const sample = candidate.sample;
    const income = toNumber(sample.serviceFeeIncome ?? sample.effectiveServiceFee);
    const tech = toNumber(sample.techServiceFee ?? sample.effectiveTechServiceFee);
    const expense = toNumber(sample.serviceFeeExpense);
    const profit = toNumber(sample.serviceFeeProfit);
    if (income === null || profit === null) continue;
    const mode = resolveFormulaMode(candidate.name, sample);
    const computed = mode === 'estimate'
      ? income - (expense ?? 0) - (tech ?? 0)
      : income - (expense ?? 0);
    const passed = Math.abs(computed - profit) <= FORMULA_EPSILON;
    checked.push({
      source: candidate.name,
      formula: mode === 'estimate'
        ? 'serviceFeeProfit = serviceFeeIncome - serviceFeeExpense - techServiceFee'
        : 'serviceFeeProfit = serviceFeeIncome - serviceFeeExpense',
      mode,
      income,
      tech,
      expense,
      profit,
      computed,
      passed
    });
    if (!passed) {
      violations.push(`${candidate.name} serviceFeeProfit=${profit} 与 computed=${computed} 不一致`);
    }
  }
  return { hasSample: checked.length > 0, violations, checked };
}

function collectFormulaCandidates(samples: Array<Record<string, unknown>>): Array<{ name: string; sample: Record<string, unknown> }> {
  const candidates: Array<{ name: string; sample: Record<string, unknown> }> = [];
  for (const sample of samples) {
    collectFormulaCandidate(candidates, 'root', sample);
  }
  return candidates;
}

function collectFormulaCandidate(
  candidates: Array<{ name: string; sample: Record<string, unknown> }>,
  name: string,
  sample: unknown
): void {
  if (!sample || typeof sample !== 'object' || Array.isArray(sample)) return;
  const record = sample as Record<string, unknown>;
  if (record.serviceFeeIncome !== undefined && record.serviceFeeProfit !== undefined) {
    candidates.push({ name, sample: record });
  }
  for (const key of ['estimate', 'effective', 'settle']) {
    if (record[key] && typeof record[key] === 'object' && !Array.isArray(record[key])) {
      collectFormulaCandidate(candidates, key, record[key]);
    }
  }
}

function resolveFormulaMode(name: string, sample: Record<string, unknown>): 'estimate' | 'effective' {
  const marker = String(sample.amountTrack ?? sample.track ?? name).toLowerCase();
  return marker.includes('estimate') || marker.includes('pay') ? 'estimate' : 'effective';
}

function toNumber(value: unknown): number | null {
  if (value === null || value === undefined || value === '') return null;
  const num = Number(value);
  return Number.isFinite(num) ? num : null;
}

async function installAuth(page: Page, auth: Record<string, unknown>): Promise<void> {
  await page.addInitScript((payload: Record<string, unknown>) => {
    localStorage.setItem('token', String(payload.token ?? ''));
    if (payload.refreshToken) localStorage.setItem('refreshToken', String(payload.refreshToken));
    if (payload.refreshExpiresIn) localStorage.setItem('refreshExpiresIn', String(payload.refreshExpiresIn));
    if (payload.accessTokenExpiresIn || payload.expiresIn) {
      localStorage.setItem('accessTokenExpiresIn', String(payload.accessTokenExpiresIn ?? payload.expiresIn ?? ''));
    }
    localStorage.setItem('userInfo', JSON.stringify(payload));
  }, auth);
}

async function openAndAssertNoFatal(page: Page, route: string): Promise<{ route: string; runtimeError: boolean; finalPath: string }> {
  await page.goto(route, { waitUntil: 'domcontentloaded', timeout: 120_000 });
  await page.waitForLoadState('networkidle', { timeout: 30_000 }).catch(() => undefined);
  const bodyText = await page.locator('body').innerText({ timeout: 10_000 }).catch(() => '');
  return {
    route,
    runtimeError: FATAL_TEXT.test(bodyText),
    finalPath: new URL(page.url()).pathname
  };
}

async function rawApi(
  api: APIRequestContext,
  method: 'GET' | 'POST' | 'PUT',
  path: string,
  token: string
): Promise<{ status: number; ok: boolean; body: unknown }> {
  const headers = token ? { Authorization: `Bearer ${token}` } : undefined;
  const response = method === 'GET'
    ? await api.get(path, { headers })
    : method === 'POST'
      ? await api.post(path, { headers })
      : await api.put(path, { headers });
  const body = await response.json().catch(async () => ({ rawText: await response.text().catch(() => '') }));
  return { status: response.status(), ok: response.ok(), body };
}

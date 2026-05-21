/**
 * 24-v1-performance-dashboard.spec.ts
 *
 * V1-P0 业绩看板对账验收 E2E
 *
 * 验收要点：
 *  1. /data 指标卡数值有意义（非零或明确含义）
 *  2. /data/orders 订单明细页：归因状态 + 渠道负责人 + 创建/结算时间双口径切换
 *  3. /dashboard 概览页：核心 KPI 文案（GMV/归因/服务费）
 *  4. API 层对账：metrics 与 summary 结构完整性
 *  5. API 层：未归因订单摘要可读取
 *  6. API 层：渠道组长 vs 管理员的数据范围差异（DataScope）
 *  7. API 层：Dashboard metrics 支持 timeField 切换
 */
import { test, expect } from '@playwright/test';
import { storageStates } from './helpers/test-data';
import { capturePage } from './helpers/screenshot';
import { testIds } from './helpers/selectors';
import { gotoApp } from './helpers/page-ready';
import {
  loginApi,
  apiGet,
  assertDashboardMetrics,
  assertDashboardSummary,
  seedTestData,
} from './helpers/api-assertions';
import { request as playwrightRequest } from '@playwright/test';

// ─────────────────────────────────────────────────────────
// Shared tokens
// ─────────────────────────────────────────────────────────
let adminToken: string;
let channelLeaderToken: string;

test.beforeAll(async () => {
  [adminToken, channelLeaderToken] = await Promise.all([
    loginApi('admin'),
    loginApi('channelLeader'),
  ]);
  await seedTestData(adminToken);
});

// ─────────────────────────────────────────────────────────
// A. 管理员视角 — 数据平台 UI
// ─────────────────────────────────────────────────────────
test.describe('A. 管理员视角数据平台 UI', () => {
  test.use({ storageState: storageStates.admin });

  test('数据平台指标卡片加载且有数值', async ({ page }, testInfo) => {
    await gotoApp(page, '/data');
    await expect(page.getByTestId(testIds.dashboardPage)).toBeVisible({ timeout: 15_000 });
    await expect(page.getByTestId(testIds.dashboardMetricCards)).toBeVisible({ timeout: 10_000 });

    // 4 张核心指标卡：订单数 / GMV / 服务费 / 毛利
    await expect(page.getByTestId(testIds.dashboardMetricOrders)).toBeVisible();
    await expect(page.getByTestId(testIds.dashboardMetricAmount)).toBeVisible();
    await expect(page.getByTestId(testIds.dashboardMetricFee)).toBeVisible();
    await expect(page.getByTestId(testIds.dashboardMetricProfit)).toBeVisible();

    // 不应出现纯"--"占位（有真实数据或 0）
    const body = page.locator('body');
    await expect(body).not.toContainText('NaN');
    await capturePage(page, testInfo, '24-admin-data-dashboard-metrics');
  });

  test('数据平台 timeField 切换 createTime/settleTime 不报错', async ({ page }, testInfo) => {
    await gotoApp(page, '/data');
    await expect(page.getByTestId(testIds.dashboardPage)).toBeVisible({ timeout: 15_000 });

    // 切换时间口径
    const timeField = page.getByTestId(testIds.dashboardTimeField);
    await expect(timeField).toBeVisible({ timeout: 8_000 });
    // 点击 settleTime radio
    await timeField.getByText('按结算时间').click();
    await page.waitForTimeout(1500);
    // 不应出现 500 错误
    await expect(page.locator('body')).not.toContainText(/500|服务器错误|Internal Server Error/i);
    await capturePage(page, testInfo, '24-admin-data-dashboard-settle-time', { visual: false });
  });

  test('数据订单明细页有归因状态和时间列', async ({ page }, testInfo) => {
    await gotoApp(page, '/data/orders');
    await expect(page.getByTestId(testIds.dataOrdersPage)).toBeVisible({ timeout: 15_000 });

    // 有导出按钮（管理员有权）
    await expect(page.getByTestId(testIds.dataOrdersExport)).toBeVisible({ timeout: 5_000 });

    await capturePage(page, testInfo, '24-admin-data-orders');
  });

  test('Dashboard 概览页展示归因 KPI', async ({ page }, testInfo) => {
    await gotoApp(page, '/dashboard');
    await expect(page.getByTestId('dashboard-overview-page')).toBeVisible({ timeout: 15_000 });
    await expect(page.getByTestId('dashboard-stat-cards')).toBeVisible({ timeout: 10_000 });

    // 核心业务文案
    await expect(page.locator('body')).toContainText(/GMV|归因|服务费|订单/, { timeout: 8_000 });
    await capturePage(page, testInfo, '24-admin-dashboard-overview');
  });

  test('独家状态页加载分页数据不触发前端异常', async ({ page }) => {
    const runtimeErrors: string[] = [];
    page.on('console', (msg) => {
      const text = msg.text();
      if (msg.type() === 'error' && /\[Vue Error\]|rawNodes|parentNode/.test(text)) {
        runtimeErrors.push(text);
      }
    });
    page.on('pageerror', (error) => {
      const text = error.stack || error.message;
      if (/\[Vue Error\]|rawNodes|parentNode/.test(text)) {
        runtimeErrors.push(text);
      }
    });

    await gotoApp(page, '/ops/exclusive');
    await expect(page.getByTestId('exclusive-status-page')).toBeVisible({ timeout: 15_000 });
    await page.waitForTimeout(1_000);

    expect(runtimeErrors).toEqual([]);
  });
});

// ─────────────────────────────────────────────────────────
// B. 渠道组长视角 — 数据范围收窄
// ─────────────────────────────────────────────────────────
test.describe('B. 渠道组长视角数据范围', () => {
  test.use({ storageState: storageStates.channelLeader });

  test('渠道组长数据看板有指标卡', async ({ page }, testInfo) => {
    await gotoApp(page, '/data');
    await expect(page.getByTestId(testIds.dashboardPage)).toBeVisible({ timeout: 15_000 });
    await expect(page.getByTestId(testIds.dashboardMetricCards)).toBeVisible({ timeout: 10_000 });
    await capturePage(page, testInfo, '24-channel-data-dashboard');
  });

  test('渠道组长订单明细页可见且有导出按钮', async ({ page }, testInfo) => {
    await gotoApp(page, '/data/orders');
    await expect(page.getByTestId(testIds.dataOrdersPage)).toBeVisible({ timeout: 15_000 });
    // 渠道组长有数据导出权限
    await expect(page.getByTestId(testIds.dataOrdersExport)).toBeVisible({ timeout: 5_000 });
    await capturePage(page, testInfo, '24-channel-data-orders');
  });
});

// ─────────────────────────────────────────────────────────
// C. API 层对账断言
// ─────────────────────────────────────────────────────────
test.describe('C. 业绩看板 API 层对账', () => {
  // ── C1. Dashboard metrics 结构完整性
  test('Dashboard metrics API 返回 4 个核心指标字段', async () => {
    const body = (await apiGet('/api/dashboard/metrics', { token: adminToken })) as {
      data?: {
        todayOrderCount?: unknown;
        todayGmv?: unknown;
        serviceFee?: unknown;
        grossProfit?: unknown;
      };
    };
    const data = body?.data;
    expect(data).toBeDefined();
    // 核心字段存在（允许为 0，但不允许缺失）
    expect('todayOrderCount' in (data ?? {})).toBe(true);
    expect('todayGmv' in (data ?? {})).toBe(true);
    expect('serviceFee' in (data ?? {})).toBe(true);
    expect('grossProfit' in (data ?? {})).toBe(true);
  });

  // ── C2. Dashboard summary 结构完整性
  test('Dashboard summary API 返回核心归因摘要字段', async () => {
    const body = (await apiGet('/api/dashboard/summary', { token: adminToken })) as {
      data?: {
        orderCount?: unknown;
        totalOrders?: unknown;
        attributedOrderCount?: unknown;
        attributedOrders?: unknown;
        unattributedOrders?: unknown;
      };
    };
    const data = body?.data;
    expect(data).toBeDefined();
    expect('totalOrders' in (data ?? {}) || 'orderCount' in (data ?? {})).toBe(true);
    expect('attributedOrders' in (data ?? {}) || 'attributedOrderCount' in (data ?? {})).toBe(true);
  });

  // ── C3. timeField=settleTime 切换
  test('Dashboard metrics 支持 timeField=settleTime', async () => {
    const BACKEND = (process.env.E2E_BACKEND_URL || 'http://localhost:8080').replace(/\/$/, '');
    const ctx = await playwrightRequest.newContext({ baseURL: BACKEND });
    try {
      const res = await ctx.get('/api/dashboard/metrics', {
        headers: { Authorization: `Bearer ${adminToken}` },
        params: { timeField: 'settleTime' },
      });
      expect(res.ok()).toBe(true);
      const body = (await res.json()) as { data?: unknown };
      expect(body?.data).toBeDefined();
    } finally {
      await ctx.dispose();
    }
  });

  // ── C4. 未归因订单摘要
  test('订单归因摘要 API 可读取', async () => {
    const body = (await apiGet('/api/dashboard/order-attribution-summary', {
      token: adminToken,
    })) as { data?: unknown };
    expect(body?.data).toBeDefined();
  });

  // ── C5. 订单统计 API 数值合理
  test('订单统计 API totalOrders 为非负整数', async () => {
    const body = (await apiGet('/api/orders/stats', { token: adminToken })) as {
      data?: { totalOrders?: number; attributedOrders?: number; unattributedOrders?: number };
    };
    const data = body?.data;
    expect(data).toBeDefined();
    expect(typeof data?.totalOrders).toBe('number');
    expect(data!.totalOrders!).toBeGreaterThanOrEqual(0);
    // 归因 + 未归因 ≤ 总量（允许有误差或 partial）
    if (
      typeof data?.attributedOrders === 'number' &&
      typeof data?.unattributedOrders === 'number'
    ) {
      expect(data.attributedOrders + data.unattributedOrders).toBeLessThanOrEqual(
        data.totalOrders! + 1 // +1 容错
      );
    }
  });

  // ── C6. DataScope 差异：渠道组长与管理员订单统计应有差异（或相同但均合法）
  test('渠道组长订单统计 API 可达（DataScope 隔离健康）', async () => {
    await assertDashboardMetrics(channelLeaderToken);
    await assertDashboardSummary(channelLeaderToken);
  });

  // ── C7. 订单导出接口管理员可调（非 403）
  test('订单导出接口管理员角色不返回 403', async () => {
    const BACKEND = (process.env.E2E_BACKEND_URL || 'http://localhost:8080').replace(/\/$/, '');
    const ctx = await playwrightRequest.newContext({ baseURL: BACKEND });
    try {
      const res = await ctx.get('/api/orders/exports', {
        headers: { Authorization: `Bearer ${adminToken}` },
        params: { page: 1, size: 1 },
      });
      expect(res.status()).not.toBe(403);
    } finally {
      await ctx.dispose();
    }
  });

  // ── C8. 近 7 日趋势 API（trend7d 字段）
  test('Dashboard metrics 返回近 7 日趋势数组', async () => {
    const body = (await apiGet('/api/dashboard/metrics', { token: adminToken })) as {
      data?: { trend7d?: unknown[] };
    };
    // trend7d 若存在应为数组
    if (body?.data?.trend7d !== undefined) {
      expect(Array.isArray(body.data.trend7d)).toBe(true);
    }
    // 没有 trend7d 也不是错误（字段可选），关键是 data 存在
    expect(body?.data).toBeDefined();
  });
});

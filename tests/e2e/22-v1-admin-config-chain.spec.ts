/**
 * 22-v1-admin-config-chain.spec.ts
 *
 * V1-P0 管理链配置验收 E2E
 *
 * 验收要点：
 *  1. 管理员可访问系统管理所有子页（用户、角色、配置、操作日志）
 *  2. 用户列表可查询
 *  3. 角色列表可查询，内置角色不可删
 *  4. 系统配置中心可读取内置规则键（寄样限制、保护期等）
 *  5. 管理员可访问抖店联调面板 /system/douyin
 *  6. API 层：管理员可调用健康探针 /system/health
 *  7. API 层：系统配置 API 管理员有权访问
 */
import { test, expect } from '@playwright/test';
import { storageStates } from './helpers/test-data';
import { capturePage } from './helpers/screenshot';
import { loginApi, apiGet, apiPost, seedTestData } from './helpers/api-assertions';
import { gotoApp } from './helpers/page-ready';

test.use({ storageState: storageStates.admin });

let adminToken: string;

test.beforeAll(async () => {
  adminToken = await loginApi('admin');
  await seedTestData(adminToken);
});

test.describe('管理配置链 UI 层验收', () => {
  // ──────────────────────────────────────────────
  // 1. 用户管理页可访问
  // ──────────────────────────────────────────────
  test('管理员可进入用户管理页', async ({ page }, testInfo) => {
    await gotoApp(page, '/system/users');
    await expect(page).toHaveURL(/\/system\/users/, { timeout: 10_000 });
    await expect(page.locator('body')).toContainText(/用户管理|员工管理|系统管理/, {
      timeout: 10_000,
    });
    await capturePage(page, testInfo, '22-admin-system-users');
  });

  // ──────────────────────────────────────────────
  // 2. 角色管理页可访问
  // ──────────────────────────────────────────────
  test('管理员可进入部门管理页', async ({ page }, testInfo) => {
    await gotoApp(page, '/system/depts');
    await expect(page).toHaveURL(/\/system\/depts/, { timeout: 10_000 });
    await expect(page.locator('body')).toContainText(/部门管理|招商组|渠道组/, {
      timeout: 10_000,
    });
    await capturePage(page, testInfo, '22-admin-system-depts');
  });

  test('管理员可进入角色管理页', async ({ page }, testInfo) => {
    await gotoApp(page, '/system/roles');
    await expect(page.locator('body')).toContainText(/角色管理|角色列表|Role/, {
      timeout: 10_000,
    });
    await capturePage(page, testInfo, '22-admin-system-roles');
  });

  // ──────────────────────────────────────────────
  // 3. 系统配置中心可访问
  // ──────────────────────────────────────────────
  test('管理员可进入系统配置中心并看到业务规则', async ({ page }, testInfo) => {
    await gotoApp(page, '/system/config');
    await expect(page.locator('body')).toContainText(
      /配置|规则|寄样|保护期|sample|config/i,
      { timeout: 15_000 }
    );
    await capturePage(page, testInfo, '22-admin-system-config');
  });

  // ──────────────────────────────────────────────
  // 4. 操作日志中心可访问
  // ──────────────────────────────────────────────
  test('管理员可进入操作日志中心', async ({ page }, testInfo) => {
    await gotoApp(page, '/system/operation-logs');
    await expect(page.locator('body')).toContainText(/日志|操作|Log/i, {
      timeout: 10_000,
    });
    await capturePage(page, testInfo, '22-admin-operation-logs');
  });

  // ──────────────────────────────────────────────
  // 5. 抖店联调面板可访问
  // ──────────────────────────────────────────────
  test('管理员可访问抖店联调面板', async ({ page }, testInfo) => {
    await gotoApp(page, '/system/douyin');
    await expect(page).toHaveURL(/\/system\/douyin/, { timeout: 10_000 });
    await expect(page.locator('body')).toContainText(/Token|联调|抖店|活动/, {
      timeout: 10_000,
    });
    await capturePage(page, testInfo, '22-admin-system-douyin');
  });

  // ──────────────────────────────────────────────
  // 6. Dashboard 概览页对管理员可见
  // ──────────────────────────────────────────────
  test('管理员看到 Dashboard 概览（归因概览）', async ({ page }, testInfo) => {
    await gotoApp(page, '/dashboard');
    await expect(page.getByTestId('dashboard-overview-page')).toBeVisible({ timeout: 15_000 });
    await expect(page.getByTestId('dashboard-stat-cards')).toBeVisible({ timeout: 10_000 });
    // 核心指标文案
    await expect(page.locator('body')).toContainText(/GMV|归因|订单|服务费/, {
      timeout: 10_000,
    });
    await capturePage(page, testInfo, '22-admin-dashboard-overview');
  });
});

test.describe('管理配置链 API 层断言', () => {
  // ──────────────────────────────────────────────
  // 7. 健康探针
  // ──────────────────────────────────────────────
  test('后端健康探针可达 (UP)', async () => {
    const body = (await apiGet('/api/system/health', { token: adminToken })) as {
      status?: string;
    };
    expect(body?.status?.toLowerCase()).toMatch(/up/);
  });

  // ──────────────────────────────────────────────
  // 8. 用户列表 API
  // ──────────────────────────────────────────────
  test('用户列表 API 管理员可访问', async () => {
    const body = (await apiGet('/api/users', { token: adminToken })) as {
      data?: { total?: number };
    };
    expect(body?.data).toBeDefined();
  });

  // ──────────────────────────────────────────────
  // 9. 角色列表 API
  // ──────────────────────────────────────────────
  test('角色列表 API 管理员可访问', async () => {
    const body = (await apiGet('/api/roles', { token: adminToken })) as {
      data?: unknown;
    };
    expect(body?.data).toBeDefined();
  });

  // ──────────────────────────────────────────────
  // 10. 系统配置 API
  // ──────────────────────────────────────────────
  test('系统配置 API 管理员可读取', async () => {
    const body = (await apiGet('/api/configs', { token: adminToken })) as {
      data?: unknown;
    };
    expect(body?.data).toBeDefined();
  });

  // ──────────────────────────────────────────────
  // 11. Dashboard summary 管理员视角
  // ──────────────────────────────────────────────
  test('Dashboard summary API 管理员视角有数据', async () => {
    const body = (await apiGet('/api/dashboard/summary', { token: adminToken })) as {
      data?: unknown;
    };
    expect(body?.data).toBeDefined();
  });

  // ──────────────────────────────────────────────
  // 12. 归因重算 dryRun 接口管理员可触发
  // ──────────────────────────────────────────────
  test('归因重算 dryRun 接口管理员可调用', async () => {
    const body = (await apiPost('/api/orders/replay-attribution', {
      dryRun: true,
      limit: 5,
    }, { token: adminToken })) as {
      data?: { scanned?: number };
    };
    // dryRun 返回 scanned 字段
    expect(typeof body?.data?.scanned).toBe('number');
  });
});

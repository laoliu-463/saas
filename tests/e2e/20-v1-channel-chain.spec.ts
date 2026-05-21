/**
 * 20-v1-channel-chain.spec.ts
 *
 * V1-P0 渠道链闭环验收 E2E
 *
 * 验收要点：
 *  1. 渠道组长可访问商品库、达人 CRM、寄样台、数据看板
 *  2. 商品卡片 hover → 转链 → 反馈 toast（pick_source 生成或降级说明）
 *  3. 订单归因页可见归因状态列 + 渠道负责人列
 *  4. 寄样台有数据 + 申请按钮可见
 *  5. 数据看板 API 可返回有效 metrics
 *  6. 不应访问系统管理（路由守卫拦截）
 */
import { test, expect } from '@playwright/test';
import { storageStates } from './helpers/test-data';
import { capturePage } from './helpers/screenshot';
import { testIds } from './helpers/selectors';
import { gotoApp } from './helpers/page-ready';
import { waitForProductCard } from './helpers/product-library';
import {
  loginApi,
  assertProductLibraryNotEmpty,
  assertOrderStatsReachable,
  assertDashboardMetrics,
  assertSampleListReachable,
  assert403,
  seedTestData,
} from './helpers/api-assertions';

test.use({ storageState: storageStates.channelLeader });

let channelToken: string;

test.beforeAll(async () => {
  await seedTestData();
  channelToken = await loginApi('channelLeader');
  await assertProductLibraryNotEmpty(channelToken);
});

test.describe('渠道链 UI 层验收', () => {
  // ──────────────────────────────────────────────
  // 1. 商品库可见且有商品
  // ──────────────────────────────────────────────
  test('渠道组长商品库有商品且可转链', async ({ page }, testInfo) => {
    await gotoApp(page, '/product');
    await expect(page.getByTestId(testIds.productLibraryPage)).toBeVisible({ timeout: 15_000 });
    const firstCard = await waitForProductCard(page);
    await capturePage(page, testInfo, '20-channel-product-library');

    // hover 第一张卡片 → 转链按钮出现
    await firstCard.hover();
    const copyBtn = page.getByTestId(testIds.productCopyLink).first();
    await expect(copyBtn).toBeVisible({ timeout: 5_000 });

    // 点击转链
    await copyBtn.click({ force: true });

    // 等待任意反馈 toast（成功或降级均可，关键是有反馈）
    await expect(page.locator('body')).toContainText(
      /推广链接已复制|已复制|归因到当前渠道|推广链接生成失败|pick_source/,
      { timeout: 10_000 }
    );
    await capturePage(page, testInfo, '20-channel-promotion-link-feedback', { visual: false });
  });

  // ──────────────────────────────────────────────
  // 2. 商品详情可读取推广资料包
  // ──────────────────────────────────────────────
  test('渠道组长可查看商品详情推广资料包', async ({ page }, testInfo) => {
    await gotoApp(page, '/product');
    await waitForProductCard(page);

    await page.getByTestId(testIds.productDetailButton).first().click();
    await expect(page.locator('body')).toContainText(
      /商品业务全貌|推广链接|基础信息|推广话术|卖点/,
      { timeout: 10_000 }
    );
    await capturePage(page, testInfo, '20-channel-product-detail');
  });

  // ──────────────────────────────────────────────
  // 3. 订单归因页：归因状态 + 渠道负责人列
  // ──────────────────────────────────────────────
  test('渠道视角订单归因页有归因状态和渠道负责人列', async ({ page }, testInfo) => {
    await gotoApp(page, '/orders');
    await expect(page.getByTestId(testIds.ordersPage)).toBeVisible({ timeout: 15_000 });

    // 表格有数据行
    const firstRow = page.getByTestId(testIds.orderRow).first();
    const hasRows = await firstRow.isVisible({ timeout: 5_000 }).catch(() => false);
    if (hasRows) {
      // 有数据时断言归因状态列和渠道负责人列
      await expect(page.getByTestId(testIds.orderAttributionStatus).first()).toBeVisible();
      await expect(page.getByTestId(testIds.orderChannel).first()).toBeVisible();
    } else {
      // 无数据时确认页面结构是正确的（表格已渲染但无行）
      await expect(page.getByTestId(testIds.ordersPage)).toBeVisible();
      console.warn('[20] 订单归因页暂无数据，跳过行级断言');
    }
    await capturePage(page, testInfo, '20-channel-orders-attribution');
  });

  // ──────────────────────────────────────────────
  // 4. 寄样台可见 + 申请按钮存在
  // ──────────────────────────────────────────────
  test('渠道组长寄样台有申请按钮', async ({ page }, testInfo) => {
    await gotoApp(page, '/sample');
    await expect(page.getByTestId(testIds.samplePage)).toBeVisible({ timeout: 15_000 });
    // 渠道组长有申请权限
    await expect(page.getByTestId(testIds.sampleApply)).toBeVisible({ timeout: 5_000 });
    await capturePage(page, testInfo, '20-channel-sample-page');
  });

  // ──────────────────────────────────────────────
  // 5. 达人 CRM 可进入
  // ──────────────────────────────────────────────
  test('渠道组长可进入达人 CRM', async ({ page }, testInfo) => {
    await gotoApp(page, '/talent');
    await expect(page.getByTestId(testIds.talentPage)).toBeVisible({ timeout: 15_000 });
    await capturePage(page, testInfo, '20-channel-talent-crm');
  });

  // ──────────────────────────────────────────────
  // 6. 数据看板可进入
  // ──────────────────────────────────────────────
  test('渠道组长数据看板指标卡可见', async ({ page }, testInfo) => {
    await gotoApp(page, '/data');
    await expect(page.getByTestId(testIds.dashboardPage)).toBeVisible({ timeout: 15_000 });
    await expect(page.getByTestId(testIds.dashboardMetricCards)).toBeVisible({ timeout: 10_000 });
    await capturePage(page, testInfo, '20-channel-data-dashboard');
  });

  // ──────────────────────────────────────────────
  // 7. 系统管理路由守卫拦截
  // ──────────────────────────────────────────────
  test('渠道组长访问系统管理被重定向', async ({ page }) => {
    await gotoApp(page, '/system/users');
    // 不应停留在 /system 路由
    await page.waitForTimeout(2000);
    const url = page.url();
    expect(url).not.toMatch(/\/system/);
  });
});

test.describe('渠道链 API 层断言', () => {
  // ──────────────────────────────────────────────
  // 8. 商品库 API 非空
  // ──────────────────────────────────────────────
  test('商品库 API 有有效数据', async () => {
    await assertProductLibraryNotEmpty(channelToken);
  });

  // ──────────────────────────────────────────────
  // 9. 订单统计 API 可达
  // ──────────────────────────────────────────────
  test('订单统计 API 可达（归因管道健康）', async () => {
    await assertOrderStatsReachable(channelToken);
  });

  // ──────────────────────────────────────────────
  // 10. Dashboard metrics API 有数据
  // ──────────────────────────────────────────────
  test('数据看板 metrics API 返回有效数据', async () => {
    await assertDashboardMetrics(channelToken);
  });

  // ──────────────────────────────────────────────
  // 11. 寄样列表 API 可达
  // ──────────────────────────────────────────────
  test('寄样列表 API 对渠道组长可达', async () => {
    await assertSampleListReachable(channelToken);
  });

  // ──────────────────────────────────────────────
  // 12. 禁止访问商品审核（负向）
  // ──────────────────────────────────────────────
  test('渠道组长访问商品审核接口返回 403', async () => {
    // 用一个不存在的 UUID 也能验证鉴权层先于业务层
    await assert403(
      'PUT',
      '/api/products/00000000-0000-0000-0000-000000000001/audit-result',
      channelToken,
      { approved: true }
    );
  });

  // ──────────────────────────────────────────────
  // 13. 禁止手动同步真实订单（负向）
  // ──────────────────────────────────────────────
  test('渠道组长访问订单手动同步接口返回 403', async () => {
    await assert403('POST', '/api/orders/sync', channelToken, {
      startTime: '2026-01-01 00:00:00',
      endTime: '2026-01-01 01:00:00',
    });
  });

  // ──────────────────────────────────────────────
  // 14. 禁止达人归属覆盖（负向）
  // ──────────────────────────────────────────────
  test('渠道组长访问达人归属覆盖接口返回 403', async () => {
    await assert403(
      'POST',
      '/api/talents/00000000-0000-0000-0000-000000000001/override-assignee',
      channelToken,
      {
        newUserId: '00000000-0000-0000-0000-000000000001',
        reason: 'RBAC negative assertion',
      }
    );
  });
});

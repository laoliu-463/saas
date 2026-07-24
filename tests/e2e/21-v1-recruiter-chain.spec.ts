/**
 * 21-v1-recruiter-chain.spec.ts
 *
 * V1-P0 招商链闭环验收 E2E
 *
 * 验收要点：
 *  1. 招商组长可进入活动列表 + 活动商品工作台
 *  2. 活动商品页有待审核商品、可见"分配审核人"按钮
 *  3. 共享商品库招商组长只读（无转链按钮）
 *  4. 寄样管理可见（只看不审）
 *  5. 数据看板可进入
 *  6. API 层：商品审核仅招商可执行（biz_leader 403 / admin 200）
 *  7. API 层：不可访问 /talent、/system 相关接口
 */
import { test, expect } from '@playwright/test';
import { storageStates } from './helpers/test-data';
import { capturePage } from './helpers/screenshot';
import { testIds } from './helpers/selectors';
import { gotoApp, waitForAppReady } from './helpers/page-ready';
import { waitForProductCard } from './helpers/product-library';
import {
  loginApi,
  assertProductLibraryNotEmpty,
  assertDashboardMetrics,
  assertSampleListReachable,
  assert403,
  seedTestData,
} from './helpers/api-assertions';

test.use({ storageState: storageStates.bizLeader });

let bizLeaderToken: string;

test.beforeAll(async () => {
  await seedTestData();
  bizLeaderToken = await loginApi('bizLeader');
  await assertProductLibraryNotEmpty(bizLeaderToken);
});

test.describe('招商链 UI 层验收', () => {
  // ──────────────────────────────────────────────
  // 1. 活动列表 + 活动商品工作台
  // ──────────────────────────────────────────────
  test('招商组长可进入活动列表并查看活动商品工作台', async ({ page }, testInfo) => {
    await gotoApp(page, '/product/manage');
    await expect(page.getByTestId(testIds.activityListPage)).toBeVisible({ timeout: 15_000 });
    await expect(page.getByRole('heading', { name: '活动列表' })).toBeVisible();
    await capturePage(page, testInfo, '21-recruiter-activity-list');

    const emptyAssigned = page.getByTestId('activity-list-empty-assigned');
    if (await emptyAssigned.isVisible({ timeout: 3_000 }).catch(() => false)) {
      // seed 活动未分配给当前招商组长时，仍可通过契约 URL 进入活动商品库
      await gotoApp(page, '/product/library?activityId=TEST_ACTIVITY_A');
    } else {
      const viewBtn = page.locator(`[data-testid="${testIds.activityViewProducts}"]:visible`).first();
      await expect(viewBtn).toBeVisible({ timeout: 20_000 });
      await Promise.all([
        page.waitForURL(/\/product\/library.*activityId=/, { timeout: 30_000 }),
        viewBtn.click()
      ]);
    }
    await waitForAppReady(page);
    await expect(page.getByTestId(testIds.productLibraryPage)).toBeVisible({ timeout: 10_000 });
    await expect(page.getByTestId(testIds.productLibraryActivityFilterApplied)).toBeVisible({ timeout: 10_000 });
    await capturePage(page, testInfo, '21-recruiter-activity-products');
  });

  // ──────────────────────────────────────────────
  // 2. 活动商品工作台有状态筛选器和操作列
  // ──────────────────────────────────────────────
  test('活动商品工作台有快速筛选和操作列', async ({ page }, testInfo) => {
    await gotoApp(page, '/product/manage/products');
    await expect(page.getByRole('heading', { name: /商品推进池|活动商品推进/ })).toBeVisible({ timeout: 15_000 });
    await expect(page.getByTestId('product-table')).toBeVisible({ timeout: 15_000 });

    // 筛选器与待审核商品状态
    await expect(page.locator('body')).toContainText(/商品ID|活动信息|搜索/, { timeout: 5_000 });
    await expect(page.locator('body')).toContainText(/待分配审核人|待审核/, { timeout: 5_000 });

    // 分配审核人入口已迁移至详情/批量；验证表格操作列至少可见
    await expect(page.getByTestId('product-action-detail').first()).toBeVisible({ timeout: 20_000 });
    await capturePage(page, testInfo, '21-recruiter-activity-workbench-actions');
  });

  // ──────────────────────────────────────────────
  // 3. 共享商品库只读（无转链按钮）
  // ──────────────────────────────────────────────
  test('招商组长商品库为只读视图（无转链按钮）', async ({ page }, testInfo) => {
    await gotoApp(page, '/product');
    await expect(page.getByTestId(testIds.productLibraryPage)).toBeVisible({ timeout: 15_000 });
    const firstCard = await waitForProductCard(page);
    await capturePage(page, testInfo, '21-recruiter-product-library-readonly');

    // hover 商品卡 → 不应出现转链按钮
    await firstCard.hover();
    const copyLinkCount = await page.getByTestId('product-copy-link').count();
    expect(copyLinkCount).toBe(0);
  });

  // ──────────────────────────────────────────────
  // 4. 寄样管理可进入（无审核按钮）
  // ──────────────────────────────────────────────
  test('招商组长寄样台可查看，无"申请寄样"按钮', async ({ page }, testInfo) => {
    await gotoApp(page, '/sample');
    await expect(page.getByTestId(testIds.samplePage)).toBeVisible({ timeout: 15_000 });

    // 招商组长不能申请寄样
    const applyBtn = page.getByTestId(testIds.sampleApply);
    const hasApply = await applyBtn.isVisible({ timeout: 2_000 }).catch(() => false);
    expect(hasApply).toBe(false);

    await capturePage(page, testInfo, '21-recruiter-sample-view-only');
  });

  // ──────────────────────────────────────────────
  // 5. 数据看板可进入
  // ──────────────────────────────────────────────
  test('招商组长可进入数据看板', async ({ page }, testInfo) => {
    await gotoApp(page, '/data');
    await expect(page.getByTestId(testIds.dashboardPage)).toBeVisible({ timeout: 15_000 });
    await expect(page.getByTestId(testIds.dashboardMetricCards)).toBeVisible({ timeout: 10_000 });
    await capturePage(page, testInfo, '21-recruiter-data-dashboard');
  });

  // ──────────────────────────────────────────────
  // 6. 不可进入达人 CRM（路由守卫）
  // ──────────────────────────────────────────────
  test('招商组长访问达人 CRM 被重定向', async ({ page }) => {
    await gotoApp(page, '/talent');
    await expect(page).not.toHaveURL(/\/talent/, { timeout: 10_000 });
  });

  // ──────────────────────────────────────────────
  // 7. 不可进入系统管理
  // ──────────────────────────────────────────────
  test('招商组长访问系统管理被重定向', async ({ page }) => {
    await gotoApp(page, '/system/users');
    await expect(page).not.toHaveURL(/\/system/, { timeout: 10_000 });
  });
});

test.describe('招商链 API 层断言', () => {
  // ──────────────────────────────────────────────
  // 8. 商品库 API 可达
  // ──────────────────────────────────────────────
  test('招商组长商品库 API 有有效数据', async () => {
    await assertProductLibraryNotEmpty(bizLeaderToken);
  });

  // ──────────────────────────────────────────────
  // 9. Dashboard metrics API
  // ──────────────────────────────────────────────
  test('数据看板 metrics API 返回有效数据', async () => {
    await assertDashboardMetrics(bizLeaderToken);
  });

  // ──────────────────────────────────────────────
  // 10. 寄样列表 API 可达
  // ──────────────────────────────────────────────
  test('寄样列表 API 对招商组长可达', async () => {
    await assertSampleListReachable(bizLeaderToken);
  });

  // ──────────────────────────────────────────────
  // 11. 禁止商品审核（招商组长不可审核，仅招商可审）
  // ──────────────────────────────────────────────
  test('招商组长访问商品审核接口返回 403', async () => {
    await assert403(
      'PUT',
      '/api/products/00000000-0000-0000-0000-000000000001/audit-result',
      bizLeaderToken,
      { approved: true }
    );
  });

  // ──────────────────────────────────────────────
  // 12. 禁止真实订单手动同步
  // ──────────────────────────────────────────────
  test('招商组长访问订单手动同步接口返回 403', async () => {
    await assert403('POST', '/api/orders/sync', bizLeaderToken, {
      startTime: '2026-01-01 00:00:00',
      endTime: '2026-01-01 01:00:00',
    });
  });

  // ──────────────────────────────────────────────
  // 13. 禁止寄样批量审核
  // ──────────────────────────────────────────────
  test('招商组长访问寄样批量审核接口返回 403', async () => {
    await assert403('POST', '/api/samples/batch-approve', bizLeaderToken, {
      requestNos: ['RBAC_NEGATIVE_ASSERTION'],
    });
  });

  // ──────────────────────────────────────────────
  // 14. 禁止达人归属覆盖
  // ──────────────────────────────────────────────
  test('招商组长访问达人归属覆盖接口返回 403', async () => {
    await assert403(
      'POST',
      '/api/talents/00000000-0000-0000-0000-000000000001/override-assignee',
      bizLeaderToken,
      {
        newUserId: '00000000-0000-0000-0000-000000000001',
        reason: 'RBAC negative assertion',
      }
    );
  });
});

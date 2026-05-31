/**
 * 活动分配与招商可见性 API 契约 E2E（test/mock 环境）
 *
 * 验证：
 * - admin 可走 assignmentFilter=all（上游/全量路径）
 * - 招商角色传 all 仍强制 mine（本地库分页）
 * - 招商无法读取未分配给自己的活动商品
 */
import { test, expect } from '@playwright/test';
import { apiGet, loginApi, seedTestData } from './helpers/api-assertions';
import { storageStates } from './helpers/test-data';
import { gotoApp } from './helpers/page-ready';

type ActivityListPayload = {
  data?: {
    total?: number;
    activityList?: Array<{ activityId?: string | number; recruiterUserId?: string }>;
  };
};

test.beforeAll(async () => {
  await seedTestData();
});

test('招商角色活动列表强制 mine，且未分配活动商品不可访问', async () => {
  const adminToken = await loginApi('admin');
  const staffToken = await loginApi('bizStaff');

  const adminAll = (await apiGet(
    '/api/colonel/activities',
    { token: adminToken },
    { page: 1, pageSize: 20, assignmentFilter: 'all' }
  )) as ActivityListPayload;

  const staffForcedMine = (await apiGet(
    '/api/colonel/activities',
    { token: staffToken },
    { page: 1, pageSize: 20, assignmentFilter: 'all' }
  )) as ActivityListPayload;

  const staffMine = (await apiGet(
    '/api/colonel/activities',
    { token: staffToken },
    { page: 1, pageSize: 20, assignmentFilter: 'mine' }
  )) as ActivityListPayload;

  expect(staffForcedMine?.data?.total).toBe(staffMine?.data?.total);

  const adminActivities = adminAll?.data?.activityList || [];
  const firstActivityId = adminActivities[0]?.activityId;
  if (!firstActivityId) {
    test.skip(true, '当前环境无活动样本，跳过分配与越权验证');
    return;
  }

  const recruiters = (await apiGet('/api/users/master-data/recruiters', { token: adminToken }, { limit: 20 })) as {
    data?: Array<{ userId?: string; id?: string }>;
  };
  const staffUser = (recruiters.data || []).find((item) => String(item.userId || item.id || '').length > 0);
  const assigneeId = staffUser?.userId || staffUser?.id;
  if (!assigneeId) {
    test.skip(true, '无招商候选人，跳过分配验证');
    return;
  }

  const ctx = await (await import('@playwright/test')).request.newContext({
    baseURL: (process.env.E2E_BACKEND_URL || 'http://127.0.0.1:8080').replace(/\/$/, ''),
    ignoreHTTPSErrors: true
  });
  try {
    const assignRes = await ctx.put(`/api/colonel/activities/${firstActivityId}/assignee`, {
      headers: { Authorization: `Bearer ${adminToken}`, 'Content-Type': 'application/json' },
      data: { assigneeId }
    });
    expect(assignRes.ok()).toBeTruthy();

    const staffAfterAssign = (await apiGet(
      '/api/colonel/activities',
      { token: staffToken },
      { page: 1, pageSize: 20, assignmentFilter: 'mine' }
    )) as ActivityListPayload;
    const assignedIds = (staffAfterAssign.data?.activityList || []).map((row) => String(row.activityId));
    expect(assignedIds).toContain(String(firstActivityId));

    const unassignedActivity = adminActivities.find((row) => String(row.activityId) !== String(firstActivityId));
    if (unassignedActivity?.activityId) {
      const forbidden = await ctx.get(`/api/colonel/activities/${unassignedActivity.activityId}/products`, {
        headers: { Authorization: `Bearer ${staffToken}` },
        params: { count: 10 }
      });
      expect(forbidden.status()).toBe(403);
    }
  } finally {
    await ctx.dispose();
  }
});

test.describe('活动列表页面', () => {
  test.use({ storageState: storageStates.bizStaff });

  test('biz_staff 可访问活动列表页面入口', async ({ page }) => {
    await gotoApp(page, '/product/manage');
    await expect(page.getByTestId('activity-list-page')).toBeVisible({ timeout: 20_000 });
  });
});

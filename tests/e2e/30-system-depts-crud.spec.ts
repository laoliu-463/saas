import { expect, test, type Page, type Route } from '@playwright/test';
import { storageStates } from './helpers/test-data';
import { gotoApp } from './helpers/page-ready';

test.use({ storageState: storageStates.admin });

type DeptNode = {
  id: string;
  deptCode: string;
  deptName: string;
  parentId: string | null;
  deptType?: string;
  leader: string | null;
  leaderUserId?: string | null;
  sortOrder: number;
  status: number;
  remark: string | null;
  children?: DeptNode[];
};

const initialDepts = (): DeptNode[] => [
  {
    id: 'dept-biz',
    deptCode: 'BIZ',
    deptName: '招商部',
    parentId: null,
    deptType: 'department',
    leader: null,
    leaderUserId: null,
    sortOrder: 1,
    status: 1,
    remark: null,
    children: []
  }
];

async function fulfillJson(route: Route, data: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(data)
  });
}

async function mockDeptApis(page: Page) {
  let depts = initialDepts();
  let lastDeptUpdatePayload: Record<string, unknown> | null = null;

  await page.route('**/api/depts**', async (route) => {
    const url = new URL(route.request().url());
    const method = route.request().method();

    if (method === 'GET' && url.pathname.endsWith('/api/depts/tree')) {
      await fulfillJson(route, { code: 200, data: depts });
      return;
    }

    if (method === 'GET' && url.pathname.endsWith('/api/depts/dept-biz/stats')) {
      await fulfillJson(route, {
        code: 200,
        data: { deptId: 'dept-biz', memberCount: 1, recruiterGroupCount: 0, channelGroupCount: 0 }
      });
      return;
    }

    if (method === 'GET' && url.pathname.endsWith('/api/depts/dept-biz/members')) {
      await fulfillJson(route, { code: 200, data: { records: [], total: 0 } });
      return;
    }

    if (method === 'GET' && url.pathname.endsWith('/api/depts/dept-biz/groups')) {
      await fulfillJson(route, { code: 200, data: [] });
      return;
    }

    if (method === 'GET' && url.pathname.endsWith('/api/depts/dept-qa/stats')) {
      await fulfillJson(route, {
        code: 200,
        data: { deptId: 'dept-qa', memberCount: 0, recruiterGroupCount: 0, channelGroupCount: 0 }
      });
      return;
    }

    if (method === 'GET' && url.pathname.endsWith('/api/depts/dept-qa/members')) {
      await fulfillJson(route, { code: 200, data: { records: [], total: 0 } });
      return;
    }

    if (method === 'GET' && url.pathname.endsWith('/api/depts/dept-qa/groups')) {
      await fulfillJson(route, { code: 200, data: [] });
      return;
    }

    if (method === 'POST' && url.pathname.endsWith('/api/depts')) {
      const payload = route.request().postDataJSON() as Partial<DeptNode>;
      const nextDept: DeptNode = {
        id: 'dept-qa',
        deptCode: String(payload.deptCode),
        deptName: String(payload.deptName),
        parentId: payload.parentId ?? null,
        deptType: payload.deptType || 'department',
        leader: typeof payload.leader === 'string' ? payload.leader : null,
        leaderUserId: typeof payload.leaderUserId === 'string' ? payload.leaderUserId : null,
        sortOrder: Number(payload.sortOrder ?? 0),
        status: Number(payload.status ?? 1),
        remark: payload.remark ?? null,
        children: []
      };
      depts = [...depts, nextDept];
      await fulfillJson(route, { code: 200, data: nextDept });
      return;
    }

    if (method === 'PUT' && url.pathname.endsWith('/api/depts/dept-qa')) {
      const payload = route.request().postDataJSON() as Partial<DeptNode>;
      lastDeptUpdatePayload = payload as Record<string, unknown>;
      depts = depts.map((dept) =>
        dept.id === 'dept-qa'
          ? {
              ...dept,
              deptName: String(payload.deptName),
              leader: typeof payload.leader === 'string' ? payload.leader : null,
              leaderUserId: typeof payload.leaderUserId === 'string' ? payload.leaderUserId : null,
              sortOrder: Number(payload.sortOrder ?? dept.sortOrder),
              status: Number(payload.status ?? dept.status),
              remark: payload.remark ?? null
            }
          : dept
      );
      await fulfillJson(route, { code: 200, data: depts.find((dept) => dept.id === 'dept-qa') });
      return;
    }

    if (method === 'DELETE' && url.pathname.endsWith('/api/depts/dept-qa')) {
      depts = depts.filter((dept) => dept.id !== 'dept-qa');
      await fulfillJson(route, { code: 200, data: true });
      return;
    }

    await route.continue();
  });

  await page.route('**/api/users**', async (route) => {
    const method = route.request().method();
    if (method === 'GET') {
      await fulfillJson(route, {
        code: 200,
        data: {
          records: [
            {
              id: 'user-leader-01',
              username: 'biz_leader_01',
              realName: '招商组长',
              status: 1
            }
          ],
          total: 1
        }
      });
      return;
    }
    await route.continue();
  });

  return {
    getLastDeptUpdatePayload: () => lastDeptUpdatePayload
  };
}

async function fillDeptForm(page: Page, values: { code?: string; name: string }) {
  if (values.code) {
    await page.getByTestId('dept-code-input').locator('input').fill(values.code);
  }
  await page.getByTestId('dept-name-input').locator('input').fill(values.name);
}

test('管理员可完成部门新增、编辑、删除闭环', async ({ page }) => {
  const apiState = await mockDeptApis(page);

  await gotoApp(page, '/system/depts');
  await expect(page.getByTestId('system-depts-tree')).toBeVisible({ timeout: 15_000 });
  await expect(page.locator('body')).toContainText('招商部');

  await page.getByTestId('dept-add-btn').click();
  await expect(page.getByTestId('dept-form-modal')).toBeVisible();
  await fillDeptForm(page, { code: 'QA', name: 'QA 测试部' });
  await page.getByTestId('dept-submit-btn').click();
  await expect(page.locator('body')).toContainText('部门已创建');

  await page.getByText('QA 测试部').click();
  await expect(page.getByTestId('system-depts-detail')).toContainText('QA 测试部');
  await page.getByRole('button', { name: '编辑' }).click();
  await fillDeptForm(page, { name: 'QA 测试部更新' });
  await page.getByTestId('dept-leader-select').click();
  await page.keyboard.type('招商');
  await page.getByText('招商组长 (biz_leader_01)').click();
  await page.getByTestId('dept-submit-btn').click();
  await expect(page.locator('body')).toContainText('部门已更新');
  expect(apiState.getLastDeptUpdatePayload()).toMatchObject({
    leaderUserId: 'user-leader-01',
    leader: '招商组长'
  });

  await page.getByRole('button', { name: '删除' }).click();
  await page.getByRole('button', { name: '确认' }).click();
  await expect(page.locator('body')).toContainText('已删除');
});

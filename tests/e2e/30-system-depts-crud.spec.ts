import { expect, test, type Page, type Route } from '@playwright/test';
import { storageStates } from './helpers/test-data';
import { gotoApp } from './helpers/page-ready';

test.use({ storageState: storageStates.admin });

type DeptNode = {
  id: string;
  deptCode: string;
  deptName: string;
  parentId: string | null;
  leader: string | null;
  sortOrder: number;
  status: number;
  remark: string | null;
  children?: DeptNode[];
};

const initialDepts = (): DeptNode[] => [
  {
    id: 'dept-biz',
    deptCode: 'BIZ',
    deptName: '招商组',
    parentId: null,
    leader: '招商负责人',
    sortOrder: 1,
    status: 1,
    remark: null,
    children: []
  },
  {
    id: 'dept-channel',
    deptCode: 'CHANNEL',
    deptName: '渠道组',
    parentId: null,
    leader: '渠道负责人',
    sortOrder: 2,
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

  await page.route('**/api/depts**', async (route) => {
    const url = new URL(route.request().url());
    const method = route.request().method();

    if (method === 'GET' && url.pathname.endsWith('/api/depts/tree')) {
      await fulfillJson(route, { code: 200, data: depts });
      return;
    }

    if (method === 'POST' && url.pathname.endsWith('/api/depts')) {
      const payload = route.request().postDataJSON() as Partial<DeptNode>;
      const nextDept: DeptNode = {
        id: 'dept-qa',
        deptCode: String(payload.deptCode),
        deptName: String(payload.deptName),
        parentId: payload.parentId ?? null,
        leader: payload.leader ?? null,
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
      depts = depts.map((dept) =>
        dept.id === 'dept-qa'
          ? {
              ...dept,
              deptName: String(payload.deptName),
              leader: payload.leader ?? null,
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
}

async function fillDeptForm(page: Page, values: { code?: string; name: string; leader?: string }) {
  if (values.code) {
    await page.getByTestId('dept-code-input').locator('input').fill(values.code);
  }
  await page.getByTestId('dept-name-input').locator('input').fill(values.name);
  if (values.leader) {
    await page.getByTestId('dept-leader-input').locator('input').fill(values.leader);
  }
}

test('管理员可完成部门新增、编辑、删除闭环', async ({ page }) => {
  await mockDeptApis(page);

  await gotoApp(page, '/system/depts');
  await expect(page.getByTestId('system-depts-table')).toBeVisible({ timeout: 15_000 });
  await expect(page.locator('body')).toContainText('招商组');

  await page.getByTestId('dept-add-btn').click();
  await expect(page.getByTestId('dept-form-modal')).toBeVisible();
  await fillDeptForm(page, { code: 'QA', name: 'QA 测试组', leader: '测试负责人' });
  await page.getByTestId('dept-submit-btn').click();
  await expect(page.locator('body')).toContainText('部门已创建');
  await expect(page.locator('body')).toContainText('QA 测试组');

  await page.getByTestId('dept-edit-dept-qa').click();
  await fillDeptForm(page, { name: 'QA 测试组更新', leader: '新负责人' });
  await page.getByTestId('dept-submit-btn').click();
  await expect(page.locator('body')).toContainText('部门已更新');
  await expect(page.locator('body')).toContainText('QA 测试组更新');

  await page.getByTestId('dept-delete-dept-qa').click();
  await expect(page.locator('body')).toContainText('确认删除该部门？');
  await page.getByRole('button', { name: '确认' }).click();
  await expect(page.locator('body')).toContainText('部门已删除');
  await expect(page.locator('body')).not.toContainText('QA 测试组更新');
});

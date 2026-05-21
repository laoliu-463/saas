/**
 * 23-v1-rbac-scope.spec.ts
 *
 * V1-P0 RBAC + 数据范围验收 E2E
 *
 * 验收要点（全部 API 层，不依赖浏览器）：
 *
 * A. 运营（ops_staff）权限边界
 *    - 可访问：/samples（仅 PENDING_SHIP 及以后状态）
 *    - 禁止：/products, /talent, /orders/sync, 寄样创建/审核/完结
 *
 * B. 渠道专员（channel_staff）权限边界
 *    - 可访问：/products, /talent, /samples（仅本人）
 *    - 禁止：订单手动同步, 商品审核, 达人归属覆盖
 *
 * C. 招商组长（biz_leader）权限边界
 *    - 可访问：/products, /product/manage（活动）, /samples（全组）
 *    - 禁止：商品审核, 真实订单同步, 寄样批量审核
 *
 * D. 渠道组长（channel_leader）权限边界
 *    - 可访问：/products, /talent, /samples, /orders（归因）
 *    - 禁止：商品审核, 订单手动同步, 达人归属覆盖
 *    - 特殊：寄样导出可用（GET /samples/exports → 非 403）
 *
 * E. 寄样 7 天限制（API 层语义）
 *    - ops_staff 访问 GET /samples?status=PENDING_AUDIT → 403
 *    - ops_staff 访问 POST /samples → 403
 *    - ops_staff 访问 PUT /samples/{id}/status（COMPLETED）→ 403
 */
import { test, expect } from '@playwright/test';
import { loginApi, assert403, apiGet, seedTestData } from './helpers/api-assertions';
import { request as playwrightRequest } from '@playwright/test';

// 占位 UUID，鉴权拦截在 404 之前，用不存在的 ID 也能验证 403
const FAKE_UUID = '00000000-0000-0000-0000-000000000001';
const OVERRIDE_BODY = {
  newUserId: FAKE_UUID,
  reason: 'RBAC negative assertion',
};

// ─────────────────────────────────────────────────────────
// 共享 token（beforeAll 串行获取）
// ─────────────────────────────────────────────────────────
let tokens: Record<'admin' | 'bizLeader' | 'channelLeader' | 'channelStaff' | 'ops', string> = {
  admin: '',
  bizLeader: '',
  channelLeader: '',
  channelStaff: '',
  ops: '',
};

test.beforeAll(async () => {
  [
    tokens.admin,
    tokens.bizLeader,
    tokens.channelLeader,
    tokens.channelStaff,
    tokens.ops,
  ] = await Promise.all([
    loginApi('admin'),
    loginApi('bizLeader'),
    loginApi('channelLeader'),
    loginApi('channelStaff'),
    loginApi('ops'),
  ]);
  await seedTestData(tokens.admin);
});

// ─────────────────────────────────────────────────────────
// A. 运营权限边界
// ─────────────────────────────────────────────────────────
test.describe('A. 运营（ops_staff）权限边界', () => {
  test('运营访问商品库接口返回 403', async () => {
    await assert403('GET', '/api/products', tokens.ops);
  });

  test('运营访问达人列表接口返回 403', async () => {
    await assert403('GET', '/api/talents', tokens.ops);
  });

  test('运营创建寄样接口返回 403', async () => {
    await assert403('POST', '/api/samples', tokens.ops, {
      productId: FAKE_UUID,
      talentId: 'talent_test_a',
      quantity: 1,
    });
  });

  test('运营访问待审核寄样列表返回 403', async () => {
    // GET /samples?status=PENDING_AUDIT 运营无权
    const BACKEND = (process.env.E2E_BACKEND_URL || 'http://localhost:8080').replace(/\/$/, '');
    const ctx = await playwrightRequest.newContext({ baseURL: BACKEND });
    try {
      const res = await ctx.get('/api/samples', {
        headers: { Authorization: `Bearer ${tokens.ops}` },
        params: { status: 'PENDING_AUDIT' },
      });
      const body = await res.json().catch(() => null) as { code?: number | string } | null;
      expect(res.status() === 403 || Number(body?.code) === 403).toBe(true);
    } finally {
      await ctx.dispose();
    }
  });

  test('运营手动完结寄样返回 403', async () => {
    await assert403('PUT', `/api/samples/${FAKE_UUID}/status`, tokens.ops, {
      action: 'COMPLETED',
    });
  });

  test('运营手动关闭寄样返回 403', async () => {
    await assert403('PUT', `/api/samples/${FAKE_UUID}/status`, tokens.ops, {
      action: 'CLOSED',
      reason: 'RBAC negative assertion',
    });
  });

  test('运营访问独家达人状态接口返回 403', async () => {
    await assert403('GET', '/api/operations/exclusive-talents', tokens.ops);
  });

  test('运营访问数据看板 metrics 接口返回 403', async () => {
    await assert403('GET', '/api/dashboard/metrics', tokens.ops);
  });
});

// ─────────────────────────────────────────────────────────
// B. 渠道专员权限边界
// ─────────────────────────────────────────────────────────
test.describe('B. 渠道专员（channel_staff）权限边界', () => {
  test('渠道专员可访问商品库 API', async () => {
    const body = (await apiGet('/api/products', { token: tokens.channelStaff })) as {
      data?: unknown;
    };
    expect(body?.data).toBeDefined();
  });

  test('渠道专员可访问达人列表 API', async () => {
    const body = (await apiGet('/api/talents', { token: tokens.channelStaff })) as {
      data?: unknown;
    };
    expect(body?.data).toBeDefined();
  });

  test('渠道专员访问商品审核接口返回 403', async () => {
    await assert403(
      'PUT',
      `/api/products/${FAKE_UUID}/audit-result`,
      tokens.channelStaff,
      { approved: true }
    );
  });

  test('渠道专员访问订单手动同步接口返回 403', async () => {
    await assert403('POST', '/api/orders/sync', tokens.channelStaff, {
      startTime: '2026-01-01 00:00:00',
      endTime: '2026-01-01 01:00:00',
    });
  });

  test('渠道专员访问达人归属覆盖接口返回 403', async () => {
    await assert403('POST', `/api/talents/${FAKE_UUID}/override-assignee`, tokens.channelStaff, OVERRIDE_BODY);
  });

  test('渠道专员访问系统用户接口返回 403', async () => {
    await assert403('GET', '/api/users', tokens.channelStaff);
  });
});

// ─────────────────────────────────────────────────────────
// C. 招商组长权限边界
// ─────────────────────────────────────────────────────────
test.describe('C. 招商组长（biz_leader）权限边界', () => {
  test('招商组长商品审核接口返回 403', async () => {
    await assert403(
      'PUT',
      `/api/products/${FAKE_UUID}/audit-result`,
      tokens.bizLeader,
      { approved: true }
    );
  });

  test('招商组长订单手动同步接口返回 403', async () => {
    await assert403('POST', '/api/orders/sync', tokens.bizLeader, {
      startTime: '2026-01-01 00:00:00',
      endTime: '2026-01-01 01:00:00',
    });
  });

  test('招商组长寄样批量审核接口返回 403', async () => {
    await assert403('POST', '/api/samples/batch-approve', tokens.bizLeader, {
      requestNos: ['RBAC_NEGATIVE_ASSERTION'],
    });
  });

  test('招商组长达人归属覆盖接口返回 403', async () => {
    await assert403('POST', `/api/talents/${FAKE_UUID}/override-assignee`, tokens.bizLeader, OVERRIDE_BODY);
  });

  test('招商组长系统配置修改接口返回 403', async () => {
    await assert403('PUT', `/api/configs/${FAKE_UUID}`, tokens.bizLeader, {
      configValue: 'x',
    });
  });
});

// ─────────────────────────────────────────────────────────
// D. 渠道组长权限边界
// ─────────────────────────────────────────────────────────
test.describe('D. 渠道组长（channel_leader）权限边界', () => {
  test('渠道组长可访问寄样导出接口（非 403）', async () => {
    const BACKEND = (process.env.E2E_BACKEND_URL || 'http://localhost:8080').replace(/\/$/, '');
    const ctx = await playwrightRequest.newContext({ baseURL: BACKEND });
    try {
      const res = await ctx.get('/api/samples/exports', {
        headers: { Authorization: `Bearer ${tokens.channelLeader}` },
      });
      // 渠道组长有权导出，不应返回 403
      expect(res.status()).not.toBe(403);
    } finally {
      await ctx.dispose();
    }
  });

  test('渠道组长商品审核接口返回 403', async () => {
    await assert403(
      'PUT',
      `/api/products/${FAKE_UUID}/audit-result`,
      tokens.channelLeader,
      { approved: true }
    );
  });

  test('渠道组长达人归属覆盖接口返回 403', async () => {
    await assert403(
      'POST',
      `/api/talents/${FAKE_UUID}/override-assignee`,
      tokens.channelLeader,
      OVERRIDE_BODY
    );
  });

  test('渠道组长商家归属覆盖接口返回 403', async () => {
    await assert403(
      'POST',
      `/api/merchants/${FAKE_UUID}/override-assignee`,
      tokens.channelLeader,
      OVERRIDE_BODY
    );
  });
});

// ─────────────────────────────────────────────────────────
// E. 未登录/过期 Token 验证
// ─────────────────────────────────────────────────────────
test.describe('E. 未授权访问（无 Token）', () => {
  test('无 Token 访问商品库返回 401', async () => {
    const BACKEND = (process.env.E2E_BACKEND_URL || 'http://localhost:8080').replace(/\/$/, '');
    const ctx = await playwrightRequest.newContext({ baseURL: BACKEND });
    try {
      const res = await ctx.get('/api/products');
      expect(res.status()).toBe(401);
    } finally {
      await ctx.dispose();
    }
  });

  test('无 Token 访问寄样列表返回 401', async () => {
    const BACKEND = (process.env.E2E_BACKEND_URL || 'http://localhost:8080').replace(/\/$/, '');
    const ctx = await playwrightRequest.newContext({ baseURL: BACKEND });
    try {
      const res = await ctx.get('/api/samples');
      expect(res.status()).toBe(401);
    } finally {
      await ctx.dispose();
    }
  });

  test('无 Token 访问 Dashboard summary 返回 401', async () => {
    const BACKEND = (process.env.E2E_BACKEND_URL || 'http://localhost:8080').replace(/\/$/, '');
    const ctx = await playwrightRequest.newContext({ baseURL: BACKEND });
    try {
      const res = await ctx.get('/api/dashboard/summary');
      expect(res.status()).toBe(401);
    } finally {
      await ctx.dispose();
    }
  });
});

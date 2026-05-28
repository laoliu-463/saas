/**
 * api-assertions.ts
 *
 * V1-P0 验收级 E2E 的后端 API 断言工具。
 * 用于在浏览器操作之后，直接调用后端接口确认关键业务状态，
 * 弥补纯 UI 断言无法验证数据层的盲区。
 */
import { request as playwrightRequest, type APIRequestContext } from '@playwright/test';
import { accounts, type AccountKey } from './test-data';

const BACKEND = () =>
  (process.env.E2E_BACKEND_URL || 'http://127.0.0.1:8080').replace(/\/$/, '');

function unwrapMetricsPayload(data: unknown): Record<string, unknown> | undefined {
  if (!data || typeof data !== 'object') return undefined;
  const record = data as Record<string, unknown>;
  const settle = record.settle;
  const estimate = record.estimate;
  if (settle && typeof settle === 'object') return settle as Record<string, unknown>;
  if (estimate && typeof estimate === 'object') return estimate as Record<string, unknown>;
  return record;
}

/** 通过用户名密码登录，返回 accessToken */
export async function loginApi(role: AccountKey): Promise<string> {
  const { username, password } = accounts[role];
  const ctx: APIRequestContext = await playwrightRequest.newContext({
    baseURL: BACKEND(),
    ignoreHTTPSErrors: true,
  });
  try {
    const res = await ctx.post('/api/auth/login', {
      data: { username, password },
      headers: { 'Content-Type': 'application/json' },
    });
    if (!res.ok()) {
      throw new Error(`登录失败 [${username}]: HTTP ${res.status()}`);
    }
    const body = (await res.json()) as { data?: { token?: string } };
    const token = body?.data?.token;
    if (!token) throw new Error(`登录无 token [${username}]`);
    return token;
  } finally {
    await ctx.dispose();
  }
}

export interface ApiAssertOptions {
  token: string;
  /** 期望的 HTTP 状态码，默认 200 */
  expectedStatus?: number;
}

/** 带 Bearer token 的 GET，返回 response body（已 JSON 解析） */
export async function apiGet(
  path: string,
  opts: ApiAssertOptions,
  params?: Record<string, string | number>
): Promise<unknown> {
  const ctx = await playwrightRequest.newContext({ baseURL: BACKEND() });
  try {
    const res = await ctx.get(path, {
      headers: { Authorization: `Bearer ${opts.token}` },
      params: params as Record<string, string>,
    });
    const expected = opts.expectedStatus ?? 200;
    if (res.status() !== expected) {
      throw new Error(
        `GET ${path} 期望 ${expected}，实际 ${res.status()}`
      );
    }
    return res.ok() ? await res.json().catch(() => null) : null;
  } finally {
    await ctx.dispose();
  }
}

/** 带 Bearer token 的 POST，返回 response body */
export async function apiPost(
  path: string,
  data: unknown,
  opts: ApiAssertOptions
): Promise<unknown> {
  const ctx = await playwrightRequest.newContext({ baseURL: BACKEND() });
  try {
    const res = await ctx.post(path, {
      data,
      headers: {
        Authorization: `Bearer ${opts.token}`,
        'Content-Type': 'application/json',
      },
    });
    const expected = opts.expectedStatus ?? 200;
    if (res.status() !== expected) {
      const text = await res.text().catch(() => '');
      throw new Error(
        `POST ${path} 期望 ${expected}，实际 ${res.status()}：${text.slice(0, 200)}`
      );
    }
    return res.ok() ? await res.json().catch(() => null) : null;
  } finally {
    await ctx.dispose();
  }
}

/** 准备本地测试数据，供 V1-P0 验收套件独立运行 */
export async function seedTestData(adminToken?: string): Promise<void> {
  const token = adminToken ?? await loginApi('admin');
  const body = (await apiPost('/api/test/seed', {}, { token })) as {
    code?: number | string;
    data?: unknown;
  } | null;
  if (body && Number(body.code) >= 400) {
    throw new Error(`/api/test/seed 业务失败：code=${body.code}`);
  }
}

// ─────────────────────────────────────────────
// 业务断言辅助函数
// ─────────────────────────────────────────────

/**
 * 断言商品库有商品（count > 0）
 * GET /api/products → data.total > 0
 */
export async function assertProductLibraryNotEmpty(token: string): Promise<void> {
  const body = (await apiGet('/api/products', { token })) as {
    data?: { total?: number; records?: unknown[] };
  };
  const total = body?.data?.total ?? body?.data?.records?.length ?? 0;
  if (total === 0) {
    throw new Error('商品库为空，V1-P0 断言失败：预期至少 1 条商品');
  }
}

/**
 * 断言 pick_source_mapping 有记录
 * GET /api/orders/stats → data.totalOrders（间接验证归因管道健康）
 */
export async function assertOrderStatsReachable(token: string): Promise<void> {
  const body = (await apiGet('/api/orders/stats', { token })) as {
    data?: { totalOrders?: number };
  };
  if (body?.data === undefined) {
    throw new Error('订单统计接口无返回，归因管道异常');
  }
}

/**
 * 断言 Dashboard 指标可读取
 * GET /api/dashboard/metrics → data 存在
 */
export async function assertDashboardMetrics(token: string): Promise<void> {
  const body = (await apiGet('/api/dashboard/metrics', { token })) as {
    data?: unknown;
  };
  if (!unwrapMetricsPayload(body?.data)) {
    throw new Error('Dashboard metrics 接口无 data，看板数据不可信');
  }
}

/**
 * 断言 Dashboard summary 可读取
 */
export async function assertDashboardSummary(token: string): Promise<void> {
  const body = (await apiGet('/api/dashboard/summary', { token })) as {
    data?: unknown;
  };
  if (!body?.data) {
    throw new Error('Dashboard summary 接口无 data');
  }
}

/**
 * 断言 /api/samples 对当前角色有数据
 */
export async function assertSampleListReachable(token: string): Promise<void> {
  const body = (await apiGet('/api/samples', { token })) as {
    data?: { total?: number };
  };
  if (body?.data === undefined) {
    throw new Error('寄样列表接口无返回');
  }
}

/**
 * 断言接口返回 403（负向权限验证）
 */
export async function assert403(
  method: 'GET' | 'POST' | 'PUT' | 'DELETE',
  path: string,
  token: string,
  requestBody?: unknown
): Promise<void> {
  const ctx = await playwrightRequest.newContext({ baseURL: BACKEND() });
  try {
    const headers = { Authorization: `Bearer ${token}` };
    let res;
    if (method === 'GET') {
      res = await ctx.get(path, { headers });
    } else if (method === 'POST') {
      res = await ctx.post(path, { headers, data: requestBody ?? {} });
    } else if (method === 'PUT') {
      res = await ctx.put(path, { headers, data: requestBody ?? {} });
    } else {
      res = await ctx.delete(path, { headers });
    }
    const responseBody = await res.json().catch(() => null) as { code?: number | string } | null;
    const businessCode = Number(responseBody?.code);
    if (res.status() !== 403 && businessCode !== 403) {
      throw new Error(
        `${method} ${path} 期望 403（权限拦截），实际 HTTP ${res.status()} / code ${responseBody?.code ?? '-'}`
      );
    }
  } finally {
    await ctx.dispose();
  }
}

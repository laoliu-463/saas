import type { APIRequestContext } from '@playwright/test';
import { request as playwrightRequest } from '@playwright/test';

/** 后端 REST API 前缀，例如 http://localhost:8080/api */
export function getBackendApiBase(): string {
  const backend = (process.env.E2E_BACKEND_URL || 'http://localhost:8080').replace(/\/$/, '');
  return `${backend}/api`;
}

interface ProbeResult {
  status: number;
  ok: boolean;
  error?: string;
  skipped?: boolean;
  reason?: string;
  data?: unknown;
}

export interface BackendProbeResults {
  token?: ProbeResult;
  institution?: ProbeResult;
  activities?: ProbeResult;
  activityProducts?: ProbeResult;
  orderSync?: ProbeResult;
  dashboard?: ProbeResult;
  skuProbe?: ProbeResult;
}

export async function apiLogin(apiBase: string, username: string, password: string): Promise<Record<string, unknown>> {
  const ctx: APIRequestContext = await playwrightRequest.newContext({
    baseURL: apiBase.replace(/\/api\/?$/, ''),
    ignoreHTTPSErrors: true
  });
  try {
    const res = await ctx.post('/api/auth/login', {
      data: { username, password },
      headers: { 'Content-Type': 'application/json' }
    });
    const body = (await res.json()) as { data?: Record<string, unknown>; msg?: string };
    const data = body?.data ?? {};
    if (!res.ok() || !data?.token) {
      throw new Error(`管理员登录失败: HTTP ${res.status()} ${body?.msg ?? ''}`);
    }
    return data;
  } finally {
    await ctx.dispose();
  }
}

/** 从活动列表响应中提取第一个活动的 ID */
function extractActivityId(body: unknown): string | null {
  if (!body || typeof body !== 'object') return null;
  const root = body as Record<string, unknown>;
  // 兼容 { data: [...] } 和 { data: { data: [...] } } 两种结构
  const list = Array.isArray(root.data) ? root.data : Array.isArray((root.data as Record<string, unknown>)?.data)
    ? (root.data as Record<string, unknown>).data
    : null;
  if (!Array.isArray(list) || list.length === 0) return null;
  const first = list[0] as Record<string, unknown>;
  return String(first.activityId ?? first.id ?? '');
}

function formatLocalDateTime(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

/**
 * 与 legacy runtime/qa 脚本一致的后端直探测针（不依赖浏览器）。
 *
 * Playwright request context 中以 "/" 开头的 path 会替换 baseURL 的路径部分，
 * 因此 baseURL 只保留 host，所有路径统一用 /api/… 前缀。
 */
export async function verifyBackendApis(token: string): Promise<BackendProbeResults> {
  const backend = (process.env.E2E_BACKEND_URL || 'http://localhost:8080').replace(/\/$/, '');
  const ctx = await playwrightRequest.newContext({ baseURL: backend, ignoreHTTPSErrors: true });
  const headers = { Authorization: `Bearer ${token}` };
  const results: BackendProbeResults = {};

  try {
    try {
      const r = await ctx.get('/api/douyin/tokens', { headers });
      const data = await r.json().catch(() => undefined);
      const status = (data as { data?: Record<string, unknown> } | undefined)?.data ?? {};
      const tokenUsable =
        status.hasAccessToken === true &&
        status.hasRefreshToken === true &&
        status.reauthorizeRequired !== true;
      results.token = {
        status: r.status(),
        ok: r.ok() && tokenUsable,
        reason: tokenUsable
          ? undefined
          : 'REAL_PRE_TOKEN_PRECONDITION_BLOCKED: missing access_token/refresh_token or reauthorization required',
        data: {
          appId: status.appId,
          hasAccessToken: status.hasAccessToken === true,
          hasRefreshToken: status.hasRefreshToken === true,
          tokenExpiringSoon: status.tokenExpiringSoon === true,
          reauthorizeRequired: status.reauthorizeRequired === true,
        },
      };
    } catch (e) {
      results.token = { status: 0, ok: false, error: e instanceof Error ? e.message : String(e) };
    }

    if (!results.token?.ok) {
      const reason = results.token?.reason || `Token 不可用，跳过真实上游副作用探测: token=${results.token?.status ?? 'N/A'}`;
      results.institution = { status: 0, ok: false, skipped: true, reason };
      results.activities = { status: 0, ok: false, skipped: true, reason };
      results.activityProducts = { status: 0, ok: false, skipped: true, reason };
      results.orderSync = { status: 0, ok: false, skipped: true, reason };
      results.skuProbe = { status: 0, ok: false, skipped: true, reason };
      return results;
    }

    try {
      const r = await ctx.get('/api/douyin/institution-info', { headers });
      const data = await r.json().catch(() => undefined);
      results.institution = { status: r.status(), ok: r.ok(), data };
    } catch (e) {
      results.institution = { status: 0, ok: false, error: e instanceof Error ? e.message : String(e) };
    }

    if (!results.institution?.ok) {
      const reason = `授权主体不可用，跳过真实上游副作用探测: institution=${results.institution?.status ?? 'N/A'}`;
      results.activities = { status: 0, ok: false, skipped: true, reason };
      results.activityProducts = { status: 0, ok: false, skipped: true, reason };
      results.orderSync = { status: 0, ok: false, skipped: true, reason };
      results.skuProbe = { status: 0, ok: false, skipped: true, reason };
      return results;
    }

    let activitiesBody: unknown;
    try {
      const r = await ctx.get('/api/douyin/activities', { headers });
      results.activities = { status: r.status(), ok: r.ok() };
      activitiesBody = await r.json().catch(() => undefined);
    } catch (e) {
      results.activities = { status: 0, ok: false, error: e instanceof Error ? e.message : String(e) };
    }

    // 动态取活动 ID：优先列表首条，其次环境变量，否则跳过
    const dynamicActivityId =
      extractActivityId(activitiesBody) ||
      process.env.E2E_ACTIVITY_ID ||
      null;
    if (!dynamicActivityId) {
      results.activityProducts = { status: 0, ok: false, skipped: true, reason: '无可用活动 ID（活动列表为空或接口未返回）' };
    } else {
      try {
        const r = await ctx.get('/api/douyin/activity-product-list', {
          headers,
          params: { activityId: String(dynamicActivityId), count: 20 }
        });
        results.activityProducts = { status: r.status(), ok: r.ok() };
      } catch (e) {
        results.activityProducts = { status: 0, ok: false, error: e instanceof Error ? e.message : String(e) };
      }
    }

    try {
      const end = new Date();
      const start = new Date(end.getTime() - 7 * 24 * 60 * 60 * 1000);
      const r = await ctx.post('/api/orders/sync', {
        headers,
        data: {
          startTime: formatLocalDateTime(start),
          endTime: formatLocalDateTime(end)
        }
      });
      results.orderSync = { status: r.status(), ok: r.ok() };
    } catch (e) {
      results.orderSync = { status: 0, ok: false, error: e instanceof Error ? e.message : String(e) };
    }

    try {
      const r = await ctx.get('/api/dashboard/metrics', { headers });
      results.dashboard = { status: r.status(), ok: r.ok() };
    } catch (e) {
      results.dashboard = { status: 0, ok: false, error: e instanceof Error ? e.message : String(e) };
    }

    try {
      const r = await ctx.post('/api/douyin/promotion-link-probes/raw', {
        headers,
        data: { method: 'buyin.productSkus.v2', product_id: process.env.E2E_PRODUCT_ID || '3810562766247428542' }
      });
      const data = await r.json().catch(() => undefined);
      results.skuProbe = { status: r.status(), ok: r.ok(), data };
    } catch (e) {
      results.skuProbe = { status: 0, ok: false, error: e instanceof Error ? e.message : String(e) };
    }
  } finally {
    await ctx.dispose();
  }

  return results;
}

export function skuProbeAccept(probe: BackendProbeResults['skuProbe']): boolean {
  if (!probe || probe.error || probe.skipped || !probe.ok) return false;
  const root = probe.data as Record<string, unknown> | undefined;
  const data = root?.data as Record<string, unknown> | undefined;
  const remote = data?.remoteResponse as Record<string, unknown> | undefined;
  const remoteData = remote?.data as Record<string, unknown> | undefined;
  const code = remote?.code ?? data?.errorCode ?? data?.code ?? root?.code;
  const skus = remoteData?.skus;
  const skuCount = Array.isArray(skus)
    ? skus.length
    : skus && typeof skus === 'object'
      ? Object.keys(skus).length
      : 0;
  const success = ['10000', '0', '200'].includes(String(code)) || data?.status === 'success';
  return success && skuCount > 0;
}

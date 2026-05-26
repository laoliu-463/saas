/**
 * real-pre P0 / 32 / 订单同步 + 归因字段
 *
 * 复用 runtime/qa/real-pre-safe-upstream.cjs，不真实创建新转链。
 * 缺真实订单样本时输出 PENDING；缺 mapping 时输出 BLOCKED。
 */
import { test, expect, request as playwrightRequest, type APIRequestContext } from '@playwright/test';
import { accounts } from './helpers/test-data';
import { apiLogin } from './helpers/real-pre-api';
import {
  createRealPreP0Step,
  ensureRealPreP0Env,
  markBlocked,
  markFail,
  markPending,
  persistStepSummary,
  setDetail,
  shouldRunRealPreP0,
  safeUnwrap,
  formatLocalDateTime
} from './helpers/real-pre-p0-step';

type JsonMap = Record<string, unknown>;
type ReusablePromotionMapping = {
  mappingId?: string;
  pickSource?: string;
  productId?: string;
  activityId?: string;
  userId?: string;
  promotionLinkId?: string;
  promotionUrl?: string;
  shortUrl?: string;
  createTime?: string;
};

const safeUpstream = require('../../runtime/qa/real-pre-safe-upstream.cjs') as {
  queryReusablePromotionMapping: (options: JsonMap) => ReusablePromotionMapping[];
  selectReusablePromotionMapping: (rows: ReusablePromotionMapping[]) => ReusablePromotionMapping;
  buildPromotionBlockerMessage: (options: JsonMap) => string;
};

const SYNC_WINDOW_MINUTES = Number(process.env.E2E_ORDER_SYNC_WINDOW_MINUTES || 30);

test('real-pre P0 / 32 / 订单同步 + 归因', async ({}, testInfo) => {
  test.skip(!shouldRunRealPreP0('32-real-pre-order-attribution'), 'Run via npm run e2e:real-pre:p0 or set E2E_REAL_PRE_P0=true');
  test.setTimeout(15 * 60_000);
  ensureRealPreP0Env();

  const ctx = createRealPreP0Step('32-real-pre-order-attribution', 'real-pre-p0/32/order-attribution');
  const backend = (process.env.E2E_BACKEND_URL || 'http://localhost:8081').replace(/\/$/, '');
  const api = await playwrightRequest.newContext({ baseURL: backend, ignoreHTTPSErrors: true });

  try {
    const admin = await apiLogin(`${backend}/api`, accounts.admin.username, accounts.admin.password);

    // Step 1: 找一条可复用的 pick_source_mapping。
    // 不传入 productId / activityId / userId 时安全上游 helper 会抛错，因此先扫描所有可复用映射。
    let reusableMapping: ReusablePromotionMapping | null = null;
    try {
      const rows = scanAnyReusableMapping();
      reusableMapping = rows.length > 0 ? rows[0] : null;
      setDetail(ctx, 'reusableMapping', reusableMapping ? {
        mappingId: reusableMapping.mappingId,
        pickSource: reusableMapping.pickSource,
        activityId: reusableMapping.activityId,
        productId: reusableMapping.productId,
        userId: reusableMapping.userId
      } : null);
      if (!reusableMapping) {
        markBlocked(ctx, 'BLOCKED_NO_REUSABLE_PROMOTION_MAPPING: 当前 real-pre 库找不到任何可复用 pick_source_mapping');
      }
    } catch (error) {
      markFail(ctx, `查询 pick_source_mapping 失败：${error instanceof Error ? error.message : String(error)}`);
    }

    // Step 2: 订单同步。
    const end = new Date();
    const start = new Date(end.getTime() - SYNC_WINDOW_MINUTES * 60 * 1000);
    const syncResult = await rawApi(api, 'POST', '/api/orders/sync', String(admin.token || ''), {
      data: { startTime: formatLocalDateTime(start), endTime: formatLocalDateTime(end) }
    });
    if (!syncResult.ok) {
      markFail(ctx, `POST /api/orders/sync HTTP ${syncResult.status}`);
    }
    const syncData = safeUnwrap<JsonMap>(syncResult.body) || {};
    const failed = Number(syncData.failed ?? syncData.failedCount ?? 0);
    const totalFetched = Number(syncData.totalFetched ?? syncData.fetched ?? syncData.total ?? 0);
    const created = Number(syncData.created ?? 0);
    const updated = Number(syncData.updated ?? 0);
    const attributed = Number(syncData.attributed ?? 0);
    const unattributed = Number(syncData.unattributed ?? 0);
    setDetail(ctx, 'orderSync', {
      windowMinutes: SYNC_WINDOW_MINUTES,
      startTime: formatLocalDateTime(start),
      endTime: formatLocalDateTime(end),
      totalFetched,
      created,
      updated,
      attributed,
      unattributed,
      failed
    });
    if (failed > 0) {
      markFail(ctx, `订单同步 failed=${failed}，无法解释`);
    }
    if (totalFetched === 0 && ctx.summary.conclusion === 'PASS') {
      markPending(ctx, 'PENDING_NO_UPSTREAM_ORDERS: 当前窗口上游无订单返回');
    }

    // Step 3: 订单列表 + 字段抽样。
    const listResult = await rawApi(api, 'GET', '/api/orders', String(admin.token || ''), {
      params: { page: 1, size: 10 }
    });
    const listBody = listResult.body as JsonMap | undefined;
    if (!listResult.ok) {
      markFail(ctx, `GET /api/orders HTTP ${listResult.status}`);
    } else if (listBody?.code !== undefined && Number(listBody?.code) !== 200) {
      markFail(ctx, `GET /api/orders business code=${listBody?.code}`);
    }
    const listData = safeUnwrap<JsonMap>(listBody) || {};
    const records = extractRecords(listData);
    setDetail(ctx, 'orderList', {
      status: listResult.status,
      total: Number(listData.total ?? records.length ?? 0),
      sampleCount: records.length
    });

    const attributionStats = {
      ATTRIBUTED: 0,
      PICK_SOURCE_EMPTY: 0,
      MAPPING_NOT_FOUND: 0,
      NATIVE_MAPPING_MISSING: 0,
      UPSTREAM_PRODUCT_UNCOVERED: 0,
      CANNOT_AUTO_ATTRIBUTION: 0,
      UNSAFE_BECAUSE_CREATED_AFTER_ORDER: 0,
      UNKNOWN: 0
    } as Record<string, number>;
    const fieldGaps: string[] = [];

    for (const order of records) {
      const orderId = String(order.orderId ?? order.order_id ?? '');
      const productId = String(order.productId ?? order.product_id ?? '');
      const activityId = String(order.activityId ?? order.activity_id ?? '');
      const pickSource = String(order.pickSource ?? order.pick_source ?? '');
      const payAmount = order.payAmount ?? order.pay_amount;
      const settleAmount = order.settleAmount ?? order.settle_amount;
      const estimateServiceFee = order.estimateServiceFee ?? order.estimate_service_fee;
      const effectiveServiceFee = order.effectiveServiceFee ?? order.effective_service_fee;
      const payTime = order.payTime ?? order.pay_time;

      const missing: string[] = [];
      if (!orderId) missing.push('orderId');
      if (!productId) missing.push('productId');
      if (!activityId) missing.push('activityId');
      if (payAmount === undefined) missing.push('payAmount');
      if (settleAmount === undefined) missing.push('settleAmount');
      if (estimateServiceFee === undefined) missing.push('estimateServiceFee');
      if (effectiveServiceFee === undefined) missing.push('effectiveServiceFee');
      if (payTime === undefined) missing.push('payTime');
      if (missing.length) fieldGaps.push(`order=${orderId || '?'} missing=${missing.join(',')}`);

      const reason = inferAttributionBucket(order, pickSource);
      attributionStats[reason] = (attributionStats[reason] || 0) + 1;
    }

    setDetail(ctx, 'attributionStats', attributionStats);
    setDetail(ctx, 'fieldGaps', fieldGaps);

    if (records.length === 0) {
      if (ctx.summary.conclusion === 'PASS') {
        markPending(ctx, 'PENDING_NO_UPSTREAM_ORDERS: 订单列表为空，无法验证归因字段');
      }
    } else if (fieldGaps.length && ctx.summary.conclusion === 'PASS') {
      markFail(ctx, `订单字段不完整：${fieldGaps.slice(0, 5).join(' | ')}`);
    } else if (attributionStats.ATTRIBUTED === 0 && ctx.summary.conclusion === 'PASS') {
      markPending(ctx, 'PENDING_NO_UPSTREAM_ORDERS: 当前真实订单全部未归因，无法证明归因链');
    }
  } catch (error) {
    markFail(ctx, error instanceof Error ? error.message : String(error));
  } finally {
    persistStepSummary(ctx);
    await api.dispose();
    await testInfo.attach('step-summary.json', {
      body: JSON.stringify(ctx.summary, null, 2),
      contentType: 'application/json'
    });
  }

  expect(
    ctx.summary.conclusion === 'FAIL'
      ? ctx.summary.failures.join('; ')
      : 'OK',
    `real-pre 32 订单归因 conclusion=${ctx.summary.conclusion}`
  ).toBe('OK');
});

function scanAnyReusableMapping(): ReusablePromotionMapping[] {
  // 安全上游 helper 强制需要 activityId/productId/userId；这里不指定具体业务样本，
  // 复用同样的 SQL 但放宽过滤，仅做存在性检查。
  const { execFileSync } = require('node:child_process');
  const container = process.env.E2E_DB_CONTAINER || 'saas-active-postgres-real-pre-1';
  const user = process.env.E2E_DB_USER || 'saas';
  const db = process.env.E2E_DB_NAME || 'saas_real_pre';
  const sql = [
    "select psm.id::text, psm.pick_source, psm.product_id, psm.activity_id, psm.user_id::text,",
    "  coalesce(psm.promotion_link_id::text, ''), coalesce(pl.promotion_url, psm.converted_url, ''),",
    "  coalesce(pl.short_url, ''), psm.create_time::text",
    'from pick_source_mapping psm',
    'left join promotion_link pl on pl.id = psm.promotion_link_id and pl.deleted = 0',
    'where psm.deleted = 0',
    '  and psm.status = 1',
    "  and coalesce(psm.pick_source, '') <> ''",
    'order by psm.update_time desc nulls last, psm.create_time desc',
    'limit 5;'
  ].join('\n');
  try {
    const out = execFileSync(
      'docker',
      ['exec', container, 'psql', '-X', '-q', '-v', 'ON_ERROR_STOP=1', '-U', user, '-d', db, '-t', '-A', '-F', '|', '-c', sql],
      { encoding: 'utf8' }
    );
    return safeUpstream.parsePipeRows
      ? (safeUpstream as unknown as { parsePipeRows: (rows: string) => ReusablePromotionMapping[] }).parsePipeRows(out)
      : String(out || '').split(/\r?\n/).filter(Boolean).map((line) => {
          const parts = line.split('|');
          return {
            mappingId: parts[0] || '',
            pickSource: parts[1] || '',
            productId: parts[2] || '',
            activityId: parts[3] || '',
            userId: parts[4] || '',
            promotionLinkId: parts[5] || '',
            promotionUrl: parts[6] || '',
            shortUrl: parts[7] || '',
            createTime: parts[8] || ''
          };
        });
  } catch (error) {
    throw new Error(`scanAnyReusableMapping 失败: ${error instanceof Error ? error.message : String(error)}`);
  }
}

function inferAttributionBucket(order: JsonMap, pickSource: string): string {
  if (order.userId || order.user_id) return 'ATTRIBUTED';
  if (!pickSource) return 'PICK_SOURCE_EMPTY';
  const reason = String((order as { attributionReason?: string }).attributionReason || (order as { attribution_reason?: string }).attribution_reason || '').toUpperCase();
  if (reason.includes('UPSTREAM_PRODUCT_UNCOVERED')) return 'UPSTREAM_PRODUCT_UNCOVERED';
  if (reason.includes('UNSAFE_BECAUSE_CREATED_AFTER_ORDER') || reason.includes('CREATED_AFTER_ORDER')) return 'UNSAFE_BECAUSE_CREATED_AFTER_ORDER';
  if (reason.includes('NATIVE_MAPPING_MISSING')) return 'NATIVE_MAPPING_MISSING';
  if (reason.includes('MAPPING_NOT_FOUND') || reason.includes('NO_MAPPING')) return 'MAPPING_NOT_FOUND';
  if (reason.includes('CANNOT_AUTO_ATTRIBUTION')) return 'CANNOT_AUTO_ATTRIBUTION';
  if (pickSource && !order.userId && !order.user_id) return 'MAPPING_NOT_FOUND';
  return 'UNKNOWN';
}

function extractRecords(data: unknown): JsonMap[] {
  if (Array.isArray(data)) return data as JsonMap[];
  if (!data || typeof data !== 'object') return [];
  for (const key of ['records', 'items', 'list', 'rows', 'content']) {
    const value = (data as JsonMap)[key];
    if (Array.isArray(value)) return value as JsonMap[];
  }
  return [];
}

async function rawApi(
  api: APIRequestContext,
  method: 'GET' | 'POST' | 'PUT',
  path: string,
  token: string,
  options: { data?: JsonMap; params?: JsonMap } = {}
): Promise<{ status: number; ok: boolean; body: unknown }> {
  const headers = token ? { Authorization: `Bearer ${token}` } : undefined;
  const requestOptions = {
    headers,
    data: options.data,
    params: options.params as Record<string, string | number | boolean> | undefined
  };
  const response = method === 'GET'
    ? await api.get(path, requestOptions)
    : method === 'POST'
      ? await api.post(path, requestOptions)
      : await api.put(path, requestOptions);
  const body = await response.json().catch(async () => ({ rawText: await response.text().catch(() => '') }));
  return { status: response.status(), ok: response.ok(), body };
}

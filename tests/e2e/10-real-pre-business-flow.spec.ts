import { expect, request as playwrightRequest, test, type APIRequestContext, type Page } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { accounts } from './helpers/test-data';
import { apiLogin } from './helpers/real-pre-api';
import { installAuth } from './helpers/auth';
import { gotoApp } from './helpers/page-ready';

type AuthPayload = Record<string, unknown>;
type JsonMap = Record<string, unknown>;
type ReusablePromotionMapping = {
  mappingId?: string;
  pickSource?: string;
  promotionLinkId?: string;
  promotionUrl?: string;
  shortUrl?: string;
};

const {
  queryReusablePromotionMapping,
  selectReusablePromotionMapping,
  buildPromotionBlockerMessage
} = require('../../runtime/qa/real-pre-safe-upstream.cjs') as {
  queryReusablePromotionMapping: (options: JsonMap) => ReusablePromotionMapping[];
  selectReusablePromotionMapping: (rows: ReusablePromotionMapping[]) => ReusablePromotionMapping;
  buildPromotionBlockerMessage: (options: JsonMap) => string;
};

const DEFAULT_ACTIVITY_ID = '3916506';
const DEFAULT_PRODUCT_ID = '3810562766247428542';
const BUSINESS_READY = new Set(['APPROVED', 'BOUND', 'ASSIGNED', 'LINKED', 'FOLLOWING']);
const REAL_PRE_NAV_TIMEOUT_MS = Number(process.env.E2E_REAL_PRE_NAV_TIMEOUT_MS || 120_000);
const SHOULD_RUN =
  process.env.E2E_REAL_PRE_BUSINESS === 'true' ||
  process.env.npm_lifecycle_event === 'e2e:real-pre:business' ||
  process.env.npm_lifecycle_event === 'e2e:real-pre:visual' ||
  process.argv.some((arg) => arg.includes('10-real-pre-business-flow'));

test('real-pre business flow reuses promotion mapping and keeps dashboard readable', async ({ page }, testInfo) => {
  test.skip(!SHOULD_RUN, 'Run with npm run e2e:real-pre:business, npm run e2e:real-pre:visual, or set E2E_REAL_PRE_BUSINESS=true.');
  test.setTimeout(600_000);

  const backend = (process.env.E2E_BACKEND_URL || 'http://localhost:8080').replace(/\/$/, '');
  const api = await playwrightRequest.newContext({ baseURL: backend, ignoreHTTPSErrors: true });
  const admin = await apiLogin(`${backend}/api`, accounts.admin.username, accounts.admin.password);
  const bizLeader = await apiLogin(`${backend}/api`, accounts.bizLeader.username, accounts.bizLeader.password);
  const bizStaff = await apiLogin(`${backend}/api`, 'biz_staff', accounts.bizLeader.password);
  const channelStaff = await apiLogin(`${backend}/api`, accounts.channelStaff.username, accounts.channelStaff.password);
  const summary: JsonMap = {
    evidenceType: 'real-pre-business-e2e',
    runId: process.env.QA_RUN_ID || `QA${formatRunStamp(new Date())}`,
    safeUpstreamMode: 'REUSE_EXISTING_PROMOTION_MAPPING',
    databaseCleared: false,
    cleanupStatus: 'PLAN_REQUIRED_BEFORE_COMPLETION',
    secretsWrittenToDocsOrGit: false
  };

  try {
    const tokenStatus = await apiJson(api, 'GET', '/api/douyin/tokens', admin);
    expect(tokenStatus.hasAccessToken, 'token has access token').toBe(true);
    expect(tokenStatus.hasRefreshToken, 'token has refresh token').toBe(true);
    expect(tokenStatus.reauthorizeRequired, 'token does not require reauthorization').toBe(false);
    summary.token = {
      hasAccessToken: tokenStatus.hasAccessToken,
      hasRefreshToken: tokenStatus.hasRefreshToken,
      reauthorizeRequired: tokenStatus.reauthorizeRequired
    };

    const activities = await apiJson(api, 'GET', '/api/douyin/activities', admin);
    expect(isUpstreamSuccess(activities), 'activity list upstream success').toBe(true);
    const remoteActivityId = findActivityId(activities);
    const activityId = process.env.E2E_ACTIVITY_ID || DEFAULT_ACTIVITY_ID;
    summary.activity = {
      activityId,
      remoteActivityId,
      status: activities.status,
      remoteCode: findDeepValue(activities, ['code', 'err_no'])
    };

    const rawProducts = await apiJson(api, 'GET', '/api/douyin/activity-product-list', admin, {
      params: { activityId, count: 20 }
    });
    expect(isUpstreamSuccess(rawProducts), 'raw activity product upstream success').toBe(true);
    const rawProductCount = findProductArray(rawProducts.remoteResponse).length;

    const businessProducts = await apiJson(api, 'GET', `/api/colonel/activities/${activityId}/products`, admin, {
      params: { count: 20, refresh: true }
    });
    const businessItems = Array.isArray(businessProducts.items) ? businessProducts.items as JsonMap[] : findProductArray(businessProducts);
    expect(businessItems.length, 'business product list has at least one product').toBeGreaterThan(0);
    const candidate = selectCandidate(businessItems);
    const productId = String(candidate.productId ?? candidate.product_id);
    summary.activityId = activityId;
    summary.selectedProductId = productId;
    summary.productOperationStateSnapshots = [
      readProductOperationStateSnapshot(activityId, productId)
    ];
    const productOperationLogIdsBefore = readProductOperationLogIds(activityId, productId);
    summary.products = {
      rawProductCount,
      businessProductCount: businessItems.length,
      productId,
      initialBizStatus: candidate.bizStatus ?? null
    };

    const skuProbe = await apiJson(api, 'POST', '/api/douyin/promotion-link-probes/raw', admin, {
      data: { method: 'buyin.productSkus.v2', product_id: productId }
    });
    const skuCount = findSkuCount(skuProbe);
    expect(isUpstreamSuccess(skuProbe), 'SKU probe upstream success').toBe(true);
    expect(skuCount, 'SKU probe returns SKU rows').toBeGreaterThan(0);
    summary.sku = { skuCount };

    let detail = await apiJson(api, 'GET', `/api/colonel/activities/${activityId}/products/${productId}`, admin);
    expect(isLinkableContext(detail), 'product has promotion-link context or existing linked state').toBe(true);
    const beforeStatus = String(detail.bizStatus || 'PENDING_AUDIT');
    if (beforeStatus === 'PENDING_AUDIT') {
      detail = await apiJson(api, 'PUT', `/api/colonel/activities/${activityId}/products/${productId}/audit-result`, bizStaff, {
        data: buildAuditPayload()
      });
      expect(detail.selectedToLibrary || detail.libraryVisible, 'audited product enters local library').toBeTruthy();
      expect(BUSINESS_READY.has(String(detail.bizStatus)), 'audited product reaches business-ready state').toBe(true);
    }

    const afterAuditStatus = String(detail.bizStatus || beforeStatus);
    if (afterAuditStatus !== 'LINKED' && afterAuditStatus !== 'FOLLOWING') {
      detail = await apiJson(api, 'PUT', `/api/colonel/activities/${activityId}/products/${productId}/assignee`, bizLeader, {
        data: { assigneeId: String(bizStaff.userId) }
      });
      expect(String(detail.bizStatus), 'assigned product status').toBe('ASSIGNED');
    }
    summary.localState = {
      beforeStatus,
      afterAuditOrAssignStatus: detail.bizStatus,
      selectedToLibrary: Boolean(detail.selectedToLibrary || detail.libraryVisible)
    };
    summary.productOperationLogIds = diffNewIds(productOperationLogIdsBefore, readProductOperationLogIds(activityId, productId));

    const reusableMappings = queryReusablePromotionMapping({
      activityId,
      productId,
      userId: String(channelStaff.userId)
    });
    let reusableMapping: ReusablePromotionMapping;
    try {
      reusableMapping = selectReusablePromotionMapping(reusableMappings);
    } catch {
      throw new Error(buildPromotionBlockerMessage({ activityId, productId, userId: String(channelStaff.userId) }));
    }
    const pickSource = String(reusableMapping.pickSource || '');
    expect(pickSource, 'reusable promotion mapping returns pick_source').toMatch(/^v\./);

    const mappingCount = reusableMappings.length;
    expect(mappingCount, 'reusable pick_source_mapping row exists').toBeGreaterThan(0);
    const detailAfterLink = await apiJson(api, 'GET', `/api/colonel/activities/${activityId}/products/${productId}`, admin);
    expect(BUSINESS_READY.has(String(detailAfterLink.bizStatus)), 'product remains business-ready after reusable promotion mapping check').toBe(true);
    summary.promotion = {
      pickSource,
      mappingId: reusableMapping.mappingId || '',
      promotionLinkId: reusableMapping.promotionLinkId || '',
      promotionUrl: reusableMapping.promotionUrl || '',
      hasPromoteLink: Boolean(reusableMapping.promotionUrl || reusableMapping.shortUrl),
      mappingCount,
      finalBizStatus: detailAfterLink.bizStatus,
      promotionLinkStatus: detailAfterLink.promotionLinkStatus ?? null
    };

    const end = new Date();
    const start = new Date(end.getTime() - 30 * 60 * 1000);
    const orderSync = await apiJson(api, 'POST', '/api/orders/sync', admin, {
      data: { startTime: formatLocalDateTime(start), endTime: formatLocalDateTime(end) }
    });
    expect(Number(orderSync.failed ?? orderSync.failedCount ?? 0), 'order sync failed count').toBe(0);
    const orders = await apiJson(api, 'GET', '/api/orders', admin, {
      params: { page: 1, size: 10, productId }
    });
    summary.orders = {
      sync: {
        totalFetched: Number(orderSync.totalFetched ?? orderSync.fetched ?? orderSync.total ?? 0),
        created: Number(orderSync.created ?? 0),
        updated: Number(orderSync.updated ?? 0),
        attributed: Number(orderSync.attributed ?? 0),
        unattributed: Number(orderSync.unattributed ?? 0),
        failed: Number(orderSync.failed ?? orderSync.failedCount ?? 0)
      },
      productOrderTotal: Number(orders.total ?? 0)
    };

    const dashboardMetrics = await apiJson(api, 'GET', '/api/dashboard/metrics', admin);
    expect(dashboardMetrics, 'dashboard metrics returned').toBeTruthy();
    summary.dashboard = {
      todayOrderCount: Number(dashboardMetrics.todayOrderCount ?? dashboardMetrics.totalOrders ?? 0),
      todayGmv: String(dashboardMetrics.todayGmv ?? dashboardMetrics.totalAmount ?? '0')
    };

    await installAuth(page, admin);
    await assertPageOpens(page, '/system/douyin');
    await assertPageOpens(page, '/dashboard');
    await assertPageOpens(page, '/product');
    await assertPageOpens(page, '/orders');
    await assertPageOpens(page, '/data');
    summary.pages = ['/system/douyin', '/dashboard', '/product', '/orders', '/data'];
    summary.conclusion = 'PASS_NEEDS_CLEANUP';

    await writeEvidence(summary, testInfo.outputPath('business-summary.json'));
    await testInfo.attach('business-summary.json', {
      body: JSON.stringify(summary, null, 2),
      contentType: 'application/json'
    });
  } finally {
    await api.dispose();
  }
});

async function apiJson(
  api: APIRequestContext,
  method: 'GET' | 'POST' | 'PUT',
  path: string,
  auth: AuthPayload,
  options: { data?: JsonMap; params?: JsonMap } = {}
): Promise<JsonMap> {
  const headers = { Authorization: `Bearer ${String(auth.token ?? '')}` };
  const response = method === 'GET'
    ? await api.get(path, { headers, params: options.params as Record<string, string | number | boolean> | undefined })
    : method === 'POST'
      ? await api.post(path, { headers, data: options.data, params: options.params as Record<string, string | number | boolean> | undefined })
      : await api.put(path, { headers, data: options.data, params: options.params as Record<string, string | number | boolean> | undefined });
  const body = await response.json().catch(() => undefined) as JsonMap | undefined;
  expect(response.ok(), `${method} ${path} HTTP ${response.status()}`).toBe(true);
  expect(body?.code, `${method} ${path} business code`).toBe(200);
  return (body?.data ?? body) as JsonMap;
}

function readProductOperationStateSnapshot(activityId: string, productId: string): JsonMap {
  const container = process.env.E2E_DB_CONTAINER || 'saas-active-postgres-real-pre-1';
  const user = process.env.E2E_DB_USER || 'saas';
  const db = process.env.E2E_DB_NAME || 'saas_real_pre';
  const sql = `
select coalesce(row_to_json(t)::text, '')
from (
  select
    audit_status,
    biz_status,
    audit_remark,
    audit_payload,
    bound_activity_id,
    assignee_id::text as assignee_id,
    promote_link,
    short_link,
    promotion_scene,
    external_unique_id,
    last_operation_at::text as last_operation_at,
    selected_to_library,
    selected_at::text as selected_at,
    selected_by::text as selected_by,
    deleted
  from product_operation_state
  where activity_id = ${sqlLiteral(activityId)}
    and product_id = ${sqlLiteral(productId)}
  limit 1
) t;`;
  const out = execFileSync('docker', ['exec', container, 'psql', '-X', '-q', '-v', 'ON_ERROR_STOP=1', '-U', user, '-d', db, '-tAc', sql], {
    encoding: 'utf8'
  }).trim();
  return {
    activityId,
    productId,
    before: out ? JSON.parse(out) : null
  };
}

function readProductOperationLogIds(activityId: string, productId: string): string[] {
  const container = process.env.E2E_DB_CONTAINER || 'saas-active-postgres-real-pre-1';
  const user = process.env.E2E_DB_USER || 'saas';
  const db = process.env.E2E_DB_NAME || 'saas_real_pre';
  const sql = [
    'select id::text',
    'from product_operation_log',
    `where activity_id = ${sqlLiteral(activityId)}`,
    `and product_id = ${sqlLiteral(productId)}`,
    'order by create_time asc;'
  ].join(' ');
  const out = execFileSync('docker', ['exec', container, 'psql', '-X', '-q', '-v', 'ON_ERROR_STOP=1', '-U', user, '-d', db, '-tAc', sql], {
    encoding: 'utf8'
  }).trim();
  return out ? out.split(/\r?\n/).map((line) => line.trim()).filter(Boolean) : [];
}

function diffNewIds(before: string[], after: string[]): string[] {
  const seen = new Set(before);
  return after.filter((id) => !seen.has(id));
}

function sqlLiteral(value: string): string {
  return `'${String(value).replace(/'/g, "''")}'`;
}

function selectCandidate(items: JsonMap[]): JsonMap {
  const preferredProductId = process.env.E2E_PRODUCT_ID || DEFAULT_PRODUCT_ID;
  if (preferredProductId) {
    const preferred = items.find((item) => String(item.productId ?? item.product_id) === preferredProductId);
    if (preferred) return preferred;
  }
  const linkable = items.find((row) => isLinkableContext(row) && (row.productId || row.product_id));
  if (linkable) return linkable;
  const priorities = ['PENDING_AUDIT', 'APPROVED', 'BOUND', 'ASSIGNED', 'LINKED', 'FOLLOWING'];
  for (const status of priorities) {
    const item = items.find((row) => String(row.bizStatus || 'PENDING_AUDIT') === status && (row.productId || row.product_id));
    if (item) return item;
  }
  const fallback = items.find((row) => row.productId || row.product_id);
  expect(fallback, 'activity product candidate').toBeTruthy();
  return fallback as JsonMap;
}

function isLinkableContext(row: JsonMap): boolean {
  const hasUrlContext = Boolean(row.detailUrl || row.promoteLink || row.promotionUrl);
  const hasOriginContext = Boolean(row.originColonelBuyinId || row.origin_buyin_id || row.colonelBuyinId);
  const hasLinkedState = ['LINKED', 'FOLLOWING'].includes(String(row.bizStatus || ''));
  return hasLinkedState || hasUrlContext || hasOriginContext;
}

function buildAuditPayload(): JsonMap {
  return {
    approved: true,
    reason: 'real-pre business smoke approval',
    exclusivePriceRemark: 'real-pre smoke: verified activity price context.',
    shippingInfo: 'real-pre smoke: shipping info confirmed.',
    sellingPoints: ['real-pre smoke selected product', 'activity product can enter local library'],
    promotionScript: 'real-pre smoke promotion script.',
    supportsAds: true,
    rewardRemark: 'real-pre smoke reward remark.',
    participationRequirements: 'real-pre smoke participation requirement.',
    campaignTimeRemark: 'real-pre smoke campaign window.',
    materialFiles: ['real-pre-smoke-material']
  };
}

async function assertPageOpens(page: Page, path: string): Promise<void> {
  await gotoApp(page, path, { timeout: REAL_PRE_NAV_TIMEOUT_MS });
  await expect(page.locator('body'), `${path} should not show runtime error`).not.toContainText(
    /Unexpected Application Error|Application Error|Bad Gateway|Internal Server Error/i
  );
}

function findActivityId(input: unknown): string | null {
  const list = findDeepValue(input, ['activity_list', 'activityList', 'activities', 'data']);
  if (Array.isArray(list)) {
    const first = list.find((item) => item && typeof item === 'object') as JsonMap | undefined;
    const value = first?.activity_id ?? first?.activityId ?? first?.id;
    return value ? String(value) : null;
  }
  const value = findDeepValue(input, ['activity_id', 'activityId']);
  return value ? String(value) : null;
}

function findProductArray(input: unknown, seen = new Set<unknown>()): JsonMap[] {
  if (!input || seen.has(input)) return [];
  if (Array.isArray(input)) {
    const first = input[0] as JsonMap | undefined;
    if (!first || typeof first !== 'object') return input as JsonMap[];
    if ('product_id' in first || 'productId' in first || 'title' in first || 'productName' in first) {
      return input as JsonMap[];
    }
  }
  if (typeof input !== 'object') return [];
  seen.add(input);
  for (const value of Object.values(input as JsonMap)) {
    const found = findProductArray(value, seen);
    if (found.length) return found;
  }
  return [];
}

function findSkuCount(input: unknown): number {
  const container = findDeepValue(input, ['skus', 'sku_map', 'skuMap', 'sku_list', 'skuList', 'list']);
  if (Array.isArray(container)) return container.length;
  if (container && typeof container === 'object') return Object.keys(container).length;
  return 0;
}

function isUpstreamSuccess(result: unknown): boolean {
  const code = findDeepValue(result, ['code', 'err_no', 'errorCode']);
  if (code === undefined || code === null || code === '') {
    return (result as JsonMap | undefined)?.status === 'success';
  }
  return ['10000', '0', '200'].includes(String(code));
}

function findDeepValue(input: unknown, keys: string[], seen = new Set<unknown>()): unknown {
  if (!input || typeof input !== 'object' || seen.has(input)) return undefined;
  seen.add(input);
  for (const key of keys) {
    if (Object.prototype.hasOwnProperty.call(input, key)) {
      return (input as JsonMap)[key];
    }
  }
  for (const value of Object.values(input as JsonMap)) {
    const found = findDeepValue(value, keys, seen);
    if (found !== undefined) return found;
  }
  return undefined;
}

function formatLocalDateTime(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

function formatRunStamp(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}_${pad(date.getHours())}${pad(date.getMinutes())}${pad(date.getSeconds())}`;
}

async function writeEvidence(summary: JsonMap, fallbackPath: string): Promise<void> {
  const evidenceDir = process.env.E2E_BUSINESS_EVIDENCE_DIR;
  const target = evidenceDir ? join(evidenceDir, 'summary.json') : fallbackPath;
  mkdirSync(evidenceDir || fallbackPath.replace(/[\\/][^\\/]+$/, ''), { recursive: true });
  writeFileSync(target, JSON.stringify(summary, null, 2), 'utf8');
}

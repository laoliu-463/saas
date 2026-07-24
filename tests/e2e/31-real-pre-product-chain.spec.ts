/**
 * real-pre P0 / 31 / 商品链
 *
 * 验证 real-pre 下：
 *   - 管理员登录可用
 *   - 抖店 Token 已授权（缺则 BLOCKED_AUTH）
 *   - 真实活动列表可读（缺权限 -> BLOCKED_UPSTREAM_ACTIVITY_AUTH）
 *   - 真实活动商品可读（空 -> PENDING_NO_ACTIVITY_PRODUCTS）
 *   - 本地业务商品视图可读
 *   - 商品详情可读且字段完整
 *   - /product 与 /system/douyin 页面不出现运行时错误
 *
 * 不会真实创建上游转链，不会修改真实业务数据。
 * 结论 PASS / BLOCKED / PENDING / FAIL 通过 step-summary.json 输出。
 */
import { test, expect, request as playwrightRequest, type APIRequestContext, type Page, type Request } from '@playwright/test';
import { accounts } from './helpers/test-data';
import { installAuth } from './helpers/auth';
import { gotoApp } from './helpers/page-ready';
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
  isUpstreamSuccess,
  findDeepValue
} from './helpers/real-pre-p0-step';

type JsonMap = Record<string, unknown>;
type ProductImageSnapshot = {
  src: string;
  alt: string;
  complete: boolean;
  naturalWidth: number;
  naturalHeight: number;
  display: string;
  visibility: string;
};
type ProductImageRequestFailure = {
  url: string;
  errorText: string;
};
type ProductImageCheck = {
  route: string;
  finalPath: string;
  imageCount: number;
  loadedCount: number;
  failedCount: number;
  requestFailureCount: number;
  cspFailureCount: number;
  failedImages: ProductImageSnapshot[];
  requestFailures: ProductImageRequestFailure[];
};

const BUSINESS_READY = new Set(['PENDING_AUDIT', 'APPROVED', 'BOUND', 'ASSIGNED', 'LINKED', 'FOLLOWING']);
const FATAL_TEXT = /Unexpected Application Error|Application Error|Bad Gateway|Internal Server Error/i;
const REAL_PRE_NAV_TIMEOUT_MS = Number(process.env.E2E_REAL_PRE_NAV_TIMEOUT_MS || 120_000);

test('real-pre P0 / 31 / 商品链', async ({ page }, testInfo) => {
  test.skip(!shouldRunRealPreP0('31-real-pre-product-chain'), 'Run via npm run e2e:real-pre:p0 or set E2E_REAL_PRE_P0=true');
  test.setTimeout(15 * 60_000);
  ensureRealPreP0Env();

  const ctx = createRealPreP0Step('31-real-pre-product-chain', 'real-pre-p0/31/product-chain');
  const backend = (process.env.E2E_BACKEND_URL || 'http://localhost:8081').replace(/\/$/, '');
  const api = await playwrightRequest.newContext({ baseURL: backend, ignoreHTTPSErrors: true });

  try {
    const admin = await apiLogin(`${backend}/api`, accounts.admin.username, accounts.admin.password);
    setDetail(ctx, 'admin', { userId: String(admin.userId || admin.id || ''), username: String(admin.username || accounts.admin.username) });

    const tokenResult = await rawApi(api, 'GET', '/api/douyin/tokens', String(admin.token || ''));
    const tokenData = safeUnwrap<JsonMap>(tokenResult.body) || {};
    const tokenReady =
      tokenData.hasAccessToken === true &&
      tokenData.hasRefreshToken === true &&
      tokenData.reauthorizeRequired !== true;
    setDetail(ctx, 'tokenStatus', {
      hasAccessToken: tokenData.hasAccessToken === true,
      hasRefreshToken: tokenData.hasRefreshToken === true,
      reauthorizeRequired: tokenData.reauthorizeRequired === true,
      appId: tokenData.appId
    });
    if (!tokenResult.ok) {
      markFail(ctx, `GET /api/douyin/tokens HTTP ${tokenResult.status}`);
    } else if (!tokenReady) {
      markBlocked(ctx, 'BLOCKED_AUTH: real-pre 抖店 Token 缺失或需要重新授权');
      persistStepSummary(ctx);
      // 已经 BLOCKED，仍可继续打开页面做最低限度可达性检查，但不算 PASS
    }

    let activityId: string | null = null;
    if (tokenReady) {
      const activitiesResult = await rawApi(api, 'GET', '/api/douyin/activities', String(admin.token || ''));
      const activitiesData = safeUnwrap<JsonMap>(activitiesResult.body) || {};
      const upstreamOk = activitiesResult.ok && isUpstreamSuccess(activitiesData);
      activityId = process.env.E2E_ACTIVITY_ID || findActivityId(activitiesData);
      setDetail(ctx, 'activitiesProbe', {
        status: activitiesResult.status,
        upstreamOk,
        remoteCode: findDeepValue(activitiesData, ['code', 'err_no']),
        activityId
      });
      if (!upstreamOk) {
        markBlocked(ctx, 'BLOCKED_UPSTREAM_ACTIVITY_AUTH: 抖音联盟活动列表上游失败（可能官方权限或限流）');
      }
    }

    let rawProductCount = 0;
    if (tokenReady && activityId && ctx.summary.conclusion !== 'BLOCKED') {
      const rawProducts = await rawApi(api, 'GET', '/api/douyin/activity-product-list', String(admin.token || ''), {
        params: { activityId, count: 20 }
      });
      const rawProductsData = safeUnwrap<JsonMap>(rawProducts.body) || {};
      const upstreamOk = rawProducts.ok && isUpstreamSuccess(rawProductsData);
      const productArray = findProductArray(rawProductsData);
      rawProductCount = productArray.length;
      setDetail(ctx, 'rawActivityProducts', {
        status: rawProducts.status,
        upstreamOk,
        count: rawProductCount,
        remoteCode: findDeepValue(rawProductsData, ['code', 'err_no'])
      });
      if (!upstreamOk) {
        markBlocked(ctx, 'BLOCKED_UPSTREAM_ACTIVITY_AUTH: 活动商品上游失败');
      } else if (rawProductCount === 0) {
        markPending(ctx, 'PENDING_NO_ACTIVITY_PRODUCTS: 当前活动上游商品列表为空，无法验证商品链');
      }
    }

    let candidateProductId: string | null = null;
    let businessProductCount = 0;
    let initialBizStatus: string | null = null;
    if (tokenReady && activityId && ctx.summary.conclusion === 'PASS') {
      const businessResult = await rawApi(api, 'GET', `/api/colonel/activities/${activityId}/products`, String(admin.token || ''), {
        params: { count: 20, refresh: true }
      });
      if (!businessResult.ok) {
        markFail(ctx, `GET /api/colonel/activities/${activityId}/products HTTP ${businessResult.status}`);
      } else {
        const businessData = safeUnwrap<JsonMap>(businessResult.body) || {};
        const items = Array.isArray((businessData as JsonMap).items)
          ? ((businessData as JsonMap).items as JsonMap[])
          : findProductArray(businessData);
        businessProductCount = items.length;
        const candidate = selectCandidate(items);
        candidateProductId = candidate ? String(candidate.productId ?? candidate.product_id ?? '') : null;
        initialBizStatus = candidate ? String(candidate.bizStatus ?? '') : null;
        setDetail(ctx, 'businessProducts', {
          status: businessResult.status,
          count: businessProductCount,
          productId: candidateProductId,
          bizStatus: initialBizStatus
        });
        if (businessProductCount === 0) {
          markPending(ctx, 'PENDING_NO_ACTIVITY_PRODUCTS: 本地业务商品视图为空');
        } else if (!candidateProductId) {
          markPending(ctx, 'PENDING_NO_ACTIVITY_PRODUCTS: 找不到任何包含 productId 的候选商品');
        } else if (!BUSINESS_READY.has(String(initialBizStatus || 'PENDING_AUDIT'))) {
          markPending(ctx, `PENDING_NO_ACTIVITY_PRODUCTS: 候选商品 bizStatus=${initialBizStatus} 不在合法状态集合`);
        }
      }
    }

    if (tokenReady && activityId && candidateProductId && ctx.summary.conclusion === 'PASS') {
      const detailResult = await rawApi(api, 'GET', `/api/colonel/activities/${activityId}/products/${candidateProductId}`, String(admin.token || ''));
      if (!detailResult.ok) {
        markFail(ctx, `GET product detail HTTP ${detailResult.status}`);
      } else {
        const detailData = safeUnwrap<JsonMap>(detailResult.body) || {};
        const productId = String(detailData.productId ?? detailData.product_id ?? '');
        const productName = String(detailData.productName ?? detailData.product_name ?? detailData.title ?? '');
        const bizStatus = String(detailData.bizStatus ?? '');
        const hasRequired = Boolean(productId) && Boolean(productName);
        setDetail(ctx, 'productDetail', {
          productId,
          productName,
          bizStatus,
          selectedToLibrary: Boolean(detailData.selectedToLibrary || detailData.libraryVisible)
        });
        if (!hasRequired) {
          markFail(ctx, 'product detail 缺少 productId/productName 等关键字段');
        } else if (!BUSINESS_READY.has(bizStatus || 'PENDING_AUDIT')) {
          markFail(ctx, `product detail bizStatus=${bizStatus} 不在合法状态集合`);
        }
      }
    }

    // 页面 smoke：始终尝试，无论上面是 BLOCKED/PENDING 还是 PASS，
    // 因为 /product 与 /system/douyin 在 BLOCKED/PENDING 下也必须不崩溃。
    await installAuth(page, admin);
    const productPage = await openAndAssertNoFatal(page, '/product');
    const douyinPage = await openAndAssertNoFatal(page, '/system/douyin');
    const productImageCheck = await openProductManageAndAssertImages(page, activityId);
    setDetail(ctx, 'pageCheck', { productPage, douyinPage, productImageCheck });
    if (productPage.runtimeError) markFail(ctx, '/product 出现运行时错误');
    if (douyinPage.runtimeError) markFail(ctx, '/system/douyin 出现运行时错误');
    if (businessProductCount > 0 && productImageCheck.imageCount === 0) {
      markFail(ctx, '商品列表有业务商品，但 /product/manage/products 未渲染任何外链商品图片');
    }
    if (productImageCheck.failedCount > 0 || productImageCheck.requestFailureCount > 0) {
      markFail(
        ctx,
        `商品图片加载失败: failedImages=${productImageCheck.failedCount}, requestFailures=${productImageCheck.requestFailureCount}, cspFailures=${productImageCheck.cspFailureCount}`
      );
    }

    setDetail(ctx, 'activityId', activityId);
    setDetail(ctx, 'rawProductCount', rawProductCount);
    setDetail(ctx, 'businessProductCount', businessProductCount);
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

  // FAIL 时让 Playwright 失败；BLOCKED/PENDING 走 step-summary 由 orchestrator 解读，
  // 不在这里抛错，避免 orchestrator 把 BLOCKED 误判为 FAIL。
  expect(
    ctx.summary.conclusion === 'FAIL'
      ? ctx.summary.failures.join('; ')
      : 'OK',
    `real-pre 31 商品链 conclusion=${ctx.summary.conclusion}`
  ).toBe('OK');
});

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

function selectCandidate(items: JsonMap[]): JsonMap | null {
  if (!items.length) return null;
  const preferredProductId = process.env.E2E_PRODUCT_ID;
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
  return items.find((row) => row.productId || row.product_id) || null;
}

function isLinkableContext(row: JsonMap): boolean {
  const hasUrlContext = Boolean(row.detailUrl || row.promoteLink || row.promotionUrl);
  const hasOriginContext = Boolean(row.originColonelBuyinId || row.origin_buyin_id || row.colonelBuyinId);
  const hasLinkedState = ['LINKED', 'FOLLOWING'].includes(String(row.bizStatus || ''));
  return hasLinkedState || hasUrlContext || hasOriginContext;
}

async function openAndAssertNoFatal(page: Page, route: string): Promise<{ route: string; finalPath: string; runtimeError: boolean }> {
  await gotoApp(page, route, { timeout: REAL_PRE_NAV_TIMEOUT_MS });
  const bodyText = await page.locator('body').innerText({ timeout: 10_000 }).catch(() => '');
  const finalPath = new URL(page.url()).pathname;
  return {
    route,
    finalPath,
    runtimeError: FATAL_TEXT.test(bodyText)
  };
}

async function openProductManageAndAssertImages(page: Page, activityId?: string | null): Promise<ProductImageCheck> {
  const route = activityId
    ? `/product/manage/products?activityId=${encodeURIComponent(activityId)}`
    : '/product/manage/products';
  const requestFailures: ProductImageRequestFailure[] = [];
  const onRequestFailed = (request: Request): void => {
    if (request.resourceType() !== 'image') return;
    const url = request.url();
    if (!isExternalHttpUrl(url, currentPageOrigin(page))) return;
    requestFailures.push({
      url,
      errorText: request.failure()?.errorText || 'unknown'
    });
  };

  page.on('requestfailed', onRequestFailed);
  try {
    await gotoApp(page, route, { timeout: REAL_PRE_NAV_TIMEOUT_MS });
    // 外链图片可能晚于 networkidle 完成，先等待已渲染图片进入完成态，
    // 再根据 naturalWidth 判断真实加载失败，避免把正常慢加载误报成 FAIL。
    await page.waitForFunction(() => {
      const pageOrigin = window.location.origin;
      const externalImages = Array.from(document.images).filter((img) => {
        const src = img.currentSrc || img.getAttribute('src') || '';
        if (!src || src.startsWith('data:') || src.startsWith('blob:')) return false;
        try {
          const url = new URL(src, window.location.href);
          return (url.protocol === 'http:' || url.protocol === 'https:') && url.origin !== pageOrigin;
        } catch {
          return false;
        }
      });
      return externalImages.every((img) => img.complete);
    }, { timeout: 30_000 }).catch(() => undefined);
    const finalPath = new URL(page.url()).pathname;
    const images = await page.locator('img').evaluateAll((nodes) => {
      return nodes
        .map((node) => {
          const img = node as HTMLImageElement;
          const style = window.getComputedStyle(img);
          return {
            src: img.currentSrc || img.getAttribute('src') || '',
            alt: img.getAttribute('alt') || '',
            complete: img.complete,
            naturalWidth: img.naturalWidth,
            naturalHeight: img.naturalHeight,
            display: style.display,
            visibility: style.visibility
          };
        })
        .filter((img) => {
          if (!img.src || img.src.startsWith('data:') || img.src.startsWith('blob:')) return false;
          try {
            const url = new URL(img.src, window.location.href);
            return (url.protocol === 'http:' || url.protocol === 'https:') && url.origin !== window.location.origin;
          } catch {
            return false;
          }
        });
    });
    const failedImages = images
      .filter((img) => !img.complete || img.naturalWidth <= 0 || img.naturalHeight <= 0)
      .slice(0, 10);
    const cspFailures = requestFailures.filter((failure) => /csp/i.test(failure.errorText));
    return {
      route,
      finalPath,
      imageCount: images.length,
      loadedCount: images.length - failedImages.length,
      failedCount: failedImages.length,
      requestFailureCount: requestFailures.length,
      cspFailureCount: cspFailures.length,
      failedImages,
      requestFailures: requestFailures.slice(0, 10)
    };
  } finally {
    page.off('requestfailed', onRequestFailed);
  }
}

function currentPageOrigin(page: Page): string | null {
  try {
    const url = page.url();
    return url && url !== 'about:blank' ? new URL(url).origin : null;
  } catch {
    return null;
  }
}

function isExternalHttpUrl(url: string, pageOrigin: string | null): boolean {
  try {
    const parsed = new URL(url);
    if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') return false;
    return pageOrigin ? parsed.origin !== pageOrigin : true;
  } catch {
    return false;
  }
}

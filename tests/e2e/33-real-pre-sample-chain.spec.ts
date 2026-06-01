/**
 * real-pre P0 / 33 / 寄样链
 *
 * 验证寄样状态机：申请、7 天重复限制、招商审核、运营录入物流、状态推进。
 * 是否能命中“真实成交 -> 自动完成”取决于上游样本：
 *   - 缺真实成交订单时输出 PENDING_REAL_ORDER_FOR_HOMEWORK，不写 PASS。
 * 不删除真实业务数据；新增对象统一带 runId。
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
  markPassNeedsCleanup,
  persistStepSummary,
  setDetail,
  shouldRunRealPreP0,
  safeUnwrap
} from './helpers/real-pre-p0-step';

type JsonMap = Record<string, unknown>;
type ReusablePromotionMapping = {
  mappingId?: string;
  productId?: string;
  activityId?: string;
  userId?: string;
};

// biz_staff 不在 test-data.ts 的 accounts 表里（accounts 只列了 5 个角色），
// 不能借 accounts.bizLeader.password —— 一旦真实环境给 bizLeader 单独覆盖
// E2E_BIZ_LEADER_PASSWORD，biz_staff 就会因为密码错位而被误判 BLOCKED_AUTH。
// 与 35 spec 的 PASSWORD 常量风格对齐，让 biz_staff 走自己的覆盖链。
const BIZ_STAFF_PASSWORD =
  process.env.E2E_BIZ_STAFF_PASSWORD || process.env.E2E_DEFAULT_PASSWORD || 'admin123';

test('real-pre P0 / 33 / 寄样链', async ({}, testInfo) => {
  test.skip(!shouldRunRealPreP0('33-real-pre-sample-chain'), 'Run via npm run e2e:real-pre:p0 or set E2E_REAL_PRE_P0=true');
  test.setTimeout(20 * 60_000);
  ensureRealPreP0Env();

  const ctx = createRealPreP0Step('33-real-pre-sample-chain', 'real-pre-p0/33/sample-chain');
  const backend = (process.env.E2E_BACKEND_URL || 'http://localhost:8081').replace(/\/$/, '');
  const api = await playwrightRequest.newContext({ baseURL: backend, ignoreHTTPSErrors: true });

  try {
    const admin = await apiLogin(`${backend}/api`, accounts.admin.username, accounts.admin.password);
    const channelStaff = await apiLogin(`${backend}/api`, accounts.channelStaff.username, accounts.channelStaff.password);
    let bizStaffToken = '';
    let opsToken = '';
    try {
      const biz = await apiLogin(`${backend}/api`, 'biz_staff', BIZ_STAFF_PASSWORD);
      bizStaffToken = String(biz.token || '');
    } catch (error) {
      markBlocked(ctx, `BLOCKED_AUTH: biz_staff 登录失败：${error instanceof Error ? error.message : String(error)}`);
    }
    try {
      const ops = await apiLogin(`${backend}/api`, accounts.ops.username, accounts.ops.password);
      opsToken = String(ops.token || '');
    } catch (error) {
      markBlocked(ctx, `BLOCKED_AUTH: ops_staff 登录失败：${error instanceof Error ? error.message : String(error)}`);
    }

    setDetail(ctx, 'accounts', {
      admin: String(admin.userId || admin.id || ''),
      channelStaff: String(channelStaff.userId || channelStaff.id || ''),
      bizStaffLoginOk: Boolean(bizStaffToken),
      opsLoginOk: Boolean(opsToken)
    });

    // 1) 选商品候选：复用现有商品库列表（不真实创建新商品）。
    const libraryResult = await rawApi(api, 'GET', '/api/products', String(channelStaff.token || ''), {
      params: { page: 1, size: 20 }
    });
    const libraryRecords = extractRecords(safeUnwrap<JsonMap>(libraryResult.body));
    const productLookup = await resolveProductCandidate(api, String(admin.token || ''), libraryRecords);
    setDetail(ctx, 'productLibrary', {
      total: libraryRecords.length,
      status: libraryResult.status,
      candidateSource: productLookup.source,
      fallbackMapping: productLookup.mapping
    });
    const productCandidate = productLookup.product;
    if (!productCandidate) {
      markPending(ctx, 'PENDING_NO_ACTIVITY_PRODUCTS: 渠道账号商品库为空，且无可复用转链商品详情，无法发起真实寄样');
      persistStepSummary(ctx);
      await testInfo.attach('step-summary.json', { body: JSON.stringify(ctx.summary, null, 2), contentType: 'application/json' });
      await api.dispose();
      expect(ctx.summary.conclusion === 'FAIL' ? ctx.summary.failures.join('; ') : 'OK').toBe('OK');
      return;
    }

    const productLocalId = String(productCandidate.id);
    const productExternalId = String(productCandidate.productId ?? productCandidate.product_id ?? '');
    setDetail(ctx, 'product', { productLocalId, productExternalId });

    // 2) 新建一个带 runId 的本地 QA 达人；不调用真实第三方达人采集。
    const talentUid = `${ctx.runId}_t33_${Date.now()}`;
    const talentCreate = await rawApi(api, 'POST', '/api/talents', String(channelStaff.token || ''), {
      data: {
        douyinUid: talentUid,
        douyinNo: talentUid,
        nickname: `${ctx.runId} talent 33`,
        fansCount: 50000,
        level: 'L5',
        contactWechat: `wx_${talentUid}`,
        ipLocation: 'Shanghai'
      }
    });
    if (!talentCreate.ok || (talentCreate.body as JsonMap | undefined)?.code !== 200) {
      markFail(ctx, `创建达人失败：HTTP ${talentCreate.status} code=${(talentCreate.body as JsonMap | undefined)?.code}`);
    }
    const talent = safeUnwrap<JsonMap>(talentCreate.body) || {};
    const talentLocalId = String(talent.id || '');
    setDetail(ctx, 'talent', { talentLocalId, talentUid });
    if (talentLocalId) {
      const claim = await rawApi(api, 'POST', `/api/talents/${talentLocalId}/claims`, String(channelStaff.token || ''));
      setDetail(ctx, 'talentClaim', {
        status: claim.status,
        code: (claim.body as JsonMap | undefined)?.code
      });
      if (!claim.ok || (claim.body as JsonMap | undefined)?.code !== 200) {
        markFail(ctx, `达人认领失败：HTTP ${claim.status} code=${(claim.body as JsonMap | undefined)?.code}`);
      }
    }

    // 3) 渠道发起寄样申请。
    const samplePayload = {
      productId: productLocalId,
      talentId: talentUid,
      talentNickname: `${ctx.runId} talent 33`,
      talentFansCount: 50000,
      talentCreditScore: 4.8,
      talentMainCategory: 'food',
      quantity: 1,
      remark: `real-pre P0 33 ${ctx.runId}`
    };
    const sampleCreate = await rawApi(api, 'POST', '/api/samples', String(channelStaff.token || ''), { data: samplePayload });
    if (!sampleCreate.ok || (sampleCreate.body as JsonMap | undefined)?.code !== 200) {
      markFail(ctx, `创建寄样失败：HTTP ${sampleCreate.status} code=${(sampleCreate.body as JsonMap | undefined)?.code}`);
      persistStepSummary(ctx);
      await testInfo.attach('step-summary.json', { body: JSON.stringify(ctx.summary, null, 2), contentType: 'application/json' });
      await api.dispose();
      // R3 修法：与文件末尾的最终断言风格保持一致，让 Playwright 的 exit code
      // 与 step-summary.json 的 conclusion 双轨一致，避免 step-summary 写入异常时
      // orchestrator 把这条 FAIL 误判成 PASS。
      expect(
        ctx.summary.conclusion === 'FAIL' ? ctx.summary.failures.join('; ') : 'OK',
        `real-pre 33 寄样链早退分支 conclusion=${ctx.summary.conclusion}`
      ).toBe('OK');
      return;
    }
    const sample = safeUnwrap<JsonMap>(sampleCreate.body) || {};
    const sampleId = String(sample.id || '');
    const requestNo = String(sample.requestNo || '');
    setDetail(ctx, 'sample', { sampleId, requestNo, initialStatus: sample.status });

    const statusTransitions: Array<{ step: string; status: string; ok: boolean }> = [
      { step: 'create', status: String(sample.status || ''), ok: true }
    ];

    // 4) 7 天重复限制验证（同 channel+talent+product 再发一次）。
    const duplicate = await rawApi(api, 'POST', '/api/samples', String(channelStaff.token || ''), { data: samplePayload });
    const duplicateOk = !duplicate.ok || Number((duplicate.body as JsonMap | undefined)?.code) !== 200;
    setDetail(ctx, 'duplicateLimitResult', {
      blocked: duplicateOk,
      status: duplicate.status,
      code: (duplicate.body as JsonMap | undefined)?.code,
      msg: (duplicate.body as JsonMap | undefined)?.msg
    });
    if (!duplicateOk) {
      markFail(ctx, '7 天重复限制未生效：渠道账号能立即重复申请同一商品+达人');
    }

    // 5) biz_staff 审核通过 -> 待发货。
    //    biz_staff 审核依赖 isSampleProductAssignedToUser(product_operation_state.assigneeId = userId)。
    //    当商品来自商品库时 product_operation_state 由运营选品时建立，
    //    但当商品来自 colonel 活动时需要先通过 biz_leader 调用 audit-assignee 接口
    //    将商品分配给 biz_staff。
    const bizStaffUserId = String(
      (await apiLogin(`${backend}/api`, 'biz_staff', BIZ_STAFF_PASSWORD)).userId || ''
    );
    if (bizStaffUserId && productLookup.mapping?.activityId && productLookup.mapping?.productId) {
      const assignRes = await rawApi(
        api,
        'PUT',
        `/api/colonel/activities/${productLookup.mapping.activityId}/products/${productLookup.mapping.productId}/audit-assignee`,
        String(admin.token || ''),
        { data: { assigneeId: bizStaffUserId } }
      );
      setDetail(ctx, 'bizStaffAssignResult', { status: assignRes.status, ok: assignRes.ok });
      if (!assignRes.ok) {
        markBlocked(ctx, `分配商品给 biz_staff 失败 HTTP ${assignRes.status}`);
      }
    }
    if (bizStaffToken && sampleId) {
      const audit = await rawApi(api, 'PUT', `/api/samples/${sampleId}/status`, bizStaffToken, {
        data: { action: 'PENDING_SHIP', reason: `real-pre P0 33 biz audit ${ctx.runId}` }
      });
      const auditData = safeUnwrap<JsonMap>(audit.body) || {};
      statusTransitions.push({ step: 'biz_audit', status: String(auditData.status || ''), ok: audit.ok });
      if (!audit.ok) {
        markFail(ctx, `招商审核失败 HTTP ${audit.status}`);
      } else if (String(auditData.status) !== 'PENDING_SHIP') {
        markFail(ctx, `招商审核后状态不是 PENDING_SHIP, 实际=${auditData.status}`);
      }
    } else if (!bizStaffToken) {
      markBlocked(ctx, 'BLOCKED_AUTH: 缺少 biz_staff 账号，无法验证审核步骤');
    }

    // 6) ops_staff 录入物流单号 + 推进。
    let opsTrackingNo = '';
    if (opsToken && sampleId && ctx.summary.conclusion === 'PASS') {
      opsTrackingNo = `SF${ctx.runId}_LOGI_${Date.now()}`;
      const shipped = await rawApi(api, 'PUT', `/api/samples/${sampleId}/status`, opsToken, {
        data: { action: 'SHIPPING', trackingNo: opsTrackingNo, shipperCode: 'SF', reason: `real-pre P0 33 ops ship ${ctx.runId}` }
      });
      const shippedData = safeUnwrap<JsonMap>(shipped.body) || {};
      statusTransitions.push({ step: 'ops_ship', status: String(shippedData.status || ''), ok: shipped.ok });
      if (!shipped.ok) {
        markFail(ctx, `运营发货失败 HTTP ${shipped.status}`);
      } else if (String(shippedData.status) !== 'SHIPPED') {
        markFail(ctx, `运营发货后状态不是 SHIPPED, 实际=${shippedData.status}`);
      } else if (String(shippedData.trackingNo) !== opsTrackingNo) {
        markFail(ctx, '物流单号未持久化');
      }

      const pendingHomework = await rawApi(api, 'PUT', `/api/samples/${sampleId}/status`, opsToken, {
        data: { action: 'PENDING_HOMEWORK', reason: `real-pre P0 33 ops handoff ${ctx.runId}` }
      });
      const pendingHomeworkData = safeUnwrap<JsonMap>(pendingHomework.body) || {};
      statusTransitions.push({ step: 'ops_pending_homework', status: String(pendingHomeworkData.status || ''), ok: pendingHomework.ok });
      if (!pendingHomework.ok) {
        markFail(ctx, `推进到待交作业失败 HTTP ${pendingHomework.status}`);
      }
    } else if (!opsToken && ctx.summary.conclusion === 'PASS') {
      markBlocked(ctx, 'BLOCKED_AUTH: 缺少 ops_staff 账号，无法验证物流步骤');
    }

    setDetail(ctx, 'statusTransitions', statusTransitions);
    setDetail(ctx, 'logisticsResult', { trackingNo: opsTrackingNo });

    // 7) 自动完成只能在真实成交命中时声明，否则 PENDING。
    const finalCheck = await rawApi(api, 'GET', `/api/samples/${sampleId}`, String(admin.token || ''));
    const finalData = safeUnwrap<JsonMap>(finalCheck.body) || {};
    const finalStatus = String(finalData.status || '');
    setDetail(ctx, 'finalSampleStatus', finalStatus);
    if (finalStatus === 'COMPLETED' || finalStatus === 'CLOSED') {
      setDetail(ctx, 'homeworkResult', 'AUTO_COMPLETED_BY_REAL_ORDER');
    } else if (ctx.summary.conclusion === 'PASS') {
      markPending(ctx, 'PENDING_REAL_ORDER_FOR_HOMEWORK: 当前无真实成交订单可触发寄样自动完成，仅能验证手动状态机');
      setDetail(ctx, 'homeworkResult', 'PENDING_REAL_ORDER_FOR_HOMEWORK');
    }

    // 状态机走通且新增了 QA 数据 -> 必须经过清理计划再宣称完成。
    if (ctx.summary.conclusion === 'PASS') {
      markPassNeedsCleanup(ctx);
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
    ctx.summary.conclusion === 'FAIL' ? ctx.summary.failures.join('; ') : 'OK',
    `real-pre 33 寄样链 conclusion=${ctx.summary.conclusion}`
  ).toBe('OK');
});

async function resolveProductCandidate(
  api: APIRequestContext,
  adminToken: string,
  libraryRecords: JsonMap[]
): Promise<{ product: JsonMap | null; source: string; mapping: JsonMap | null }> {
  const fromLibrary = libraryRecords.find((row) => row.id);
  if (fromLibrary) {
    return { product: fromLibrary, source: 'product-library', mapping: null };
  }
  const mapping = scanAnyReusableMapping()[0];
  if (!mapping?.activityId || !mapping?.productId) {
    return { product: null, source: 'none', mapping: mapping || null };
  }
  const detail = await rawApi(
    api,
    'GET',
    `/api/colonel/activities/${mapping.activityId}/products/${mapping.productId}`,
    adminToken
  );
  const product = safeUnwrap<JsonMap>(detail.body) || null;
  return {
    product: product && product.id ? product : null,
    source: product && product.id ? 'reusable-promotion-mapping' : 'none',
    mapping
  };
}

function scanAnyReusableMapping(): ReusablePromotionMapping[] {
  const { execFileSync } = require('node:child_process');
  const container = process.env.E2E_DB_CONTAINER || 'saas-active-postgres-real-pre-1';
  const user = process.env.E2E_DB_USER || 'saas';
  const db = process.env.E2E_DB_NAME || 'saas_real_pre';
  const sql = [
    "select psm.id::text, psm.product_id, psm.activity_id, psm.user_id::text",
    'from pick_source_mapping psm',
    'left join promotion_link pl on pl.id = psm.promotion_link_id and pl.deleted = 0',
    'where psm.deleted = 0',
    '  and psm.status = 1',
    "  and coalesce(psm.pick_source, '') <> ''",
    "  and coalesce(pl.promotion_url, psm.converted_url, '') <> ''",
    'order by psm.update_time desc nulls last, psm.create_time desc',
    'limit 5;'
  ].join('\n');
  const out = execFileSync(
    'docker',
    ['exec', container, 'psql', '-X', '-q', '-v', 'ON_ERROR_STOP=1', '-U', user, '-d', db, '-t', '-A', '-F', '|', '-c', sql],
    { encoding: 'utf8' }
  );
  return String(out || '').split(/\r?\n/).filter(Boolean).map((line) => {
    const parts = line.split('|');
    return {
      mappingId: parts[0] || '',
      productId: parts[1] || '',
      activityId: parts[2] || '',
      userId: parts[3] || ''
    };
  });
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

function extractRecords(data: unknown): Record<string, unknown>[] {
  if (Array.isArray(data)) return data as Record<string, unknown>[];
  if (!data || typeof data !== 'object') return [];
  for (const key of ['records', 'items', 'list', 'rows', 'content']) {
    const value = (data as Record<string, unknown>)[key];
    if (Array.isArray(value)) return value as Record<string, unknown>[];
  }
  return [];
}

const fs = require('node:fs');
const path = require('node:path');

const SCRIPT_NAME = 'business-state-flow-regression';
const REPO_ROOT = path.resolve(__dirname, '..', '..');
const OUT_ROOT = path.join(__dirname, 'out');
const API_BASE_URL = normalizeBaseUrl(process.env.API_BASE_URL || 'http://127.0.0.1:8080');
const REQUEST_TIMEOUT_MS = Number(process.env.QA_REQUEST_TIMEOUT_MS || 20000);
const ACTIVITY_ID = process.env.QA_TEST_ACTIVITY_ID || 'TEST_ACTIVITY_A';
const PRODUCTS = {
  assigned: '10901825',
  pendingAudit: '10901826',
  rejected: '10901827',
  approved: '10901828'
};
const ACCOUNTS = {
  admin: { username: process.env.QA_ADMIN_USER || 'admin', password: process.env.QA_ADMIN_PASSWORD || 'admin123' },
  bizLeader: { username: 'biz_leader', password: 'admin123' },
  bizStaff: { username: 'biz_staff', password: 'admin123' },
  channelStaff: { username: 'channel_staff', password: 'admin123' },
  opsStaff: { username: 'ops_staff', password: 'admin123' }
};

const STATUS_LABELS = {
  PENDING_AUDIT: '待审核',
  APPROVED: '已上架',
  REJECTED: '已拒绝',
  ASSIGNED: '已分配',
  LINKED: '已转链',
  PENDING_SHIP: '待发货',
  SHIPPING: '快递中',
  SHIPPED: '快递中',
  DELIVERED: '已签收',
  PENDING_HOMEWORK: '待交作业',
  PENDING_TASK: '待交作业',
  COMPLETED: '已完成',
  FINISHED: '已完成',
  CLOSED: '已关闭',
  ATTRIBUTED: '已归因',
  UNATTRIBUTED: '未归因',
  NO_PICK_SOURCE: '无 pick_source',
  MAPPING_NOT_FOUND: '映射缺失',
  COLONEL_MAPPING_NOT_FOUND: '映射缺失',
  PRODUCT_NOT_FOUND: '商品未覆盖',
  REFUNDED_OR_CLOSED: '退款关闭',
  COLONEL_MAPPING_AMBIGUOUS: '歧义映射'
};

function timestamp(date = new Date()) {
  const pad = (n, len = 2) => String(n).padStart(len, '0');
  return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}-${pad(date.getHours())}${pad(date.getMinutes())}${pad(date.getSeconds())}-${pad(date.getMilliseconds(), 3)}`;
}

function normalizeBaseUrl(value) {
  return String(value || '').replace(/\/+$/, '');
}

function ensureDir(dir) {
  fs.mkdirSync(dir, { recursive: true });
}

function unwrapApiBody(body) {
  if (body && typeof body === 'object' && Object.prototype.hasOwnProperty.call(body, 'data')) {
    return body.data;
  }
  return body;
}

function extractRecords(body) {
  const data = unwrapApiBody(body);
  if (Array.isArray(data)) return data;
  if (!data || typeof data !== 'object') return [];
  for (const key of ['records', 'list', 'rows', 'items', 'content', 'orderList', 'orders', 'samples', 'productList']) {
    if (Array.isArray(data[key])) return data[key];
  }
  return [];
}

function getField(record, names) {
  const wanted = new Set([].concat(names).map((name) => String(name).toLowerCase()));
  const stack = [record];
  while (stack.length) {
    const current = stack.pop();
    if (!current || typeof current !== 'object') continue;
    for (const [key, value] of Object.entries(current)) {
      if (wanted.has(key.toLowerCase())) return value;
      if (value && typeof value === 'object') stack.push(value);
    }
  }
  return undefined;
}

function hasSuccessBusinessCode(body) {
  const code = body && typeof body === 'object' ? body.code : undefined;
  return code == null || Number(code) === 200;
}

function summarizeHttpResult(result) {
  return {
    method: result.method,
    path: result.path,
    url: result.url,
    status: result.status,
    ok: result.ok,
    businessCode: result.businessCode,
    durationMs: result.durationMs,
    error: result.error || null,
    message: result.body?.msg || result.body?.message || null
  };
}

function pickKeyFields(body) {
  const source = unwrapApiBody(body);
  const fields = {};
  const keys = [
    'id',
    'requestNo',
    'sampleRequestId',
    'productId',
    'activityId',
    'bizStatus',
    'auditStatus',
    'selectedToLibrary',
    'libraryVisible',
    'copyEnabled',
    'status',
    'statusText',
    'trackingNo',
    'orderId',
    'pickSource',
    'attributionStatus',
    'attributionRemark',
    'unattributedReason',
    'reason',
    'scanned',
    'attributed',
    'unattributed',
    'updated',
    'dryRun',
    'nativeKeyMatched',
    'unsafeBecauseCreatedAfterOrder',
    'ambiguousMapping',
    'visible',
    'recordsCount',
    'sampleStatus',
    'ambiguousMappingCount'
  ];
  for (const key of keys) {
    const value = getField(source, key);
    if (value !== undefined) fields[key] = value;
  }
  return fields;
}

function buildAuditPayload(runId) {
  return {
    approved: true,
    reason: `QA ${runId} 审核通过并加入商品库`,
    exclusivePriceRemark: `QA ${runId} 专属价说明`,
    shippingInfo: '48 小时内发货，支持 TEST/mock 快递状态推进',
    sellingPoints: [`QA ${runId} 主卖点 A`, `QA ${runId} 主卖点 B`],
    promotionScript: `QA ${runId} 推广话术，可用于渠道复制链接后跟进达人`,
    supportsAds: true,
    rewardRemark: `QA ${runId} 阶梯奖励说明`,
    participationRequirements: '近 30 天有内容更新，账号状态正常',
    campaignTimeRemark: 'TEST/mock 验收窗口内有效',
    materialFiles: [`https://test.local/material/${encodeURIComponent(runId)}.png`],
    sampleThresholdSales: 30000,
    sampleThresholdLevel: 1,
    sampleThresholdRemark: '满足默认寄样标准后可申请'
  };
}

function evaluateExpectation(caseDef, result) {
  const expect = caseDef.expect || {};
  const businessOk = hasSuccessBusinessCode(result.body);
  const success = result.ok && businessOk;
  const businessCode = result.body?.code;
  if (expect.forbidden) {
    const forbidden = [401, 403].includes(result.status) || [401, 403].includes(Number(businessCode));
    return forbidden
      ? { pass: true, reason: '' }
      : { pass: false, reason: `expected forbidden but got HTTP ${result.status}, code=${businessCode ?? '-'}` };
  }
  if (expect.error) {
    const errored = !success || result.status >= 400 || (businessCode != null && Number(businessCode) !== 200);
    return errored
      ? { pass: true, reason: '' }
      : { pass: false, reason: 'expected business error but request succeeded' };
  }
  if (Array.isArray(expect.statuses) && !expect.statuses.includes(result.status) && !expect.statuses.includes(Number(businessCode))) {
    return { pass: false, reason: `expected status/code ${expect.statuses.join('|')} but got HTTP ${result.status}, code=${businessCode ?? '-'}` };
  }
  if (expect.ok && !success) {
    return { pass: false, reason: `expected success but got HTTP ${result.status}, code=${businessCode ?? '-'}: ${result.body?.msg || result.error || ''}` };
  }
  if (expect.field) {
    const value = getField(unwrapApiBody(result.body), expect.field);
    if (Object.prototype.hasOwnProperty.call(expect, 'equals') && value !== expect.equals) {
      return { pass: false, reason: `expected ${expect.field}=${expect.equals}, got ${value}` };
    }
    if (Array.isArray(expect.oneOf) && !expect.oneOf.includes(value)) {
      return { pass: false, reason: `expected ${expect.field} in ${expect.oneOf.join('|')}, got ${value}` };
    }
    if (expect.exists && (value === undefined || value === null || value === '')) {
      return { pass: false, reason: `expected ${expect.field} to exist` };
    }
  }
  return { pass: true, reason: '' };
}

function summarizeCaseResults(results) {
  const total = results.length;
  const pass = results.filter((item) => item.pass).length;
  const fail = total - pass;
  return {
    total,
    pass,
    fail,
    overallPass: fail === 0,
    failures: results
      .filter((item) => !item.pass)
      .map((item) => ({ name: item.name, failureReason: item.failureReason || item.reason || 'failed' }))
  };
}

async function fetchJson(url, options = {}) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
  const started = Date.now();
  try {
    const response = await fetch(url, { ...options, signal: controller.signal });
    const text = await response.text();
    let body = null;
    try {
      body = text ? JSON.parse(text) : null;
    } catch {
      body = { rawText: text.slice(0, 2000) };
    }
    return { url, status: response.status, ok: response.ok, durationMs: Date.now() - started, body };
  } finally {
    clearTimeout(timeout);
  }
}

function apiUrl(apiPath) {
  return `${API_BASE_URL}/api${apiPath.startsWith('/') ? apiPath : `/${apiPath}`}`;
}

async function apiRequest(method, apiPath, { token, body } = {}) {
  const url = apiUrl(apiPath);
  try {
    const result = await fetchJson(url, {
      method,
      body: body == null ? undefined : JSON.stringify(body),
      headers: {
        Accept: 'application/json',
        ...(body == null ? {} : { 'Content-Type': 'application/json' }),
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      }
    });
    return {
      ...result,
      method,
      path: apiPath,
      businessCode: result.body?.code
    };
  } catch (error) {
    return {
      method,
      path: apiPath,
      url,
      status: 0,
      ok: false,
      businessCode: undefined,
      durationMs: 0,
      body: null,
      error: error?.message || String(error)
    };
  }
}

function normalizeEnv(raw) {
  const data = raw?.data && typeof raw.data === 'object' ? raw.data : raw || {};
  const activeProfiles = []
    .concat(data.activeProfiles || data.activeProfile || data.profile || data.profiles || [])
    .filter(Boolean)
    .map((item) => String(item).toLowerCase());
  const environmentLabel = String(data.environmentLabel || data.environment || data.env || '').trim();
  const profile = String(data.profile || data.activeProfile || activeProfiles[0] || '').toLowerCase();
  const appTestEnabled = data.appTestEnabled === true || String(data.appTestEnabled).toLowerCase() === 'true';
  const douyinTestEnabled = data.douyinTestEnabled === true || String(data.douyinTestEnabled).toLowerCase() === 'true';
  const isRealPre = environmentLabel.toLowerCase() === 'real-pre' || profile === 'real-pre' || activeProfiles.includes('real-pre');
  const isTest = !isRealPre && (
    environmentLabel.toLowerCase() === 'test' ||
    profile === 'test' ||
    activeProfiles.includes('test') ||
    appTestEnabled ||
    douyinTestEnabled
  );
  return { environmentLabel, profile, activeProfiles, appTestEnabled, douyinTestEnabled, database: data.database || null, raw, isRealPre, isTest };
}

async function login(account) {
  const result = await apiRequest('POST', '/auth/login', {
    body: { username: account.username, password: account.password }
  });
  const data = unwrapApiBody(result.body);
  const token = data?.token || data?.accessToken;
  if (!result.ok || !token) {
    throw new Error(`login failed for ${account.username}: HTTP ${result.status}, code=${result.businessCode ?? '-'}`);
  }
  return { token, user: data };
}

async function ensureTestEnvironment(ctx) {
  const envResult = await apiRequest('GET', '/system/env');
  if (!envResult.ok) {
    throw new Error(`/api/system/env failed with HTTP ${envResult.status}`);
  }
  ctx.environment = normalizeEnv(envResult.body);
  if (!ctx.environment.isTest) {
    throw new Error(`Refusing to run outside TEST/mock environment: ${JSON.stringify(ctx.environment)}`);
  }
  const health = await apiRequest('GET', '/system/health');
  ctx.health = health.body;
  if (!health.ok || health.body?.status !== 'UP') {
    throw new Error(`/api/system/health is not UP: ${JSON.stringify(health.body || health.error)}`);
  }
}

async function seed(ctx) {
  const result = await apiRequest('POST', '/test/seed', { token: ctx.tokens.admin });
  if (!result.ok || !hasSuccessBusinessCode(result.body)) {
    throw new Error(`/api/test/seed failed: HTTP ${result.status}, code=${result.businessCode ?? '-'}`);
  }
  ctx.seed = unwrapApiBody(result.body) || {};
  return ctx.seed;
}

async function sampleByRequestNo(ctx, requestNo, token = ctx.tokens.admin) {
  const result = await apiRequest('GET', `/samples?page=1&size=20&keyword=${encodeURIComponent(requestNo)}`, { token });
  const records = extractRecords(result.body);
  const sample = records.find((item) => String(item.requestNo) === String(requestNo)) || records[0];
  if (!sample?.id) {
    throw new Error(`sample fixture not found by requestNo=${requestNo}`);
  }
  return sample;
}

function bodyWithData(result, data) {
  return {
    ...result,
    body: {
      code: result.body?.code ?? (result.ok ? 200 : result.status),
      msg: result.body?.msg || result.body?.message || 'synthetic',
      data
    },
    businessCode: result.body?.code ?? (result.ok ? 200 : result.status)
  };
}

async function runCase(ctx, caseDef, action) {
  let result;
  try {
    result = await action();
  } catch (error) {
    result = {
      method: caseDef.method || 'CUSTOM',
      path: caseDef.path || caseDef.name,
      url: null,
      status: 0,
      ok: false,
      businessCode: undefined,
      durationMs: 0,
      body: null,
      error: error?.message || String(error)
    };
  }
  const evaluation = evaluateExpectation(caseDef, result);
  const item = {
    id: caseDef.id,
    group: caseDef.group,
    name: caseDef.name,
    pass: evaluation.pass,
    requestResult: summarizeHttpResult(result),
    keyFields: pickKeyFields(result.body),
    failureReason: evaluation.pass ? '' : evaluation.reason
  };
  ctx.caseResults.push(item);
  console.log(`[${item.pass ? 'PASS' : 'FAIL'}] ${item.name}${item.failureReason ? ` - ${item.failureReason}` : ''}`);
  return item;
}

async function productVisibleToChannel(ctx, productId) {
  let lastResult = null;
  const records = [];
  for (let page = 1; page <= 10; page += 1) {
    const result = await apiRequest('GET', `/products?page=${page}&size=100`, { token: ctx.tokens.channelStaff });
    lastResult = result;
    records.push(...extractRecords(result.body));
    const total = Number(unwrapApiBody(result.body)?.total || 0);
    if (!extractRecords(result.body).length || records.length >= total) break;
  }
  const visible = records.some((item) => String(item.productId || item.product_id || item.product?.productId || '') === String(productId));
  return bodyWithData(lastResult || { ok: false, status: 0, body: null }, { visible, recordsCount: records.length, productId });
}

async function collectOrderById(ctx, orderId) {
  const result = await apiRequest('GET', `/orders?page=1&size=20&orderId=${encodeURIComponent(orderId)}`, { token: ctx.tokens.admin });
  const records = extractRecords(result.body);
  return bodyWithData(result, { orderId, recordsCount: records.length, ...(records[0] || {}) });
}

async function runBusinessStateFlow(ctx) {
  await seed(ctx);
  await runCase(ctx, {
    id: 'product-pending-to-approved',
    group: 'product',
    name: '商品状态流转：待审核 -> 已上架',
    expect: { ok: true, field: 'bizStatus', equals: 'APPROVED' }
  }, () => apiRequest('PUT', `/colonel/activities/${ACTIVITY_ID}/products/${PRODUCTS.pendingAudit}/audit-result`, {
    token: ctx.tokens.bizStaff,
    body: buildAuditPayload(ctx.qaRunId)
  }));

  await runCase(ctx, {
    id: 'product-approved-channel-visible',
    group: 'product',
    name: '商品可见性：已上架渠道可见',
    expect: { ok: true, field: 'visible', equals: true }
  }, () => productVisibleToChannel(ctx, PRODUCTS.pendingAudit));

  await seed(ctx);
  await runCase(ctx, {
    id: 'product-pending-to-rejected',
    group: 'product',
    name: '商品状态流转：待审核 -> 已拒绝',
    expect: { ok: true, field: 'bizStatus', equals: 'REJECTED' }
  }, () => apiRequest('PUT', `/colonel/activities/${ACTIVITY_ID}/products/${PRODUCTS.pendingAudit}/audit-result`, {
    token: ctx.tokens.bizStaff,
    body: { approved: false, reason: `QA ${ctx.qaRunId} 拒绝商品` }
  }));

  await runCase(ctx, {
    id: 'product-rejected-channel-hidden',
    group: 'product',
    name: '商品可见性：已拒绝渠道不可见',
    expect: { ok: true, field: 'visible', equals: false }
  }, () => productVisibleToChannel(ctx, PRODUCTS.pendingAudit));

  await seed(ctx);
  const pendingReview = await sampleByRequestNo(ctx, ctx.seed.pendingReviewSampleRequestNo);
  await runCase(ctx, {
    id: 'sample-pending-to-ship',
    group: 'sample',
    name: '寄样状态流转：待审核 -> 待发货',
    expect: { ok: true, field: 'status', equals: 'PENDING_SHIP' }
  }, () => apiRequest('PUT', `/samples/${pendingReview.id}/status`, {
    token: ctx.tokens.admin,
    body: { action: 'APPROVED', reason: `QA ${ctx.qaRunId} 寄样审核通过` }
  }));

  await runCase(ctx, {
    id: 'sample-ship-to-shipping',
    group: 'sample',
    name: '寄样状态流转：待发货 -> 快递中',
    expect: { ok: true, field: 'status', equals: 'SHIPPED' }
  }, () => apiRequest('PUT', `/samples/${pendingReview.id}/status`, {
    token: ctx.tokens.admin,
    body: { action: 'SHIPPING', trackingNo: `QA${Date.now()}`, shipperCode: 'SF', reason: `QA ${ctx.qaRunId} 发货` }
  }));

  await runCase(ctx, {
    id: 'sample-shipping-to-pending-task',
    group: 'sample',
    name: '寄样状态流转：快递中 -> 待交作业',
    expect: { ok: true, field: 'status', equals: 'PENDING_TASK' }
  }, () => apiRequest('PUT', `/samples/${pendingReview.id}/status`, {
    token: ctx.tokens.admin,
    body: { action: 'SIGNED', reason: `QA ${ctx.qaRunId} 签收` }
  }));

  await seed(ctx);
  await runCase(ctx, {
    id: 'sample-pending-task-to-finished',
    group: 'sample',
    name: '寄样状态流转：待交作业 -> 已完成（订单命中自动完成）',
    expect: { ok: true, field: 'sampleStatus', equals: 6 }
  }, () => apiRequest('POST', '/test/orders/generate-attributed', { token: ctx.tokens.admin }));

  await seed(ctx);
  const rejectCandidate = await sampleByRequestNo(ctx, ctx.seed.pendingReviewSampleRequestNo);
  await runCase(ctx, {
    id: 'sample-pending-to-rejected',
    group: 'sample',
    name: '寄样分支：待审核 -> 已拒绝',
    expect: { ok: true, field: 'status', equals: 'REJECTED' }
  }, () => apiRequest('PUT', `/samples/${rejectCandidate.id}/status`, {
    token: ctx.tokens.admin,
    body: { action: 'REJECTED', reason: `QA ${ctx.qaRunId} 拒绝寄样` }
  }));

  await seed(ctx);
  const closedSample = await sampleByRequestNo(ctx, ctx.seed.closedSampleRequestNo);
  await runCase(ctx, {
    id: 'sample-pending-task-to-closed',
    group: 'sample',
    name: '寄样分支：待交作业 -> 已关闭（系统自动关闭样本）',
    expect: { ok: true, field: 'status', equals: 'CLOSED' }
  }, () => apiRequest('GET', `/samples/${closedSample.id}`, { token: ctx.tokens.admin }));

  const rejectedFixture = await sampleByRequestNo(ctx, ctx.seed.rejectedSampleRequestNo);
  await runCase(ctx, {
    id: 'sample-reapply-after-rejected',
    group: 'sample',
    name: '寄样分支：已拒绝后重新申请样本存在',
    expect: { ok: true, field: 'recordsCount', exists: true }
  }, async () => {
    const result = await apiRequest('GET', `/samples?page=1&size=20&keyword=${encodeURIComponent(rejectedFixture.talentName || rejectedFixture.talentNickname || '达人F')}`, { token: ctx.tokens.admin });
    return bodyWithData(result, { recordsCount: extractRecords(result.body).length, rejectedRequestNo: rejectedFixture.requestNo });
  });

  await runCase(ctx, {
    id: 'order-attributed',
    group: 'order',
    name: '订单归因流转：已归因',
    expect: { ok: true, field: 'attributionStatus', equals: 'ATTRIBUTED' }
  }, () => apiRequest('POST', '/test/orders/generate-attributed', { token: ctx.tokens.admin }));

  await runCase(ctx, {
    id: 'order-no-pick-source',
    group: 'order',
    name: '订单归因流转：无 pick_source',
    expect: { ok: true, field: 'attributionRemark', equals: 'NO_PICK_SOURCE' }
  }, () => apiRequest('POST', '/test/orders/generate-no-pick-source', { token: ctx.tokens.admin }));

  await runCase(ctx, {
    id: 'order-mapping-missing',
    group: 'order',
    name: '订单归因流转：映射缺失',
    expect: { ok: true, field: 'attributionRemark', oneOf: ['MAPPING_NOT_FOUND', 'COLONEL_MAPPING_NOT_FOUND'] }
  }, () => apiRequest('POST', '/test/orders/generate-missing-mapping', { token: ctx.tokens.admin }));

  await runCase(ctx, {
    id: 'order-product-uncovered',
    group: 'order',
    name: '订单归因流转：商品未覆盖',
    expect: { ok: true, field: 'attributionRemark', equals: 'PRODUCT_NOT_FOUND' }
  }, () => collectOrderById(ctx, 'MOCK_AUDIT_PRODUCT_UNCOVERED'));

  await runCase(ctx, {
    id: 'order-mapping-time-unsafe',
    group: 'order',
    name: '订单归因流转：映射时间不安全',
    expect: { ok: true, field: 'unsafeBecauseCreatedAfterOrder', exists: true }
  }, () => apiRequest('POST', '/orders/replay-attribution', {
    token: ctx.tokens.admin,
    body: { reason: 'COLONEL_MAPPING_NOT_FOUND', limit: 50, dryRun: true }
  }));

  await runCase(ctx, {
    id: 'order-ambiguous-mapping',
    group: 'order',
    name: '订单归因流转：歧义映射诊断位',
    expect: { ok: true, field: 'ambiguousMappingCount', exists: true }
  }, () => apiRequest('GET', '/dashboard/summary', { token: ctx.tokens.admin }));

  await runCase(ctx, {
    id: 'order-replay-attribution',
    group: 'order',
    name: '订单归因流转：重放归因 dry-run',
    expect: { ok: true, field: 'dryRun', equals: true }
  }, () => apiRequest('POST', '/orders/replay-attribution', {
    token: ctx.tokens.admin,
    body: { reason: 'MAPPING_NOT_FOUND', limit: 20, dryRun: true }
  }));

  await seed(ctx);
  const rejectedSample = await sampleByRequestNo(ctx, ctx.seed.rejectedSampleRequestNo);
  await runCase(ctx, {
    id: 'illegal-rejected-cannot-ship',
    group: 'illegal',
    name: '非法流转：已拒绝不能发货',
    expect: { error: true }
  }, () => apiRequest('PUT', `/samples/${rejectedSample.id}/status`, {
    token: ctx.tokens.opsStaff,
    body: { action: 'SHIPPING', trackingNo: `QA${Date.now()}`, reason: 'should be rejected' }
  }));

  const finishedSample = await sampleByRequestNo(ctx, ctx.seed.orderSampleRequestNo);
  await runCase(ctx, {
    id: 'illegal-finished-cannot-review-again',
    group: 'illegal',
    name: '非法流转：已完成不能再次审核',
    expect: { error: true }
  }, () => apiRequest('PUT', `/samples/${finishedSample.id}/status`, {
    token: ctx.tokens.bizStaff,
    body: { action: 'APPROVED', reason: 'should be rejected' }
  }));

  const pendingSample = await sampleByRequestNo(ctx, ctx.seed.pendingReviewSampleRequestNo);
  await runCase(ctx, {
    id: 'illegal-channel-cannot-audit-sample',
    group: 'illegal',
    name: '非法流转：渠道不能审核寄样',
    expect: { forbidden: true }
  }, () => apiRequest('PUT', `/samples/${pendingSample.id}/status`, {
    token: ctx.tokens.channelStaff,
    body: { action: 'APPROVED', reason: 'channel should not audit' }
  }));

  await runCase(ctx, {
    id: 'illegal-ops-cannot-audit-product',
    group: 'illegal',
    name: '非法流转：运营不能审核商品',
    expect: { forbidden: true }
  }, () => apiRequest('PUT', `/colonel/activities/${ACTIVITY_ID}/products/${PRODUCTS.pendingAudit}/audit-result`, {
    token: ctx.tokens.opsStaff,
    body: { approved: false, reason: 'ops should not audit product' }
  }));
}

function buildReport(summary) {
  const lines = [];
  lines.push('# TEST/mock 主流程状态流转回归报告');
  lines.push('');
  lines.push(`- 脚本：${SCRIPT_NAME}`);
  lines.push(`- 后端：${summary.apiBaseUrl}`);
  lines.push(`- 环境：${summary.environment?.environmentLabel || '(unknown)'}`);
  lines.push(`- 总体结论：**${summary.overallPass ? 'PASS' : 'FAIL'}**`);
  lines.push(`- 用例：${summary.pass}/${summary.total} PASS`);
  lines.push('');
  for (const group of ['product', 'sample', 'order', 'illegal']) {
    lines.push(`## ${group}`);
    const cases = summary.caseResults.filter((item) => item.group === group);
    if (!cases.length) lines.push('- 无。');
    for (const item of cases) {
      lines.push(`- ${item.pass ? 'PASS' : 'FAIL'} ${item.name}`);
      lines.push(`  - 请求：${item.requestResult.method} ${item.requestResult.path} -> HTTP ${item.requestResult.status}, code=${item.requestResult.businessCode ?? '-'}`);
      lines.push(`  - 关键字段：\`${JSON.stringify(item.keyFields)}\``);
      if (item.failureReason) lines.push(`  - 失败原因：${item.failureReason}`);
    }
    lines.push('');
  }
  if (summary.notes.length) {
    lines.push('## Notes');
    for (const note of summary.notes) lines.push(`- ${note}`);
  }
  return `${lines.join('\n')}\n`;
}

function writeArtifacts(ctx, summary) {
  fs.writeFileSync(path.join(ctx.outDir, 'summary.json'), JSON.stringify(summary, null, 2));
  fs.writeFileSync(path.join(ctx.outDir, 'report.md'), buildReport(summary));
}

function createContext(outDir) {
  return {
    qaRunId: process.env.QA_RUN_ID || `QA_STATE_${timestamp()}`,
    outDir,
    environment: null,
    health: null,
    seed: {},
    tokens: {},
    caseResults: [],
    notes: []
  };
}

async function main() {
  const outDir = process.argv[2] || path.join(OUT_ROOT, `${SCRIPT_NAME}-${timestamp()}`);
  ensureDir(outDir);
  const ctx = createContext(outDir);
  const startedAt = new Date().toISOString();
  try {
    await ensureTestEnvironment(ctx);
    ctx.tokens.admin = (await login(ACCOUNTS.admin)).token;
    ctx.tokens.bizLeader = (await login(ACCOUNTS.bizLeader)).token;
    ctx.tokens.bizStaff = (await login(ACCOUNTS.bizStaff)).token;
    ctx.tokens.channelStaff = (await login(ACCOUNTS.channelStaff)).token;
    ctx.tokens.opsStaff = (await login(ACCOUNTS.opsStaff)).token;
    await runBusinessStateFlow(ctx);
  } catch (error) {
    ctx.notes.push(error?.stack || String(error));
  }
  const caseSummary = summarizeCaseResults(ctx.caseResults);
  const summary = {
    name: SCRIPT_NAME,
    qaRunId: ctx.qaRunId,
    apiBaseUrl: API_BASE_URL,
    outputDir: ctx.outDir,
    startedAt,
    finishedAt: new Date().toISOString(),
    environment: ctx.environment,
    health: ctx.health,
    ...caseSummary,
    overallPass: ctx.environment?.isTest === true && caseSummary.overallPass && ctx.notes.length === 0,
    caseResults: ctx.caseResults,
    notes: ctx.notes
  };
  writeArtifacts(ctx, summary);
  console.log(`Business state flow regression output: ${ctx.outDir}`);
  if (!summary.overallPass) process.exitCode = 1;
}

if (require.main === module) {
  main().catch((error) => {
    console.error(error?.stack || String(error));
    process.exitCode = 1;
  });
}

module.exports = {
  STATUS_LABELS,
  buildAuditPayload,
  evaluateExpectation,
  summarizeCaseResults,
  pickKeyFields
};

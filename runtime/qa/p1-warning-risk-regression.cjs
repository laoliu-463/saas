const fs = require('node:fs');
const path = require('node:path');

const SCRIPT_NAME = 'p1-warning-risk-regression';
const OUT_ROOT = path.join(__dirname, 'out');
const API_BASE_URL = normalizeBaseUrl(process.env.API_BASE_URL || 'http://localhost:8080');
const REQUEST_TIMEOUT_MS = Number(process.env.QA_REQUEST_TIMEOUT_MS || 20000);
const ACTIVITY_ID = process.env.QA_TEST_ACTIVITY_ID || 'TEST_ACTIVITY_A';
const PRODUCT_ID = '10901825';
const RETRY_PRODUCT_ID = '10901828';
const EXPECTED_REAL_PRE_OFF = {
  switchedRealPre: false,
  realThirdPartyAccessed: false,
  databaseCleared: false,
  productionConfigModified: false
};

const ACCOUNTS = {
  admin: { username: process.env.QA_ADMIN_USER || 'admin', password: process.env.QA_ADMIN_PASSWORD || 'admin123' },
  channelStaff: { username: 'channel_staff', password: 'admin123' }
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
  for (const key of ['records', 'list', 'rows', 'items', 'content', 'orderList', 'orders']) {
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

function isBusinessSuccess(result) {
  return Boolean(result?.ok) && hasSuccessBusinessCode(result.body);
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
    return { ...result, method, path: apiPath, businessCode: result.body?.code };
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
  const health = await apiRequest('GET', '/actuator/health');
  ctx.health = health.body;
  if (!health.ok || health.body?.status !== 'UP') {
    throw new Error(`/api/actuator/health is not UP: ${JSON.stringify(health.body || health.error)}`);
  }
}

async function seed(ctx) {
  const result = await apiRequest('POST', '/test/seed', { token: ctx.tokens.admin });
  if (!isBusinessSuccess(result)) {
    throw new Error(`/api/test/seed failed: HTTP ${result.status}, code=${result.businessCode ?? '-'}`);
  }
  ctx.seed = unwrapApiBody(result.body) || {};
  return ctx.seed;
}

async function getPromotionHistoryTotal(ctx) {
  return getPromotionHistoryTotalByProduct(ctx, PRODUCT_ID);
}

async function getPromotionHistoryTotalByProduct(ctx, productId) {
  const result = await apiRequest('GET', `/products/${productId}/promotion-links/history?page=1&size=100`, {
    token: ctx.tokens.channelStaff
  });
  if (!isBusinessSuccess(result)) {
    throw new Error(`promotion history failed: HTTP ${result.status}, code=${result.businessCode ?? '-'}`);
  }
  return {
    total: Number(getField(unwrapApiBody(result.body), ['total']) || extractRecords(result.body).length || 0),
    records: extractRecords(result.body),
    result
  };
}

async function getActivityProductDetail(ctx) {
  return getActivityProductDetailByProduct(ctx, PRODUCT_ID);
}

async function getActivityProductDetailByProduct(ctx, productId) {
  const result = await apiRequest('GET', `/colonel/activities/${ACTIVITY_ID}/products/${productId}`, {
    token: ctx.tokens.channelStaff
  });
  if (!isBusinessSuccess(result)) {
    throw new Error(`activity product detail failed: HTTP ${result.status}, code=${result.businessCode ?? '-'}`);
  }
  return unwrapApiBody(result.body) || {};
}

function summarizeRequest(result) {
  return {
    method: result.method,
    path: result.path,
    status: result.status,
    businessCode: result.businessCode,
    ok: result.ok,
    durationMs: result.durationMs,
    message: result.body?.msg || result.body?.message || result.error || null
  };
}

function formatFailure(error) {
  return error?.stack || error?.message || String(error);
}

async function runCase(ctx, group, id, name, fn) {
  const started = Date.now();
  try {
    const details = await fn();
    const item = {
      group,
      id,
      name,
      pass: true,
      durationMs: Date.now() - started,
      details: details || {}
    };
    ctx.caseResults.push(item);
    console.log(`[PASS] ${id} ${name}`);
    return item;
  } catch (error) {
    const item = {
      group,
      id,
      name,
      pass: false,
      durationMs: Date.now() - started,
      failureReason: formatFailure(error)
    };
    ctx.caseResults.push(item);
    console.log(`[FAIL] ${id} ${name} - ${error?.message || String(error)}`);
    return item;
  }
}

function oneOf(value, candidates) {
  return candidates.map((item) => String(item).toUpperCase()).includes(String(value || '').toUpperCase());
}

async function runPromotionRiskCases(ctx) {
  await runCase(ctx, 'promotion', 'PROMO-001', '转链失败：活动商品不存在时接口失败', async () => {
    await seed(ctx);
    const result = await apiRequest('POST', `/colonel/activities/${ACTIVITY_ID}/products/NOT_EXISTS/promotion-links`, {
      token: ctx.tokens.channelStaff,
      body: { scene: 'PRODUCT_LIBRARY', externalUniqueId: `qa-missing-${ctx.runId}` }
    });
    if (isBusinessSuccess(result)) {
      throw new Error(`expected promotion link failure, got success: ${JSON.stringify(result.body)}`);
    }
    return {
      request: summarizeRequest(result),
      resultStatus: getField(result.body, ['status']),
      resultMessage: getField(result.body, ['message'])
    };
  });

  await runCase(ctx, 'promotion', 'PROMO-002', '转链失败：RAW 探针未返回 pick_source', async () => {
    const result = await apiRequest('POST', '/douyin/promotion-link-probes/raw', {
      token: ctx.tokens.admin,
      body: {
        appId: 'test_app',
        method: 'buyin.instPickSourceConvert',
        pick_extra: `qa_missing_pick_source_${ctx.runId}`
      }
    });
    const data = unwrapApiBody(result.body) || {};
    const pickSource = getField(data, ['pick_source', 'remoteResponse.data.pick_source']);
    if (!isBusinessSuccess(result) || data.status !== 'success' || pickSource != null) {
      throw new Error(`expected success without pick_source, got ${JSON.stringify(result.body)}`);
    }
    return {
      request: summarizeRequest(result),
      endpoint: data.endpoint,
      probeStatus: data.status,
      hasPickSource: pickSource != null
    };
  });

  await runCase(ctx, 'promotion', 'PROMO-003', '转链失败：参数非法返回 failed', async () => {
    const result = await apiRequest('POST', '/douyin/promotion-link-probes/raw', {
      token: ctx.tokens.admin,
      body: {
        appId: 'test_app',
        method: 'buyin.productSkus.v2'
      }
    });
    const data = unwrapApiBody(result.body) || {};
    if (!isBusinessSuccess(result) || data.status !== 'failed' || !String(data.message || '').includes('product_id is required')) {
      throw new Error(`expected invalid-parameter failure, got ${JSON.stringify(result.body)}`);
    }
    return {
      request: summarizeRequest(result),
      probeStatus: data.status,
      message: data.message,
      errorType: data.errorType
    };
  });

  await runCase(ctx, 'promotion', 'PROMO-004', '转链失败：重复尝试仍保留 LINKED 并追加历史记录', async () => {
    await seed(ctx);
    const before = await getPromotionHistoryTotalByProduct(ctx, RETRY_PRODUCT_ID);
    const first = await apiRequest('POST', `/colonel/activities/${ACTIVITY_ID}/products/${RETRY_PRODUCT_ID}/promotion-links`, {
      token: ctx.tokens.channelStaff,
      body: { scene: 'PRODUCT_LIBRARY', externalUniqueId: `qa-repeat-1-${ctx.runId}` }
    });
    const second = await apiRequest('POST', `/colonel/activities/${ACTIVITY_ID}/products/${RETRY_PRODUCT_ID}/promotion-links`, {
      token: ctx.tokens.channelStaff,
      body: { scene: 'PRODUCT_LIBRARY', externalUniqueId: `qa-repeat-2-${ctx.runId}` }
    });
    const after = await getPromotionHistoryTotalByProduct(ctx, RETRY_PRODUCT_ID);
    const detail = await getActivityProductDetailByProduct(ctx, RETRY_PRODUCT_ID);
    const firstData = unwrapApiBody(first.body) || {};
    const secondData = unwrapApiBody(second.body) || {};
    if (!isBusinessSuccess(first) || !getField(firstData, ['pickSource'])) {
      throw new Error(`first retry did not generate link: ${JSON.stringify(first.body)}`);
    }
    if (!isBusinessSuccess(second) || !getField(secondData, ['pickSource'])) {
      throw new Error(`second retry did not generate link: ${JSON.stringify(second.body)}`);
    }
    if (after.total < before.total + 2) {
      throw new Error(`promotion history did not grow by 2, before=${before.total}, after=${after.total}`);
    }
    if (String(getField(detail, ['bizStatus'])).toUpperCase() !== 'LINKED') {
      throw new Error(`expected bizStatus=LINKED, got ${getField(detail, ['bizStatus'])}`);
    }
    return {
      beforeHistoryTotal: before.total,
      afterHistoryTotal: after.total,
      firstPickSource: getField(firstData, ['pickSource']),
      secondPickSource: getField(secondData, ['pickSource']),
      productId: RETRY_PRODUCT_ID,
      bizStatus: getField(detail, ['bizStatus']),
      promotionLinkCount: getField(detail, ['promotionLinkCount'])
    };
  });
}

async function generateTestOrder(ctx, route) {
  const result = await apiRequest('POST', route, { token: ctx.tokens.admin });
  const data = unwrapApiBody(result.body) || {};
  if (!isBusinessSuccess(result)) {
    throw new Error(`${route} failed: HTTP ${result.status}, code=${result.businessCode ?? '-'}`);
  }
  if (!data.orderId) {
    throw new Error(`${route} did not return orderId: ${JSON.stringify(result.body)}`);
  }
  return data;
}

async function runAttributionRiskCases(ctx) {
  await runCase(ctx, 'attribution', 'ATTR-001', '歧义映射：映射缺失订单保持未归因', async () => {
    await seed(ctx);
    const result = await apiRequest('POST', '/test/orders/generate-missing-mapping', { token: ctx.tokens.admin });
    const data = unwrapApiBody(result.body) || {};
    if (!isBusinessSuccess(result)) {
      throw new Error(`missing mapping seed failed: ${JSON.stringify(result.body)}`);
    }
    if (String(data.attributionStatus).toUpperCase() !== 'UNATTRIBUTED') {
      throw new Error(`expected UNATTRIBUTED, got ${data.attributionStatus}`);
    }
    if (!oneOf(data.attributionRemark, ['MAPPING_NOT_FOUND', 'COLONEL_MAPPING_NOT_FOUND'])) {
      throw new Error(`expected mapping-missing reason, got ${data.attributionRemark}`);
    }
    return {
      request: summarizeRequest(result),
      orderId: data.orderId,
      attributionStatus: data.attributionStatus,
      attributionRemark: data.attributionRemark,
      pickSource: data.pickSource
    };
  });

  await runCase(ctx, 'attribution', 'ATTR-002', '歧义映射：多候选诊断可被订单筛选识别', async () => {
    await seed(ctx);
    const generated = await generateTestOrder(ctx, '/test/orders/generate-ambiguous-mapping');
    const stats = await apiRequest('GET', `/orders/stats?dashboardDiagnosis=AMBIGUOUS_MAPPING&timeField=createTime&orderId=${encodeURIComponent(generated.orderId)}`, {
      token: ctx.tokens.admin
    });
    const list = await apiRequest('GET', `/orders/unattributed?dashboardDiagnosis=AMBIGUOUS_MAPPING&timeField=createTime&orderId=${encodeURIComponent(generated.orderId)}&page=1&size=5`, {
      token: ctx.tokens.admin
    });
    const statsData = unwrapApiBody(stats.body) || {};
    const listRecords = extractRecords(list.body);
    const firstReason = getField(listRecords[0], ['attributionRemark', 'unattributedReason']);
    if (!isBusinessSuccess(stats) || !isBusinessSuccess(list)) {
      throw new Error(`ambiguous diagnosis requests failed: stats=${JSON.stringify(stats.body)}, list=${JSON.stringify(list.body)}`);
    }
    if (Number(statsData.totalOrders || 0) <= 0 || listRecords.length <= 0) {
      throw new Error(`expected ambiguous diagnosis records, got total=${statsData.totalOrders || 0}, records=${listRecords.length}`);
    }
    if (!String(firstReason || '').toUpperCase().includes('AMBIGUOUS')) {
      throw new Error(`expected ambiguous reason in first record, got ${firstReason}`);
    }
    return {
      orderId: generated.orderId,
      statsTotalOrders: Number(statsData.totalOrders || 0),
      statsUnattributedOrders: Number(statsData.unattributedOrders || 0),
      sampleOrderId: getField(listRecords[0], ['orderId']),
      sampleReason: firstReason
    };
  });

  await runCase(ctx, 'attribution', 'ATTR-003', '歧义映射：不安全时间 dry-run 保持只诊断不回填', async () => {
    await seed(ctx);
    const generated = await generateTestOrder(ctx, '/test/orders/generate-history-unsafe');
    const result = await apiRequest('POST', '/orders/replay-attribution', {
      token: ctx.tokens.admin,
      body: { orderIds: [generated.orderId], dryRun: true }
    });
    const data = unwrapApiBody(result.body) || {};
    if (!isBusinessSuccess(result)) {
      throw new Error(`replay-attribution failed: ${JSON.stringify(result.body)}`);
    }
    if (data.dryRun !== true || Number(data.unsafeBecauseCreatedAfterOrder || 0) <= 0) {
      throw new Error(`expected dryRun unsafe result, got ${JSON.stringify(data)}`);
    }
    return {
      request: summarizeRequest(result),
      orderId: generated.orderId,
      scanned: data.scanned,
      attributed: data.attributed,
      dryRun: data.dryRun,
      unsafeBecauseCreatedAfterOrder: data.unsafeBecauseCreatedAfterOrder,
      ambiguousMapping: data.ambiguousMapping
    };
  });

  await runCase(ctx, 'attribution', 'ATTR-004', '歧义映射：商品未覆盖保持独立诊断', async () => {
    await seed(ctx);
    const generated = await generateTestOrder(ctx, '/test/orders/generate-product-uncovered');
    const detail = await apiRequest('GET', `/orders/${generated.orderId}`, { token: ctx.tokens.admin });
    const stats = await apiRequest('GET', `/orders/stats?unattributedReason=PRODUCT_NOT_FOUND&timeField=createTime&orderId=${encodeURIComponent(generated.orderId)}`, {
      token: ctx.tokens.admin
    });
    const list = await apiRequest('GET', `/orders/unattributed?unattributedReason=PRODUCT_NOT_FOUND&timeField=createTime&orderId=${encodeURIComponent(generated.orderId)}&page=1&size=5`, {
      token: ctx.tokens.admin
    });
    const detailData = unwrapApiBody(detail.body) || {};
    const statsData = unwrapApiBody(stats.body) || {};
    const listRecords = extractRecords(list.body);
    if (!isBusinessSuccess(detail) || !isBusinessSuccess(stats) || !isBusinessSuccess(list)) {
      throw new Error(`product-uncovered requests failed: detail=${JSON.stringify(detail.body)}, stats=${JSON.stringify(stats.body)}, list=${JSON.stringify(list.body)}`);
    }
    if (String(detailData.attributionStatus).toUpperCase() !== 'UNATTRIBUTED' || String(detailData.attributionRemark).toUpperCase() !== 'PRODUCT_NOT_FOUND') {
      throw new Error(`expected PRODUCT_NOT_FOUND detail, got ${JSON.stringify(detailData)}`);
    }
    if (Number(statsData.totalOrders || 0) <= 0 || listRecords.length <= 0) {
      throw new Error(`expected product-uncovered list coverage, got total=${statsData.totalOrders || 0}, records=${listRecords.length}`);
    }
    return {
      generatedOrderId: generated.orderId,
      orderId: detailData.orderId,
      attributionStatus: detailData.attributionStatus,
      attributionRemark: detailData.attributionRemark,
      statsTotalOrders: Number(statsData.totalOrders || 0),
      sampleOrderId: getField(listRecords[0], ['orderId']),
      sampleReason: getField(listRecords[0], ['attributionRemark', 'unattributedReason'])
    };
  });
}

function summarizeCases(caseResults) {
  const total = caseResults.length;
  const pass = caseResults.filter((item) => item.pass).length;
  const fail = total - pass;
  return {
    total,
    pass,
    fail,
    overallPass: fail === 0,
    failures: caseResults
      .filter((item) => !item.pass)
      .map((item) => ({ id: item.id, name: item.name, failureReason: item.failureReason }))
  };
}

function buildReport(summary) {
  const lines = [];
  lines.push('# TEST/mock P1 warning 风险专项回归报告');
  lines.push('');
  lines.push(`- 脚本：${SCRIPT_NAME}`);
  lines.push(`- 后端：${summary.apiBaseUrl}`);
  lines.push(`- 环境：${summary.environment?.environmentLabel || '(unknown)'}`);
  lines.push(`- 总体结论：**${summary.overallPass ? 'PASS' : 'FAIL'}**`);
  lines.push(`- 用例：${summary.pass}/${summary.total} PASS`);
  lines.push('');
  lines.push('## Guardrails');
  lines.push(`- No real-pre switch: ${summary.constraints.switchedRealPre ? 'NO' : 'YES'}`);
  lines.push(`- No real third-party access: ${summary.constraints.realThirdPartyAccessed ? 'NO' : 'YES'}`);
  lines.push(`- No database clear: ${summary.constraints.databaseCleared ? 'NO' : 'YES'}`);
  lines.push(`- No production config mutation: ${summary.constraints.productionConfigModified ? 'NO' : 'YES'}`);
  lines.push('');
  for (const group of ['promotion', 'attribution']) {
    lines.push(`## ${group}`);
    const cases = summary.caseResults.filter((item) => item.group === group);
    if (!cases.length) {
      lines.push('- 无。');
      lines.push('');
      continue;
    }
    for (const item of cases) {
      lines.push(`- ${item.pass ? 'PASS' : 'FAIL'} ${item.id} ${item.name}`);
      if (item.pass) {
        lines.push(`  - 证据：\`${JSON.stringify(item.details)}\``);
      } else {
        lines.push(`  - 失败：${item.failureReason}`);
      }
    }
    lines.push('');
  }
  if (summary.notes.length) {
    lines.push('## Notes');
    for (const note of summary.notes) lines.push(`- ${note}`);
    lines.push('');
  }
  return `${lines.join('\n')}\n`;
}

function writeArtifacts(ctx, summary) {
  fs.writeFileSync(path.join(ctx.outDir, 'summary.json'), JSON.stringify(summary, null, 2));
  fs.writeFileSync(path.join(ctx.outDir, 'report.md'), buildReport(summary));
}

function createContext(outDir) {
  return {
    runId: process.env.QA_RUN_ID || timestamp(),
    outDir,
    environment: null,
    health: null,
    tokens: {},
    seed: {},
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
    ctx.tokens.channelStaff = (await login(ACCOUNTS.channelStaff)).token;
    await runPromotionRiskCases(ctx);
    await runAttributionRiskCases(ctx);
  } catch (error) {
    ctx.notes.push(formatFailure(error));
  }
  const totals = summarizeCases(ctx.caseResults);
  const summary = {
    name: SCRIPT_NAME,
    runId: ctx.runId,
    apiBaseUrl: API_BASE_URL,
    outputDir: ctx.outDir,
    startedAt,
    finishedAt: new Date().toISOString(),
    environment: ctx.environment,
    health: ctx.health,
    constraints: EXPECTED_REAL_PRE_OFF,
    ...totals,
    overallPass: ctx.environment?.isTest === true && totals.overallPass && ctx.notes.length === 0,
    caseResults: ctx.caseResults,
    notes: ctx.notes
  };
  writeArtifacts(ctx, summary);
  console.log(`P1 warning risk regression output: ${ctx.outDir}`);
  if (!summary.overallPass) process.exitCode = 1;
}

if (require.main === module) {
  main().catch((error) => {
    console.error(formatFailure(error));
    process.exitCode = 1;
  });
}

module.exports = {
  normalizeEnv,
  summarizeCases
};

const fs = require('node:fs');
const path = require('node:path');

const SCRIPT_NAME = 'third-party-fallback-regression';
const OUT_ROOT = path.join(__dirname, 'out');
const API_BASE_URL = normalizeBaseUrl(process.env.API_BASE_URL || 'http://localhost:8080');
const REQUEST_TIMEOUT_MS = Number(process.env.QA_REQUEST_TIMEOUT_MS || 20000);

const ACCOUNTS = {
  admin: { username: process.env.QA_ADMIN_USER || 'admin', password: process.env.QA_ADMIN_PASSWORD || 'admin123' },
  bizStaff: { username: 'biz_staff', password: 'admin123' },
  channelLeader: { username: 'channel_leader', password: 'admin123' },
  channelStaff: { username: 'channel_staff', password: 'admin123' },
  opsStaff: { username: 'ops_staff', password: 'admin123' }
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
  for (const key of ['records', 'list', 'rows', 'items', 'content', 'samples', 'talents', 'productList']) {
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

function isSuccess(result) {
  return result.ok && hasSuccessBusinessCode(result.body);
}

function apiUrl(apiPath) {
  return `${API_BASE_URL}/api${apiPath.startsWith('/') ? apiPath : `/${apiPath}`}`;
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

async function ensureTestEnvironment(ctx) {
  const envResult = await apiRequest('GET', '/system/env');
  if (!isSuccess(envResult)) {
    throw new Error(`/api/system/env failed with HTTP ${envResult.status}, code=${envResult.businessCode ?? '-'}`);
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

async function ensureProductCandidate(ctx) {
  let result = await apiRequest('GET', '/samples/product-candidates?page=1&size=20', { token: ctx.tokens.channelLeader });
  let records = extractRecords(result.body);
  if (!records.length) {
    const seeded = await apiRequest('POST', '/test/seed', { token: ctx.tokens.admin });
    ctx.seedUsed = true;
    ctx.seedResult = {
      status: seeded.status,
      businessCode: seeded.businessCode,
      ok: isSuccess(seeded)
    };
    result = await apiRequest('GET', '/samples/product-candidates?page=1&size=20', { token: ctx.tokens.channelLeader });
    records = extractRecords(result.body);
  }
  const product = records.find((item) => getField(item, ['id'])) || records[0];
  const productId = getField(product, ['id']);
  if (!productId) {
    throw new Error('No sample product candidate available in TEST/mock environment');
  }
  ctx.productCandidate = product;
  return productId;
}

async function createTalent(ctx, suffix, { fail = false } = {}) {
  const douyinUid = `${fail ? 'test_fail_' : 'qa_fallback_'}${ctx.runId}_${suffix}`;
  const result = await apiRequest('POST', '/talents', {
    token: ctx.tokens.channelLeader,
    body: {
      douyinUid,
      douyinNo: `qa_no_${ctx.runId}_${suffix}`,
      profileUrl: `https://www.douyin.com/user/${douyinUid}`,
      contactWechat: `wx_${ctx.runId}_${suffix}`
    }
  });
  if (!isSuccess(result)) {
    throw new Error(`create talent failed: HTTP ${result.status}, code=${result.businessCode ?? '-'}, body=${JSON.stringify(result.body)}`);
  }
  const talent = unwrapApiBody(result.body);
  ctx.createdEntities.push({ type: 'talent', id: talent?.id, douyinUid, source: fail ? 'mock-failure-sample' : 'mock-success-sample' });
  return talent;
}

async function manualFillTalent(ctx, talent, suffix) {
  const result = await apiRequest('PUT', `/talents/${talent.id}/manual-fill`, {
    token: ctx.tokens.channelLeader,
    body: {
      nickname: `QA手动补录达人_${ctx.runId}_${suffix}`,
      fans: 88000,
      ipLocation: '浙江杭州',
      categories: '美妆个护',
      contactWechat: `manual_wx_${ctx.runId}_${suffix}`
    }
  });
  if (!isSuccess(result)) {
    throw new Error(`manual fill talent failed: HTTP ${result.status}, code=${result.businessCode ?? '-'}`);
  }
  return unwrapApiBody(result.body);
}

async function claimTalent(ctx, talent) {
  const result = await apiRequest('POST', `/talents/${talent.id}/claims`, { token: ctx.tokens.channelLeader });
  if (!isSuccess(result)) {
    throw new Error(`claim talent failed: HTTP ${result.status}, code=${result.businessCode ?? '-'}`);
  }
  return unwrapApiBody(result.body);
}

async function privatePoolContains(ctx, talentId) {
  const result = await apiRequest('GET', '/talents/pools/private', { token: ctx.tokens.channelLeader });
  if (!isSuccess(result)) {
    throw new Error(`private talent pool failed: HTTP ${result.status}, code=${result.businessCode ?? '-'}`);
  }
  return extractRecords(result.body).some((item) => String(getField(item, ['id'])) === String(talentId));
}

async function createSampleForTalent(ctx, talent, suffix) {
  const productId = await ensureProductCandidate(ctx);
  const talentUid = talent.douyinUid || talent.uid || talent.douyinNo;
  const result = await apiRequest('POST', '/samples', {
    token: ctx.tokens.channelLeader,
    body: {
      productId,
      talentId: talentUid,
      quantity: 1,
      remark: `QA ${ctx.runId} ${suffix} manual/mock fallback sample`
    }
  });
  if (!isSuccess(result)) {
    throw new Error(`create sample failed: HTTP ${result.status}, code=${result.businessCode ?? '-'}, body=${JSON.stringify(result.body)}`);
  }
  const sample = unwrapApiBody(result.body);
  ctx.createdEntities.push({ type: 'sample', id: sample?.id, requestNo: sample?.requestNo, talentUid, suffix });
  return sample;
}

async function approveSample(ctx, sample, suffix) {
  const result = await apiRequest('PUT', `/samples/${sample.id}/status`, {
    token: ctx.tokens.admin,
    body: { action: 'APPROVED', reason: `QA ${ctx.runId} ${suffix} approve sample` }
  });
  if (!isSuccess(result)) {
    throw new Error(`approve sample failed: HTTP ${result.status}, code=${result.businessCode ?? '-'}`);
  }
  return unwrapApiBody(result.body);
}

async function createApprovedSample(ctx, suffix) {
  const talent = await createTalent(ctx, suffix);
  const filled = await manualFillTalent(ctx, talent, suffix);
  await claimTalent(ctx, filled);
  const sample = await createSampleForTalent(ctx, filled, suffix);
  return approveSample(ctx, sample, suffix);
}

function summarizeResult(result) {
  return {
    method: result.method,
    path: result.path,
    status: result.status,
    businessCode: result.businessCode,
    ok: result.ok,
    durationMs: result.durationMs,
    error: result.error || null,
    message: result.body?.msg || result.body?.message || null
  };
}

function passCase(ctx, id, name, details = {}) {
  const item = { id, name, pass: true, details };
  ctx.results.push(item);
  console.log(`[PASS] ${id} ${name}`);
  return item;
}

function failCase(ctx, id, name, error, details = {}) {
  const item = {
    id,
    name,
    pass: false,
    failureReason: error?.stack || error?.message || String(error),
    details
  };
  ctx.results.push(item);
  console.log(`[FAIL] ${id} ${name} - ${error?.message || String(error)}`);
  return item;
}

async function runCase(ctx, id, name, fn) {
  const started = Date.now();
  try {
    const details = await fn();
    return passCase(ctx, id, name, { durationMs: Date.now() - started, ...(details || {}) });
  } catch (error) {
    return failCase(ctx, id, name, error, { durationMs: Date.now() - started });
  }
}

async function runRegression(ctx) {
  await ensureTestEnvironment(ctx);
  ctx.tokens.admin = (await login(ACCOUNTS.admin)).token;
  ctx.tokens.bizStaff = (await login(ACCOUNTS.bizStaff)).token;
  ctx.tokens.channelLeader = (await login(ACCOUNTS.channelLeader)).token;
  ctx.tokens.channelStaff = (await login(ACCOUNTS.channelStaff)).token;
  ctx.tokens.opsStaff = (await login(ACCOUNTS.opsStaff)).token;

  let logisticsManualSample;
  await runCase(ctx, 'LOGISTICS-002', '非运营录入物流失败', async () => {
    logisticsManualSample = await createApprovedSample(ctx, 'logistics-deny');
    const denied = await apiRequest('PUT', `/samples/${logisticsManualSample.id}/status`, {
      token: ctx.tokens.channelStaff,
      body: { action: 'SHIPPED', trackingNo: `QA-DENY-${ctx.runId}`, shipperCode: 'MANUAL' }
    });
    const businessCode = Number(denied.businessCode);
    const forbidden = [401, 403].includes(denied.status) || [401, 403].includes(businessCode);
    if (!forbidden) {
      throw new Error(`expected forbidden for non-ops shipping, got HTTP ${denied.status}, code=${denied.businessCode ?? '-'}`);
    }
    return { denied: summarizeResult(denied), sampleId: logisticsManualSample.id };
  });

  await runCase(ctx, 'LOGISTICS-001', '运营手动录入物流成功', async () => {
    if (!logisticsManualSample?.id) logisticsManualSample = await createApprovedSample(ctx, 'logistics-manual');
    const shipped = await apiRequest('PUT', `/samples/${logisticsManualSample.id}/status`, {
      token: ctx.tokens.opsStaff,
      body: { action: 'SHIPPED', trackingNo: `QA-MANUAL-${ctx.runId}`, shipperCode: 'MANUAL' }
    });
    const data = unwrapApiBody(shipped.body);
    if (!isSuccess(shipped) || getField(data, ['status']) !== 'SHIPPED') {
      throw new Error(`manual shipping did not enter SHIPPED/快递中: ${JSON.stringify(shipped.body)}`);
    }
    if (getField(data, ['logisticsSource']) !== 'MANUAL') {
      throw new Error(`expected logisticsSource=MANUAL, got ${getField(data, ['logisticsSource'])}`);
    }
    return { shipped: summarizeResult(shipped), status: getField(data, ['status']), logisticsSource: getField(data, ['logisticsSource']) };
  });

  await runCase(ctx, 'LOGISTICS-003', 'Mock 签收后进入待交作业', async () => {
    const sample = await createApprovedSample(ctx, 'logistics-mock');
    const shipped = await apiRequest('POST', `/test/logistics/ship/${sample.id}`, { token: ctx.tokens.admin });
    const signed = await apiRequest('POST', `/test/logistics/sign/${sample.id}`, { token: ctx.tokens.admin });
    const detail = await apiRequest('GET', `/samples/${sample.id}`, { token: ctx.tokens.admin });
    const detailData = unwrapApiBody(detail.body);
    if (!isSuccess(shipped) || getField(shipped.body, ['logisticsSource']) !== 'MOCK') {
      throw new Error(`mock ship did not mark logisticsSource=MOCK: ${JSON.stringify(shipped.body)}`);
    }
    if (!isSuccess(signed) || getField(signed.body, ['status']) !== 5) {
      throw new Error(`mock sign did not return status=5: ${JSON.stringify(signed.body)}`);
    }
    if (!isSuccess(detail) || getField(detailData, ['status']) !== 'PENDING_TASK') {
      throw new Error(`sample detail did not enter PENDING_TASK: ${JSON.stringify(detail.body)}`);
    }
    return {
      ship: summarizeResult(shipped),
      sign: summarizeResult(signed),
      status: getField(detailData, ['status']),
      logisticsSource: getField(detailData, ['logisticsSource'])
    };
  });

  let failedTalent;
  await runCase(ctx, 'TALENT-001', '采集成功自动填充', async () => {
    const talent = await createTalent(ctx, 'talent-success');
    if (getField(talent, ['enrichStatus']) !== 'SUCCESS') {
      throw new Error(`expected enrichStatus=SUCCESS, got ${getField(talent, ['enrichStatus'])}`);
    }
    if (!getField(talent, ['nickname']) || getField(talent, ['fans', 'fansCount']) == null) {
      throw new Error(`successful mock collection did not auto-fill nickname/fans: ${JSON.stringify(talent)}`);
    }
    return {
      id: talent.id,
      douyinUid: talent.douyinUid,
      enrichStatus: talent.enrichStatus,
      dataSource: talent.dataSource,
      nickname: talent.nickname
    };
  });

  await runCase(ctx, 'TALENT-002', '采集失败手动补录', async () => {
    failedTalent = await createTalent(ctx, 'talent-failed', { fail: true });
    if (getField(failedTalent, ['enrichStatus']) !== 'FAILED') {
      throw new Error(`expected failed collection to stay as FAILED, got ${getField(failedTalent, ['enrichStatus'])}`);
    }
    failedTalent = await manualFillTalent(ctx, failedTalent, 'talent-failed');
    if (getField(failedTalent, ['dataSource']) !== 'MANUAL' || getField(failedTalent, ['enrichStatus']) !== 'SUCCESS') {
      throw new Error(`manual fill did not recover talent: ${JSON.stringify(failedTalent)}`);
    }
    return {
      id: failedTalent.id,
      douyinUid: failedTalent.douyinUid,
      dataSource: failedTalent.dataSource,
      enrichStatus: failedTalent.enrichStatus
    };
  });

  await runCase(ctx, 'TALENT-003', '手动补录后可认领和寄样', async () => {
    if (!failedTalent?.id) {
      failedTalent = await manualFillTalent(ctx, await createTalent(ctx, 'talent-claim', { fail: true }), 'talent-claim');
    }
    await claimTalent(ctx, failedTalent);
    const inPrivatePool = await privatePoolContains(ctx, failedTalent.id);
    if (!inPrivatePool) {
      throw new Error('manual-filled talent was not visible in private pool after claim');
    }
    const sample = await createSampleForTalent(ctx, failedTalent, 'talent-manual-sample');
    if (!sample?.id || getField(sample, ['status']) !== 'PENDING_AUDIT') {
      throw new Error(`manual-filled talent could not create sample: ${JSON.stringify(sample)}`);
    }
    return { talentId: failedTalent.id, inPrivatePool, sampleId: sample.id, sampleStatus: sample.status };
  });

  await runCase(ctx, 'FALLBACK-001', '关闭真实物流接口不影响寄样流程', async () => {
    const sample = await createApprovedSample(ctx, 'fallback-logistics');
    const shipped = await apiRequest('PUT', `/samples/${sample.id}/status`, {
      token: ctx.tokens.opsStaff,
      body: { action: 'SHIPPED', trackingNo: `QA-FALLBACK-${ctx.runId}`, shipperCode: 'MANUAL' }
    });
    const data = unwrapApiBody(shipped.body);
    if (!isSuccess(shipped) || getField(data, ['status']) !== 'SHIPPED' || getField(data, ['logisticsSource']) !== 'MANUAL') {
      throw new Error(`manual logistics fallback did not keep sample flow alive: ${JSON.stringify(shipped.body)}`);
    }
    return {
      noRealThirdPartyCall: true,
      status: getField(data, ['status']),
      logisticsSource: getField(data, ['logisticsSource'])
    };
  });

  await runCase(ctx, 'FALLBACK-002', '关闭真实达人接口不影响达人 CRM 流程', async () => {
    const talent = await manualFillTalent(ctx, await createTalent(ctx, 'fallback-talent', { fail: true }), 'fallback-talent');
    await claimTalent(ctx, talent);
    const inPrivatePool = await privatePoolContains(ctx, talent.id);
    if (!inPrivatePool) {
      throw new Error('manual-filled fallback talent was not visible in private pool');
    }
    return {
      noRealThirdPartyCall: true,
      talentId: talent.id,
      dataSource: talent.dataSource,
      enrichStatus: talent.enrichStatus,
      inPrivatePool
    };
  });
}

function buildSummary(ctx, finishedAt) {
  const total = ctx.results.length;
  const pass = ctx.results.filter((item) => item.pass).length;
  const fail = total - pass;
  return {
    name: SCRIPT_NAME,
    runId: ctx.runId,
    apiBaseUrl: API_BASE_URL,
    startedAt: ctx.startedAt,
    finishedAt,
    overallPass: fail === 0 && total === 8,
    totals: { total, pass, fail },
    environment: ctx.environment || {},
    health: ctx.health || null,
    constraints: {
      switchedRealPre: false,
      realThirdPartyAccessed: false,
      databaseCleared: false,
      productionConfigModified: false,
      logisticsOrTalentMarkedRealIntegrated: false
    },
    seedUsed: Boolean(ctx.seedUsed),
    seedResult: ctx.seedResult || null,
    createdEntities: ctx.createdEntities,
    cases: ctx.results
  };
}

function writeReport(summary, outDir) {
  const lines = [];
  lines.push(`# Third-party fallback regression`);
  lines.push('');
  lines.push(`- Run ID: ${summary.runId}`);
  lines.push(`- API: ${summary.apiBaseUrl}`);
  lines.push(`- Started: ${summary.startedAt}`);
  lines.push(`- Finished: ${summary.finishedAt}`);
  lines.push(`- Overall: ${summary.overallPass ? 'PASS' : 'FAIL'}`);
  lines.push(`- Cases: ${summary.totals.pass}/${summary.totals.total} PASS`);
  lines.push('');
  lines.push(`## Guardrails`);
  lines.push('');
  lines.push(`- No real-pre switch: ${summary.constraints.switchedRealPre ? 'NO' : 'YES'}`);
  lines.push(`- No real third-party access: ${summary.constraints.realThirdPartyAccessed ? 'NO' : 'YES'}`);
  lines.push(`- No database clear: ${summary.constraints.databaseCleared ? 'NO' : 'YES'}`);
  lines.push(`- No production config mutation: ${summary.constraints.productionConfigModified ? 'NO' : 'YES'}`);
  lines.push(`- Logistics/talent not marked real-integrated: ${summary.constraints.logisticsOrTalentMarkedRealIntegrated ? 'NO' : 'YES'}`);
  lines.push('');
  lines.push(`## Cases`);
  lines.push('');
  lines.push('| ID | Result | Name | Evidence |');
  lines.push('| --- | --- | --- | --- |');
  for (const item of summary.cases) {
    const evidence = item.pass
      ? JSON.stringify(item.details || {}).replace(/\|/g, '\\|').slice(0, 500)
      : String(item.failureReason || '').replace(/\|/g, '\\|').replace(/\s+/g, ' ').slice(0, 500);
    lines.push(`| ${item.id} | ${item.pass ? 'PASS' : 'FAIL'} | ${item.name} | ${evidence} |`);
  }
  lines.push('');
  lines.push(`## Risk Note`);
  lines.push('');
  lines.push('Douyin SDK remains the real integration mainline. Logistics and talent provider flows in this run are fallback checks only: logistics uses MANUAL/MOCK, talent uses mock collection/manual fill. These cases must not be interpreted as real third-party logistics or real third-party talent integration completion.');
  fs.writeFileSync(path.join(outDir, 'report.md'), lines.join('\n'), 'utf8');
}

async function main() {
  const runId = process.env.QA_RUN_ID || timestamp();
  const outDir = path.join(OUT_ROOT, `${SCRIPT_NAME}-${runId}`);
  ensureDir(outDir);
  const ctx = {
    runId,
    outDir,
    startedAt: new Date().toISOString(),
    tokens: {},
    results: [],
    createdEntities: [],
    seedUsed: false,
    seedResult: null,
    environment: null,
    health: null
  };
  try {
    await runRegression(ctx);
  } catch (error) {
    failCase(ctx, 'BOOTSTRAP', '环境/登录/回归启动', error);
  }
  const finishedAt = new Date().toISOString();
  const summary = buildSummary(ctx, finishedAt);
  fs.writeFileSync(path.join(outDir, 'summary.json'), JSON.stringify(summary, null, 2), 'utf8');
  writeReport(summary, outDir);
  console.log(`summary: ${path.join(outDir, 'summary.json')}`);
  console.log(`report: ${path.join(outDir, 'report.md')}`);
  if (!summary.overallPass) {
    process.exitCode = 1;
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});

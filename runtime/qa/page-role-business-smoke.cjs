const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('@playwright/test');

const SCRIPT_NAME = 'page-role-business-smoke';
const OUT_ROOT = path.join(__dirname, 'out');
const API_BASE_URL = normalizeBaseUrl(process.env.API_BASE_URL || 'http://127.0.0.1:8080');
const FRONTEND_URL = normalizeBaseUrl(process.env.QA_FRONTEND || process.env.E2E_BASE_URL || 'http://127.0.0.1:3000');
const REQUEST_TIMEOUT_MS = Number(process.env.QA_REQUEST_TIMEOUT_MS || 20000);
const ACTIVITY_ID = process.env.QA_TEST_ACTIVITY_ID || 'TEST_ACTIVITY_A';
const PRODUCT_PENDING = '10901826';

const ROLE_BUSINESS_CASES = {
  admin: {
    username: process.env.QA_ADMIN_USER || 'admin',
    password: process.env.QA_ADMIN_PASSWORD || 'admin123',
    home: '/system',
    allowedOperation: { name: '系统用户列表', method: 'GET', path: '/users?page=1&size=5', expect: { ok: true } },
    forbiddenOperation: { name: '禁止手动完成寄样', fixture: 'pendingHomeworkSample', method: 'PUT', path: '/samples/{sampleId}/status', body: { action: 'FINISHED', reason: 'manual complete should be forbidden' }, expect: { forbidden: true } }
  },
  biz_leader: {
    username: 'biz_leader',
    password: 'admin123',
    home: '/product/manage',
    allowedOperation: { name: '分配待审核商品审核人', fixture: 'bizStaffAssignable', method: 'PUT', path: `/colonel/activities/${ACTIVITY_ID}/products/${PRODUCT_PENDING}/audit-assignee`, bodyFromFixture: 'assignBizStaff', expect: { ok: true } },
    forbiddenOperation: { name: '禁止审核商品', method: 'PUT', path: `/colonel/activities/${ACTIVITY_ID}/products/${PRODUCT_PENDING}/audit-result`, body: { approved: false, reason: 'biz_leader should not audit' }, expect: { forbidden: true } }
  },
  biz_staff: {
    username: 'biz_staff',
    password: 'admin123',
    home: '/product/manage/products',
    allowedOperation: { name: '审核商品通过入库', method: 'PUT', path: `/colonel/activities/${ACTIVITY_ID}/products/${PRODUCT_PENDING}/audit-result`, bodyFromFixture: 'auditPayload', expect: { ok: true } },
    forbiddenOperation: { name: '禁止访问用户管理接口', method: 'GET', path: '/users?page=1&size=5', expect: { forbidden: true } }
  },
  channel_leader: {
    username: 'channel_leader',
    password: 'admin123',
    home: '/sample',
    allowedOperation: { name: '查看寄样列表', method: 'GET', path: '/samples?page=1&size=5', expect: { ok: true } },
    forbiddenOperation: { name: '禁止导出寄样数据', method: 'GET', path: '/samples/exports', expect: { forbidden: true } }
  },
  channel_staff: {
    username: 'channel_staff',
    password: 'admin123',
    home: '/sample',
    allowedOperation: { name: '寄样资格预检', fixture: 'sampleEligibility', method: 'POST', path: '/samples/eligibility-check', bodyFromFixture: 'eligibilityPayload', expect: { ok: true } },
    forbiddenOperation: { name: '禁止访问用户管理接口', method: 'GET', path: '/users?page=1&size=5', expect: { forbidden: true } }
  },
  operator: {
    username: 'ops_staff',
    password: 'admin123',
    home: '/ops/shipping',
    allowedOperation: { name: '录入寄样发货', fixture: 'pendingShipSample', method: 'PUT', path: '/samples/{sampleId}/status', bodyFromFixture: 'shipPayload', expect: { ok: true } },
    forbiddenOperation: { name: '禁止创建寄样申请', method: 'POST', path: '/samples', body: { productId: '00000000-0000-0000-0000-000000000000', talentId: 'talent_test_a', quantity: 1, remark: 'operator should not create sample' }, expect: { forbidden: true } }
  }
};

function timestamp(date = new Date()) {
  const pad = (n, len = 2) => String(n).padStart(len, '0');
  return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}-${pad(date.getHours())}${pad(date.getMinutes())}${pad(date.getSeconds())}-${pad(date.getMilliseconds(), 3)}`;
}

function normalizeBaseUrl(value) {
  return String(value || '').replace(/\/+$/, '');
}

function isIgnorableConsoleError(text) {
  return /ResizeObserver|favicon\.ico|net::ERR_CONNECTION_CLOSED/.test(String(text || ''));
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
  for (const key of ['records', 'list', 'rows', 'items', 'content', 'users', 'samples', 'productList']) {
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

function normalizeRoleAlias(role) {
  return role === 'operator' ? 'ops_staff' : role;
}

function hasSuccessBusinessCode(body) {
  const code = body && typeof body === 'object' ? body.code : undefined;
  return code == null || Number(code) === 200;
}

function evaluateOperation(operation, result) {
  const expect = operation.expect || {};
  const businessCode = result.body?.code;
  const success = result.ok && hasSuccessBusinessCode(result.body);
  if (expect.forbidden) {
    const forbidden = [401, 403].includes(result.status) || [401, 403].includes(Number(businessCode));
    return forbidden
      ? { pass: true, reason: '' }
      : { pass: false, reason: `expected forbidden but got HTTP ${result.status}, code=${businessCode ?? '-'}` };
  }
  if (expect.ok && !success) {
    return { pass: false, reason: `expected success but got HTTP ${result.status}, code=${businessCode ?? '-'}: ${result.body?.msg || result.error || ''}` };
  }
  return { pass: true, reason: '' };
}

function summarizeRoleResults(results) {
  const roles = {};
  const failures = [];
  for (const result of results) {
    const pass = Boolean(result.menu?.pass && result.allowedOperation?.pass && result.forbiddenOperation?.pass);
    roles[result.role] = { ...result, pass };
    if (!pass) {
      failures.push({
        role: result.role,
        reason: [
          result.menu?.pass ? null : result.menu?.reason || 'menu failed',
          result.allowedOperation?.pass ? null : result.allowedOperation?.reason || 'allowed operation failed',
          result.forbiddenOperation?.pass ? null : result.forbiddenOperation?.reason || 'forbidden operation failed'
        ].filter(Boolean).join('; ')
      });
    }
  }
  return {
    total: results.length,
    pass: Object.values(roles).filter((item) => item.pass).length,
    fail: failures.length,
    overallPass: failures.length === 0,
    roles,
    failures
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
    return { ...result, method, path: apiPath, businessCode: result.body?.code };
  } catch (error) {
    return { method, path: apiPath, url, status: 0, ok: false, body: null, durationMs: 0, error: error?.message || String(error) };
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
  if (!envResult.ok) throw new Error(`/api/system/env failed with HTTP ${envResult.status}`);
  ctx.environment = normalizeEnv(envResult.body);
  if (!ctx.environment.isTest) throw new Error(`Refusing to run outside TEST/mock environment: ${JSON.stringify(ctx.environment)}`);
  const health = await apiRequest('GET', '/system/health');
  ctx.health = health.body;
  if (!health.ok || health.body?.status !== 'UP') throw new Error(`/api/system/health is not UP`);
}

async function login(account) {
  const result = await apiRequest('POST', '/auth/login', { body: { username: account.username, password: account.password } });
  const data = unwrapApiBody(result.body);
  const token = data?.token || data?.accessToken;
  if (!result.ok || !token) throw new Error(`login failed for ${account.username}: HTTP ${result.status}`);
  return { token, user: data };
}

async function seed(ctx) {
  const result = await apiRequest('POST', '/test/seed', { token: ctx.tokens.admin });
  if (!result.ok || !hasSuccessBusinessCode(result.body)) throw new Error(`/api/test/seed failed: HTTP ${result.status}`);
  ctx.seed = unwrapApiBody(result.body) || {};
  return ctx.seed;
}

function auditPayload(runId) {
  return {
    approved: true,
    reason: `QA ${runId} role smoke audit`,
    exclusivePriceRemark: `QA ${runId} 专属价`,
    shippingInfo: '48 小时内发货',
    sellingPoints: [`QA ${runId} 卖点 A`, `QA ${runId} 卖点 B`],
    promotionScript: `QA ${runId} 推广话术`,
    supportsAds: true,
    rewardRemark: `QA ${runId} 奖励说明`,
    participationRequirements: '账号状态正常',
    campaignTimeRemark: 'TEST/mock 验收窗口',
    materialFiles: [`https://test.local/material/${encodeURIComponent(runId)}.png`]
  };
}

async function sampleByRequestNo(ctx, requestNo, token = ctx.tokens.admin) {
  const result = await apiRequest('GET', `/samples?page=1&size=20&keyword=${encodeURIComponent(requestNo)}`, { token });
  const sample = extractRecords(result.body).find((item) => String(item.requestNo) === String(requestNo)) || extractRecords(result.body)[0];
  if (!sample?.id) throw new Error(`sample fixture not found: ${requestNo}`);
  return sample;
}

async function firstAssignableBizStaff(ctx, token) {
  const result = await apiRequest('GET', '/users/assignable?keyword=biz_staff&page=1&size=20', { token });
  const user = extractRecords(result.body).find((item) => String(item.username || '').includes('biz_staff')) || extractRecords(result.body)[0];
  if (!user?.id) throw new Error('assignable biz_staff not found');
  return user;
}

async function sampleEligibilityPayload(ctx, token) {
  const products = await apiRequest('GET', '/samples/product-candidates?page=1&size=10', { token });
  const talents = await apiRequest('GET', '/samples/talent-candidates?page=1&size=10', { token });
  const product = extractRecords(products.body)[0];
  const talent = extractRecords(talents.body)[0];
  if (!product || !talent) throw new Error('sample eligibility candidates unavailable');
  return {
    productId: product.id || product.productId,
    talentId: talent.talentId || talent.id || talent.douyinUid,
    quantity: 1,
    remark: `QA ${ctx.qaRunId} eligibility`
  };
}

async function resolveOperation(ctx, role, operation, token) {
  const resolved = { ...operation, path: operation.path, body: operation.body ? { ...operation.body } : undefined };
  if (operation.fixture === 'pendingHomeworkSample') {
    const sample = await sampleByRequestNo(ctx, ctx.seed.sampleRequestNo);
    resolved.path = operation.path.replace('{sampleId}', sample.id);
  }
  if (operation.fixture === 'pendingShipSample') {
    const sample = await sampleByRequestNo(ctx, ctx.seed.pendingShipSampleRequestNo);
    resolved.path = operation.path.replace('{sampleId}', sample.id);
  }
  if (operation.fixture === 'bizStaffAssignable') {
    const user = await firstAssignableBizStaff(ctx, token);
    resolved.body = { assigneeId: user.id };
  }
  if (operation.bodyFromFixture === 'assignBizStaff' && !resolved.body) {
    const user = await firstAssignableBizStaff(ctx, token);
    resolved.body = { assigneeId: user.id };
  }
  if (operation.bodyFromFixture === 'auditPayload') {
    resolved.body = auditPayload(ctx.qaRunId);
  }
  if (operation.bodyFromFixture === 'shipPayload') {
    resolved.body = { action: 'SHIPPING', trackingNo: `QA${Date.now()}`, shipperCode: 'SF', reason: `QA ${ctx.qaRunId} 发货` };
  }
  if (operation.fixture === 'sampleEligibility' || operation.bodyFromFixture === 'eligibilityPayload') {
    resolved.body = await sampleEligibilityPayload(ctx, token);
  }
  return resolved;
}

function summarizeRequest(result) {
  return {
    method: result.method,
    path: result.path,
    status: result.status,
    businessCode: result.body?.code,
    ok: result.ok,
    durationMs: result.durationMs,
    message: result.body?.msg || result.body?.message || null,
    keyFields: {
      id: getField(unwrapApiBody(result.body), 'id'),
      status: getField(unwrapApiBody(result.body), 'status'),
      bizStatus: getField(unwrapApiBody(result.body), 'bizStatus'),
      total: getField(unwrapApiBody(result.body), 'total')
    }
  };
}

async function executeOperation(ctx, role, operation, token) {
  const resolved = await resolveOperation(ctx, role, operation, token);
  const result = await apiRequest(resolved.method, resolved.path, { token, body: resolved.body });
  const evaluation = evaluateOperation(operation, result);
  return {
    name: operation.name,
    pass: evaluation.pass,
    reason: evaluation.reason,
    requestResult: summarizeRequest(result)
  };
}

async function collectUiForRole(ctx, role, caseDef, loginResult) {
  if (!ctx.browser) throw new Error('browser is not initialized');
  const context = await ctx.browser.newContext({ baseURL: FRONTEND_URL, viewport: { width: 1440, height: 960 } });
  await context.addInitScript(({ token, userInfo }) => {
    localStorage.setItem('token', token);
    if (userInfo?.refreshToken) localStorage.setItem('refreshToken', userInfo.refreshToken);
    if (userInfo?.refreshExpiresIn) localStorage.setItem('refreshExpiresIn', String(userInfo.refreshExpiresIn));
    if (userInfo?.accessTokenExpiresIn || userInfo?.expiresIn) {
      localStorage.setItem('accessTokenExpiresIn', String(userInfo.accessTokenExpiresIn || userInfo.expiresIn));
    }
    localStorage.setItem('userInfo', JSON.stringify(userInfo || {}));
  }, {
    token: loginResult.token,
    userInfo: loginResult.user
  });
  const page = await context.newPage();
  const consoleErrors = [];
  page.on('console', (msg) => {
    if (msg.type() === 'error' && !isIgnorableConsoleError(msg.text())) {
      consoleErrors.push(msg.text());
    }
  });
  try {
    await page.goto(caseDef.home, { waitUntil: 'domcontentloaded', timeout: 15000 }).catch(() => {});
    await page.waitForTimeout(800);
    const finalPath = new URL(page.url()).pathname;
    const sidebarText = await page.locator('[data-testid="sidebar-menu"]').innerText().catch(() => '');
    const headerText = await page.locator('header, .n-layout-header').innerText().catch(() => '');
    const buttons = await page.locator('button').evaluateAll((nodes) => nodes.map((node) => node.innerText.trim()).filter(Boolean)).catch(() => []);
    const authState = await page.evaluate(() => {
      try {
        const raw = JSON.parse(localStorage.getItem('userInfo') || 'null');
        return {
          tokenExists: Boolean(localStorage.getItem('token')),
          roleCodes: raw?.roleCodes || [],
          username: raw?.username || null,
          dataScope: raw?.dataScope || raw?.scope || null,
          deptId: raw?.deptId || null
        };
      } catch (error) {
        return { parseError: String(error) };
      }
    });
    const pass = !finalPath.startsWith('/login') && consoleErrors.length === 0;
    return {
      pass,
      reason: pass ? '' : `ui collection failed: finalPath=${finalPath}, consoleErrors=${consoleErrors.length}`,
      finalPath,
      menu: [sidebarText, headerText].filter(Boolean).join('\n'),
      buttons,
      dataScope: authState,
      consoleErrors
    };
  } finally {
    await context.close().catch(() => {});
  }
}

async function runRole(ctx, role, caseDef) {
  await seed(ctx);
  const loginResult = await login({ username: caseDef.username, password: caseDef.password });
  const token = loginResult.token;
  const menu = await collectUiForRole(ctx, role, caseDef, loginResult);
  const allowedOperation = await executeOperation(ctx, role, caseDef.allowedOperation, token);
  await seed(ctx);
  const forbiddenOperation = await executeOperation(ctx, role, caseDef.forbiddenOperation, token);
  const result = {
    role,
    username: caseDef.username,
    normalizedRole: normalizeRoleAlias(role),
    menu,
    buttons: menu.buttons,
    dataScope: menu.dataScope,
    allowedOperation,
    forbiddenOperation
  };
  console.log(`[${menu.pass && allowedOperation.pass && forbiddenOperation.pass ? 'PASS' : 'FAIL'}] ${role}`);
  return result;
}

function buildReport(summary) {
  const lines = [];
  lines.push('# TEST/mock 角色业务操作 Smoke 报告');
  lines.push('');
  lines.push(`- 脚本：${SCRIPT_NAME}`);
  lines.push(`- 前端：${summary.frontendUrl}`);
  lines.push(`- 后端：${summary.apiBaseUrl}`);
  lines.push(`- 环境：${summary.environment?.environmentLabel || '(unknown)'}`);
  lines.push(`- 总体结论：**${summary.overallPass ? 'PASS' : 'FAIL'}**`);
  lines.push('');
  for (const [role, result] of Object.entries(summary.roles)) {
    lines.push(`## ${role}`);
    lines.push(`- 账号：${result.username}`);
    lines.push(`- 角色码：${(result.dataScope?.roleCodes || []).join(', ') || '-'}`);
    lines.push(`- 数据范围：${result.dataScope?.dataScope || result.dataScope?.deptId || '-'}`);
    lines.push(`- 菜单：${JSON.stringify(String(result.menu?.menu || '').split(/\n+/).filter(Boolean))}`);
    lines.push(`- 按钮：${JSON.stringify(result.buttons || [])}`);
    lines.push(`- 允许操作：${result.allowedOperation.pass ? 'PASS' : 'FAIL'} ${result.allowedOperation.name} -> HTTP ${result.allowedOperation.requestResult.status}, code=${result.allowedOperation.requestResult.businessCode ?? '-'}`);
    if (result.allowedOperation.reason) lines.push(`  - 原因：${result.allowedOperation.reason}`);
    lines.push(`- 禁止操作：${result.forbiddenOperation.pass ? 'PASS' : 'FAIL'} ${result.forbiddenOperation.name} -> HTTP ${result.forbiddenOperation.requestResult.status}, code=${result.forbiddenOperation.requestResult.businessCode ?? '-'}`);
    if (result.forbiddenOperation.reason) lines.push(`  - 原因：${result.forbiddenOperation.reason}`);
    lines.push('');
  }
  if (summary.failures.length) {
    lines.push('## Failures');
    for (const failure of summary.failures) lines.push(`- ${failure.role}: ${failure.reason}`);
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
    qaRunId: process.env.QA_RUN_ID || `QA_ROLE_${timestamp()}`,
    outDir,
    environment: null,
    health: null,
    seed: {},
    tokens: {},
    browser: null,
    notes: []
  };
}

async function main() {
  const outDir = process.argv[2] || path.join(OUT_ROOT, `${SCRIPT_NAME}-${timestamp()}`);
  ensureDir(outDir);
  const ctx = createContext(outDir);
  const startedAt = new Date().toISOString();
  const roleResults = [];
  let browser = null;
  try {
    await ensureTestEnvironment(ctx);
    ctx.tokens.admin = (await login(ROLE_BUSINESS_CASES.admin)).token;
    browser = await chromium.launch({ headless: String(process.env.QA_HEADFUL || '').toLowerCase() !== 'true' });
    ctx.browser = browser;
    for (const [role, caseDef] of Object.entries(ROLE_BUSINESS_CASES)) {
      roleResults.push(await runRole(ctx, role, caseDef));
    }
  } catch (error) {
    ctx.notes.push(error?.stack || String(error));
  } finally {
    ctx.browser = null;
    if (browser) await browser.close().catch(() => {});
  }
  const roleSummary = summarizeRoleResults(roleResults);
  const summary = {
    name: SCRIPT_NAME,
    qaRunId: ctx.qaRunId,
    frontendUrl: FRONTEND_URL,
    apiBaseUrl: API_BASE_URL,
    outputDir: ctx.outDir,
    startedAt,
    finishedAt: new Date().toISOString(),
    environment: ctx.environment,
    health: ctx.health,
    ...roleSummary,
    overallPass: ctx.environment?.isTest === true && roleSummary.overallPass && ctx.notes.length === 0,
    notes: ctx.notes
  };
  writeArtifacts(ctx, summary);
  console.log(`Page role business smoke output: ${ctx.outDir}`);
  return summary.overallPass ? 0 : 1;
}

if (require.main === module) {
  main().then((exitCode) => {
    process.exit(exitCode);
  }).catch((error) => {
    console.error(error?.stack || String(error));
    process.exit(1);
  });
}

module.exports = {
  ROLE_BUSINESS_CASES,
  normalizeRoleAlias,
  evaluateOperation,
  isIgnorableConsoleError,
  summarizeRoleResults
};

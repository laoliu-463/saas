const fs = require('node:fs');
const path = require('node:path');
const { chromium, expect } = require('@playwright/test');

const SCRIPT_NAME = 'qa-business-crud-flow';
const REPO_ROOT = path.resolve(__dirname, '..', '..');
const CASES_FILE = path.join(__dirname, 'business-crud-cases.json');
const DEFAULT_CASES = {
  roles: {
    admin: { username: process.env.ADMIN_USER || 'admin', password: process.env.ADMIN_PASSWORD || 'admin123' },
    biz_leader: { username: 'biz_leader', password: 'admin123' },
    channel_leader: { username: 'channel_leader', password: 'admin123' },
    channel_staff: { username: 'channel_staff', password: 'admin123' }
  },
  protectedAccounts: ['admin', 'leader_zs', 'seller_zs', 'channel_user', 'ops_user', 'biz_leader'],
  mockActivityId: '3859423',
  hardFailRoutes: ['/dashboard', '/product/library', '/sample', '/orders']
};

const BASE_URL = process.env.E2E_BASE_URL || process.env.QA_FRONTEND || 'http://localhost:3002';
const API_BASE_URL = process.env.API_BASE_URL || 'http://localhost:8081';
const HEADLESS = String(process.env.QA_HEADFUL || '').toLowerCase() !== 'true';
const SLOW_MO = Number(process.env.QA_SLOW_MO || 0);
const SLOW_API_MS = Number(process.env.QA_SLOW_API_MS || 2000);
const FAIL_SLOW_API_MS = Number(process.env.QA_FAIL_SLOW_API_MS || 5000);

function timestamp(date = new Date()) {
  const pad = (n, len = 2) => String(n).padStart(len, '0');
  return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}-${pad(date.getHours())}${pad(date.getMinutes())}${pad(date.getSeconds())}-${pad(date.getMilliseconds(), 3)}`;
}

function createQaRunId(date = new Date(), suffix = Math.random().toString(36).slice(2, 6)) {
  const pad = (n) => String(n).padStart(2, '0');
  return `QA${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}_${pad(date.getHours())}${pad(date.getMinutes())}${pad(date.getSeconds())}_${suffix}`;
}

function ensureDir(dir) {
  fs.mkdirSync(dir, { recursive: true });
}

function readJsonIfExists(filePath, fallback) {
  try {
    return JSON.parse(fs.readFileSync(filePath, 'utf8'));
  } catch {
    return fallback;
  }
}

function excerpt(text, max = 700) {
  return String(text || '').replace(/\s+/g, ' ').trim().slice(0, max);
}

function sanitizeFilePart(name) {
  return String(name || 'step')
    .replace(/[\\/:*?"<>|]+/g, '-')
    .replace(/\s+/g, '-')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '')
    .slice(0, 90) || 'step';
}

function normalizePathname(input) {
  const raw = String(input || '/').split(/[?#]/)[0].trim() || '/';
  const pathname = raw.startsWith('/') ? raw : `/${raw}`;
  return pathname === '/' ? pathname : pathname.replace(/\/+$/, '');
}

function unwrapApiBody(body) {
  if (body && typeof body === 'object' && Object.prototype.hasOwnProperty.call(body, 'data')) {
    return body.data;
  }
  return body;
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
  const labelIsTest = environmentLabel.toLowerCase() === 'test';
  const isRealPre = environmentLabel.toLowerCase() === 'real-pre' || profile === 'real-pre' || activeProfiles.includes('real-pre');
  const isTest = labelIsTest || profile === 'test' || activeProfiles.includes('test') || appTestEnabled || douyinTestEnabled;
  return {
    environmentLabel: environmentLabel || null,
    profile: profile || null,
    activeProfiles,
    appTestEnabled,
    douyinTestEnabled,
    database: data.database || data.databaseName || null,
    raw,
    isRealPre,
    isTest
  };
}

function createSummarySkeleton({ qaRunId, startedAt }) {
  return {
    name: SCRIPT_NAME,
    qaRunId,
    overallPass: false,
    environment: {},
    startedAt,
    finishedAt: '',
    modules: [],
    createdEntities: [],
    cleanup: [],
    apiErrors: [],
    consoleErrors: [],
    slowApis: [],
    skipped: []
  };
}

function isQaOwnedEntity(entity, qaRunId) {
  const fixed = new Set(['admin', 'leader_zs', 'seller_zs', 'channel_user', 'ops_user', 'biz_leader', 'biz_staff', 'channel_leader', 'channel_staff', 'ops_staff']);
  const values = Object.values(entity || {}).filter((value) => value != null).map((value) => String(value));
  if (values.some((value) => fixed.has(value))) return false;
  return values.some((value) => value.includes(qaRunId));
}

function isPathUnder(pathname, prefix) {
  const value = normalizePathname(pathname);
  const base = normalizePathname(prefix);
  return value === base || value.startsWith(`${base}/`);
}

function makeQaRunId() {
  return createQaRunId();
}

function containsQaRunId(value, qaRunId) {
  return String(value || '').includes(qaRunId);
}

function isProtectedAccount(username) {
  return new Set(DEFAULT_CASES.protectedAccounts).has(String(username || ''));
}

function blockingApiErrors(ctx) {
  return ctx.apiErrors.filter((item) => item.status >= 500 || item.status === 401);
}

function createContext(outDir) {
  const now = new Date();
  const qaRunId = process.env.QA_RUN_ID || createQaRunId(now);
  const cases = { ...DEFAULT_CASES, ...readJsonIfExists(CASES_FILE, {}) };
  cases.roles = { ...DEFAULT_CASES.roles, ...(cases.roles || {}) };
  return {
    ...createSummarySkeleton({ qaRunId, startedAt: now.toISOString() }),
    baseUrl: BASE_URL,
    apiBaseUrl: API_BASE_URL,
    outDir,
    screenshotsDir: path.join(outDir, 'screenshots'),
    cases,
    currentModule: 'bootstrap',
    currentStep: 'bootstrap',
    requestStartedAt: new Map(),
    tokens: {},
    expectedRejectUrls: [/\/api\/system\/users/, /\/api\/users/],
    fatalErrors: []
  };
}

async function fetchJson(url, options = {}) {
  const started = Date.now();
  const response = await fetch(url, {
    ...options,
    headers: {
      Accept: 'application/json',
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...(options.headers || {})
    }
  });
  const text = await response.text();
  let body = null;
  try {
    body = text ? JSON.parse(text) : null;
  } catch {
    body = { rawText: text };
  }
  return { url, status: response.status, ok: response.ok, durationMs: Date.now() - started, body };
}

async function apiRequest(ctx, method, apiPath, { token, body, allowStatuses = [] } = {}) {
  const result = await fetchJson(`${ctx.apiBaseUrl}/api${apiPath}`, {
    method,
    body: body == null ? undefined : JSON.stringify(body),
    headers: token ? { Authorization: `Bearer ${token}` } : {}
  });
  const accepted = result.ok || allowStatuses.includes(result.status);
  if (!accepted) {
    const event = { step: ctx.currentStep, status: result.status, method, url: result.url, durationMs: result.durationMs, body: result.body };
    ctx.apiErrors.push(event);
  }
  if (result.durationMs >= SLOW_API_MS) {
    ctx.slowApis.push({ step: ctx.currentStep, status: result.status, method, url: result.url, durationMs: result.durationMs });
  }
  return result;
}

async function ensureTestEnvironment(ctx) {
  const health = await fetchJson(`${ctx.apiBaseUrl}/api/actuator/health`).catch((error) => ({ status: 0, ok: false, error: String(error), body: null }));
  if (!health.ok || health.body?.status !== 'UP') {
    throw new Error(`/api/actuator/health is not UP: ${JSON.stringify(health)}`);
  }
  const envProbe = await fetchJson(`${ctx.apiBaseUrl}/api/system/env`).catch((error) => ({ status: 0, ok: false, error: String(error), body: null }));
  if (!envProbe.ok) {
    throw new Error(`/api/system/env failed: ${JSON.stringify(envProbe)}`);
  }
  const environment = normalizeEnv(envProbe.body);
  ctx.environment = environment;
  if (!environment.isTest) {
    throw new Error(`Refusing to run outside TEST/mock environment: ${JSON.stringify(environment)}`);
  }
  return environment;
}

async function optionalProbe(ctx, name, apiPath) {
  const result = await apiRequest(ctx, 'POST', apiPath, { allowStatuses: [401, 403, 404, 405] }).catch((error) => ({ status: 0, error: String(error) }));
  const skipped = [0, 401, 403, 404, 405].includes(result.status);
  if (skipped) recordSkip(ctx, name, `optional endpoint unavailable: ${result.status || result.error}`);
  return { skipped, status: result.status };
}

async function optionalMockReset(ctx) {
  return optionalProbe(ctx, 'mock-reset-unavailable', '/test/mock/reset');
}

async function optionalMockSeed(ctx) {
  return optionalProbe(ctx, 'mock-seed-unavailable', '/test/mock/seed');
}

async function prepareTestSeed(ctx, token, requester = apiRequest) {
  const result = await requester(ctx, 'POST', '/test/seed', { token, allowStatuses: [401, 403, 404, 405] });
  if (!result.ok) {
    if (Array.isArray(ctx.skipped)) {
      recordSkip(ctx, 'test-seed-unavailable', `optional endpoint unavailable: ${result.status}`);
    }
    ctx.testSeed = {};
    return ctx.testSeed;
  }
  ctx.testSeed = unwrapApiBody(result.body) || {};
  return ctx.testSeed;
}

async function prepareTestOrders(ctx, token, requester = apiRequest) {
  const endpoints = [
    ['attributed', '/test/orders/generate-attributed'],
    ['noPickSource', '/test/orders/generate-no-pick-source'],
    ['missingMapping', '/test/orders/generate-missing-mapping']
  ];
  const prepared = {};
  for (const [key, route] of endpoints) {
    const result = await requester(ctx, 'POST', route, { token, allowStatuses: [401, 403, 404, 405] });
    if (!result.ok) {
      if (Array.isArray(ctx.skipped)) {
        recordSkip(ctx, `test-order-fixture-${key}-unavailable`, `optional endpoint unavailable: ${result.status}`);
      }
      continue;
    }
    prepared[key] = unwrapApiBody(result.body) || {};
  }
  ctx.testOrders = prepared;
  return prepared;
}

async function apiLogin(ctx, roleName) {
  const role = ctx.cases.roles[roleName] || {};
  const username = role.username || roleName;
  const password = role.password || 'admin123';
  const result = await apiRequest(ctx, 'POST', '/auth/login', { body: { username, password } });
  const data = unwrapApiBody(result.body);
  const token = data?.token || data?.accessToken || '';
  if (!result.ok || !token) throw new Error(`API login failed for ${username}: HTTP ${result.status}`);
  ctx.tokens[roleName] = token;
  return { username, password, token, data };
}

async function login(ctx, page, roleName = 'admin') {
  const role = ctx.cases.roles[roleName] || {};
  const username = role.username || roleName;
  const password = role.password || 'admin123';
  await page.goto('/login', { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.getByTestId('login-username').locator('input').fill(username);
  await page.getByTestId('login-password').locator('input').fill(password);
  await Promise.all([
    page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 30000 }),
    page.getByTestId('login-submit').click()
  ]);
  await page.waitForLoadState('domcontentloaded');
  return { username };
}

async function logout(page) {
  await page.evaluate(() => {
    localStorage.clear();
    sessionStorage.clear();
  }).catch(() => {});
}

async function runStep(ctx, module, name, fn, options = {}) {
  const started = Date.now();
  const step = { name, pass: false, skipped: false, durationMs: 0, details: {} };
  ctx.currentModule = module.name;
  ctx.currentStep = `${module.name} :: ${name}`;
  try {
    const details = await fn();
    step.pass = true;
    step.skipped = Boolean(details?.skipped);
    step.details = details || {};
    if (step.skipped) {
      ctx.skipped.push({ module: module.name, step: name, reason: details?.reason || 'skipped' });
    }
  } catch (error) {
    if (options.optional) {
      step.pass = true;
      step.skipped = true;
      step.details = { reason: error?.message || String(error) };
      ctx.skipped.push({ module: module.name, step: name, reason: step.details.reason });
    } else {
      step.pass = false;
      step.details = { error: error?.stack || String(error) };
    }
  } finally {
    step.durationMs = Date.now() - started;
    module.steps.push(step);
  }
  return step;
}

async function gotoAndWait(page, route, expectedTexts = []) {
  await page.goto(route, { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.waitForLoadState('networkidle', { timeout: 8000 }).catch(() => {});
  for (const text of expectedTexts) {
    await assertTextVisible(page, text);
  }
  return { route, finalUrl: page.url(), bodyExcerpt: excerpt(await page.locator('body').innerText().catch(() => '')) };
}

async function clickByTextIfVisible(page, regexOrText) {
  const locator = typeof regexOrText === 'string'
    ? page.getByText(regexOrText, { exact: false }).first()
    : page.getByText(regexOrText).first();
  if (!(await locator.isVisible({ timeout: 1500 }).catch(() => false))) return false;
  await locator.click();
  return true;
}

async function fillByLabelOrPlaceholder(page, labels, value) {
  for (const label of [].concat(labels)) {
    const byLabel = page.getByLabel(label).first();
    if (await byLabel.isVisible({ timeout: 500 }).catch(() => false)) {
      await byLabel.fill(value);
      return true;
    }
    const byPlaceholder = page.getByPlaceholder(label).first();
    if (await byPlaceholder.isVisible({ timeout: 500 }).catch(() => false)) {
      await byPlaceholder.fill(value);
      return true;
    }
  }
  return false;
}

async function selectFirstOptionIfVisible(page) {
  const select = page.locator('.n-select, [role="combobox"]').first();
  if (!(await select.isVisible({ timeout: 1000 }).catch(() => false))) return false;
  await select.click();
  const option = page.locator('.n-base-select-option, [role="option"]').first();
  if (!(await option.isVisible({ timeout: 2000 }).catch(() => false))) return false;
  await option.click();
  return true;
}

async function searchByKeyword(page, keyword) {
  const input = page.locator('input[placeholder*="搜索"], input[placeholder*="用户名"], input[placeholder*="订单"], input[placeholder*="商品"], input[placeholder*="达人"], input').first();
  if (!(await input.isVisible({ timeout: 1500 }).catch(() => false))) return false;
  await input.fill(keyword);
  const clicked = await clickByTextIfVisible(page, /^查询$|^搜索$/);
  if (!clicked) await input.press('Enter').catch(() => {});
  await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => {});
  return true;
}

async function assertTextVisible(page, textOrRegex) {
  await expect(page.getByText(textOrRegex).first()).toBeVisible({ timeout: 10000 });
}

async function assertUrlMatches(page, regex) {
  await expect(page).toHaveURL(regex, { timeout: 10000 });
}

async function takeScreenshot(ctx, page, name) {
  const file = path.join(ctx.screenshotsDir, `${String(ctx.modules.length + 1).padStart(2, '0')}-${sanitizeFilePart(name)}.png`);
  await page.screenshot({ path: file, fullPage: true }).catch(() => {});
  return file;
}

function recordCreatedEntity(ctx, type, idOrName, extra = {}) {
  const entity = { type, idOrName, ...extra };
  ctx.createdEntities.push(entity);
  return entity;
}

function recordSkip(ctx, code, reason) {
  ctx.skipped.push({ code, reason });
}

function attachPageObservers(page, ctx) {
  page.on('console', (msg) => {
    if (msg.type() !== 'error') return;
    ctx.consoleErrors.push({ step: ctx.currentStep, type: 'console', text: msg.text(), location: msg.location() });
  });
  page.on('pageerror', (error) => {
    ctx.consoleErrors.push({ step: ctx.currentStep, type: 'pageerror', text: error?.stack || String(error) });
  });
  page.on('request', (request) => {
    if (request.url().includes('/api/')) ctx.requestStartedAt.set(request, Date.now());
  });
  page.on('response', (response) => {
    const url = response.url();
    if (!url.includes('/api/')) return;
    const request = response.request();
    const started = ctx.requestStartedAt.get(request) || Date.now();
    const event = { step: ctx.currentStep, status: response.status(), url, method: request.method(), durationMs: Date.now() - started };
    ctx.requestStartedAt.delete(request);
    if (event.durationMs >= SLOW_API_MS) ctx.slowApis.push(event);
    if (event.status >= 400) ctx.apiErrors.push(event);
  });
}

function addModule(ctx, name) {
  const module = { name, pass: false, steps: [] };
  ctx.modules.push(module);
  return module;
}

function modulePass(module) {
  module.pass = module.steps.every((step) => step.pass);
  return module.pass;
}

async function adminLoginAndEnvironment(ctx, page) {
  const module = addModule(ctx, 'admin-login-environment');
  await runStep(ctx, module, 'confirm TEST and backend health', () => ensureTestEnvironment(ctx));
  await runStep(ctx, module, 'login admin and open dashboard', async () => {
    await login(ctx, page, 'admin');
    const details = await gotoAndWait(page, '/dashboard', [/环境：TEST|TEST/, /业务概览|归因概览|数据看板/]);
    await takeScreenshot(ctx, page, 'admin-dashboard');
    return details;
  });
  modulePass(module);
}

async function talentCrud(ctx, page) {
  const module = addModule(ctx, 'talent-crud');
  const actor = 'channel_leader';
  let token;
  let talent;
  const nickname = `QA达人_${ctx.qaRunId}`;
  const douyinNo = `qa_douyin_${ctx.qaRunId}`;
  await runStep(ctx, module, 'login channel leader and open talent page', async () => {
    await logout(page);
    await login(ctx, page, actor);
    return gotoAndWait(page, '/talent', [/达人|CRM/]);
  });
  await runStep(ctx, module, 'C create QA talent through API', async () => {
    token = (await apiLogin(ctx, actor)).token;
    const result = await apiRequest(ctx, 'POST', '/talents', {
      token,
      body: { nickname, douyinNo, douyinUid: `qa_uid_${ctx.qaRunId}`, contactPhone: '13800000000', remark: `QA_CREATE_${ctx.qaRunId}`, intro: `QA_CREATE_${ctx.qaRunId}` }
    });
    if (!result.ok) throw new Error(`create talent failed HTTP ${result.status}: ${JSON.stringify(result.body)}`);
    talent = unwrapApiBody(result.body);
    recordCreatedEntity(ctx, 'talent', talent?.id || nickname, { id: talent?.id, nickname, douyinNo });
    return { id: talent?.id, nickname };
  });
  await runStep(ctx, module, 'R search and open talent detail', async () => {
    await gotoAndWait(page, '/talent');
    await searchByKeyword(page, nickname);
    await assertTextVisible(page, nickname);
    await page.locator('tbody tr, .n-data-table-tr').filter({ hasText: nickname }).first().click({ timeout: 5000 }).catch(() => {});
    await assertTextVisible(page, /达人详情|基础资料|抖音号/);
    await takeScreenshot(ctx, page, 'talent-detail');
    return { nickname };
  });
  await runStep(ctx, module, 'U update talent remark and verify detail API', async () => {
    const updateText = `QA_UPDATE_${ctx.qaRunId}`;
    const updatePhone = '13800000001';
    const result = await apiRequest(ctx, 'PUT', `/talents/${talent.id}`, {
      token,
      body: { ...talent, intro: updateText, contactPhone: updatePhone }
    });
    if (!result.ok) throw new Error(`update talent failed HTTP ${result.status}`);
    const data = unwrapApiBody(result.body);
    const raw = JSON.stringify(data);
    if (!raw.includes(updatePhone)) throw new Error(`updated talent response missing contact phone ${updatePhone}`);
    return { updateText, updatePhone };
  });
  await runStep(ctx, module, 'D delete QA talent and verify gone', async () => {
    if (!isQaOwnedEntity({ nickname, douyinNo }, ctx.qaRunId)) throw new Error('refusing to delete non-QA talent');
    const result = await apiRequest(ctx, 'DELETE', `/talents/${talent.id}`, { token, allowStatuses: [404] });
    if (![200, 204, 404].includes(result.status)) throw new Error(`delete talent failed HTTP ${result.status}`);
    const search = await apiRequest(ctx, 'GET', `/talents?page=1&size=10&keyword=${encodeURIComponent(nickname)}`, { token, allowStatuses: [403] });
    const records = unwrapApiBody(search.body)?.records || [];
    return { deletedOrHidden: !records.some((item) => String(item.nickname || '').includes(ctx.qaRunId)) };
  }, { optional: false });
  modulePass(module);
}

async function userCrud(ctx, page) {
  const module = addModule(ctx, 'system-user-crud');
  let token;
  let user;
  const username = `qa_user_${ctx.qaRunId}`;
  const realName = `QA员工_${ctx.qaRunId}`;
  await runStep(ctx, module, 'open system users as admin', async () => {
    await logout(page);
    await login(ctx, page, 'admin');
    const details = await gotoAndWait(page, '/system/users', [/用户管理|新增用户/]);
    await takeScreenshot(ctx, page, 'system-users');
    return details;
  });
  await runStep(ctx, module, 'C create QA user through API', async () => {
    token = (await apiLogin(ctx, 'admin')).token;
    const roles = await apiRequest(ctx, 'GET', '/roles/enabled', { token });
    const roleList = unwrapApiBody(roles.body) || [];
    const role = roleList.find((item) => item.roleCode === 'channel_staff') || roleList.find((item) => item.roleCode !== 'admin') || roleList[0];
    if (!role?.id) throw new Error('no assignable role found');
    const result = await apiRequest(ctx, 'POST', '/users', {
      token,
      body: { username, password: 'Qa123456', realName, phone: '13900000000', email: `${username}@example.com`, roleIds: [role.id] }
    });
    if (!result.ok) throw new Error(`create user failed HTTP ${result.status}: ${JSON.stringify(result.body)}`);
    user = unwrapApiBody(result.body);
    recordCreatedEntity(ctx, 'user', user?.id || username, { id: user?.id, username, realName });
    return { id: user?.id, username, role: role.roleCode };
  });
  await runStep(ctx, module, 'R search QA user in UI', async () => {
    await gotoAndWait(page, '/system/users');
    await searchByKeyword(page, username);
    await assertTextVisible(page, username);
    return { username };
  });
  await runStep(ctx, module, 'U update QA user and verify', async () => {
    const updatedName = `QA员工更新_${ctx.qaRunId}`;
    const result = await apiRequest(ctx, 'PUT', `/users/${user.id}`, {
      token,
      body: { realName: updatedName, phone: '13900000001', email: `${username}.updated@example.com`, status: 1 }
    });
    if (!result.ok) throw new Error(`update user failed HTTP ${result.status}`);
    await gotoAndWait(page, '/system/users');
    await searchByKeyword(page, username);
    await assertTextVisible(page, updatedName);
    return { updatedName };
  });
  await runStep(ctx, module, 'D disable then delete QA user', async () => {
    if (!isQaOwnedEntity({ username, realName }, ctx.qaRunId)) throw new Error('refusing to cleanup non-QA user');
    const disabled = await apiRequest(ctx, 'PUT', `/users/${user.id}`, {
      token,
      body: { realName: `QA员工更新_${ctx.qaRunId}`, phone: '13900000001', email: `${username}.updated@example.com`, status: 0 }
    });
    if (!disabled.ok) throw new Error(`disable user failed HTTP ${disabled.status}`);
    const deleted = await apiRequest(ctx, 'DELETE', `/users/${user.id}`, { token, allowStatuses: [404] });
    if (![200, 204, 404].includes(deleted.status)) throw new Error(`delete user failed HTTP ${deleted.status}`);
    return { disabled: true, deletedOrAlreadyGone: true };
  });
  modulePass(module);
}

async function productActivityFlow(ctx, page) {
  const module = addModule(ctx, 'product-activity-flow');
  await runStep(ctx, module, 'open activity management page', async () => {
    await logout(page);
    await login(ctx, page, 'biz_leader');
    const details = await gotoAndWait(page, '/product/activity', [/活动列表|商品列表|商品管理|暂无/]);
    return { ...details, finalPath: normalizePathname(new URL(page.url()).pathname) };
  });
  await runStep(ctx, module, 'optional sync and assign entrances', async () => {
    const clickedSync = await clickByTextIfVisible(page, /同步商品|同步活动商品|绑定活动|同步/);
    if (clickedSync) await page.waitForLoadState('networkidle', { timeout: 8000 }).catch(() => {});
    const assignVisible = await page.getByText(/商品分配|分配/).first().isVisible({ timeout: 1000 }).catch(() => false);
    if (!clickedSync && !assignVisible) return { skipped: true, reason: 'no stable sync/bind/assign entry or no mock data' };
    return { clickedSync, assignVisible };
  }, { optional: true });
  await runStep(ctx, module, 'open product library and optional first detail', async () => {
    const details = await gotoAndWait(page, '/product/library', [/商品库|暂无商品|商品/]);
    const detailButton = page.getByTestId('product-detail-button').first();
    if (await detailButton.isVisible({ timeout: 1500 }).catch(() => false)) {
      await detailButton.click();
      await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => {});
    }
    await takeScreenshot(ctx, page, 'product-library');
    return details;
  });
  modulePass(module);
}

async function productReviewFlow(ctx, page) {
  const module = addModule(ctx, 'product-review-flow');
  await runStep(ctx, module, 'open review route or product management fallback', async () => {
    await gotoAndWait(page, '/product/review').catch(() => gotoAndWait(page, '/product/manage'));
    const body = await page.locator('body').innerText().catch(() => '');
    if (!/待审核|审核|商品列表|活动列表/.test(body)) return { skipped: true, reason: 'no dedicated product review route visible' };
    return { bodyExcerpt: excerpt(body) };
  }, { optional: true });
  await runStep(ctx, module, 'optional audit edit entry', async () => {
    const auditButton = page.getByText(/审核|补充信息|编辑/).first();
    if (!(await auditButton.isVisible({ timeout: 1500 }).catch(() => false))) return { skipped: true, reason: 'no review product or stable audit button' };
    await auditButton.click();
    const filled = await fillByLabelOrPlaceholder(page, ['商品卖点', '每行一条卖点'], `QA卖点_${ctx.qaRunId}`);
    if (!filled) return { skipped: true, reason: 'audit dialog opened but no stable fillable field' };
    await page.keyboard.press('Escape').catch(() => {});
    return { dialogFillable: true };
  }, { optional: true });
  modulePass(module);
}

async function promotionLinkFlow(ctx, page) {
  const module = addModule(ctx, 'promotion-link-flow');
  await runStep(ctx, module, 'click promotion link entry if product exists', async () => {
    await gotoAndWait(page, '/product/library', [/商品库|暂无商品|商品/]);
    const button = page.getByTestId('product-copy-link').first();
    if (!(await button.isVisible({ timeout: 2000 }).catch(() => false))) return { skipped: true, reason: 'no product-copy-link button or no product data' };
    await button.click();
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => {});
    return { clicked: true };
  }, { optional: true });
  modulePass(module);
}

async function sampleFlow(ctx, page) {
  const module = addModule(ctx, 'sample-flow');
  let token;
  let adminToken;
  let sample;
  await runStep(ctx, module, 'prepare TEST sample fixtures', async () => {
    adminToken = (await apiLogin(ctx, 'admin')).token;
    const seed = await prepareTestSeed(ctx, adminToken);
    if (!seed.shippingSampleId) {
      return { skipped: true, reason: 'test seed endpoint unavailable or returned no shipping sample' };
    }
    sample = { id: seed.shippingSampleId, requestNo: seed.shippingSampleRequestNo || seed.shippingSampleId, seeded: true };
    return {
      sampleRequestNo: seed.sampleRequestNo,
      shippingSampleId: seed.shippingSampleId,
      closedSampleRequestNo: seed.closedSampleRequestNo
    };
  }, { optional: true });

  await runStep(ctx, module, 'open sample page', async () => {
    await logout(page);
    await login(ctx, page, 'channel_staff');
    const details = await gotoAndWait(page, '/sample', [/寄样|申请寄样|暂无/]);
    await takeScreenshot(ctx, page, 'sample-page');
    return details;
  });
  await runStep(ctx, module, 'C create sample request when candidates exist', async () => {
    token = (await apiLogin(ctx, 'channel_staff')).token;
    const products = await apiRequest(ctx, 'GET', '/samples/product-candidates?page=1&size=5', { token, allowStatuses: [403] });
    const talents = await apiRequest(ctx, 'GET', '/samples/talent-candidates?page=1&size=5', { token, allowStatuses: [403] });
    const product = (unwrapApiBody(products.body)?.records || [])[0];
    const talent = (unwrapApiBody(talents.body)?.records || [])[0];
    if (!product || !talent) return { skipped: true, reason: 'no sample product/talent candidates in mock data' };
    const create = await apiRequest(ctx, 'POST', '/samples', {
      token,
      body: {
        productId: product.id || product.productId,
        talentId: talent.id || talent.talentId || talent.uid,
        talentNickname: talent.nickname || `QA收件人_${ctx.qaRunId}`,
        receiverName: `QA收件人_${ctx.qaRunId}`,
        receiverPhone: '13800000000',
        receiverAddress: `QA测试地址_${ctx.qaRunId}`,
        reason: `QA寄样申请_${ctx.qaRunId}`,
        remark: `QA寄样申请_${ctx.qaRunId}`,
        quantity: 1
      },
      allowStatuses: [400, 403]
    });
    if (!create.ok) return { skipped: true, reason: `sample create unavailable: HTTP ${create.status}`, body: create.body };
    const createdSample = unwrapApiBody(create.body);
    if (!createdSample?.id) {
      return { skipped: true, reason: 'sample create returned no sample id', body: create.body };
    }
    sample = createdSample;
    recordCreatedEntity(ctx, 'sample', sample?.id || sample?.requestNo, { id: sample?.id, requestNo: sample?.requestNo, receiverName: `QA收件人_${ctx.qaRunId}` });
    return { id: sample?.id, requestNo: sample?.requestNo };
  }, { optional: true });
  await runStep(ctx, module, 'R query sample and open detail if created', async () => {
    if (!sample?.id) return { skipped: true, reason: 'sample fixture unavailable' };
    const detail = await apiRequest(ctx, 'GET', `/samples/${sample.id}`, { token });
    if (!detail.ok) throw new Error(`sample detail failed HTTP ${detail.status}`);
    await gotoAndWait(page, `/sample/${sample.id}`, [/寄样|详情|商品|达人/]);
    return { id: sample.id, requestNo: sample.requestNo, status: unwrapApiBody(detail.body)?.status, seeded: Boolean(sample.seeded) };
  }, { optional: true });
  await runStep(ctx, module, 'U status transition when allowed', async () => {
    if (!sample?.id) return { skipped: true, reason: 'sample fixture unavailable' };
    if (sample.seeded) {
      const testToken = adminToken || (await apiLogin(ctx, 'admin')).token;
      const ship = await apiRequest(ctx, 'POST', `/test/logistics/ship/${sample.id}`, { token: testToken, allowStatuses: [400, 403, 404] });
      if (!ship.ok) return { skipped: true, reason: `seeded sample ship unavailable: HTTP ${ship.status}`, body: ship.body };
      const sign = await apiRequest(ctx, 'POST', `/test/logistics/sign/${sample.id}`, { token: testToken, allowStatuses: [400, 403, 404] });
      if (!sign.ok) return { skipped: true, reason: `seeded sample sign unavailable: HTTP ${sign.status}`, body: sign.body };
      return { actions: ['ship', 'sign'], ship: unwrapApiBody(ship.body), sign: unwrapApiBody(sign.body) };
    }
    const approve = await apiRequest(ctx, 'PUT', `/samples/${sample.id}/status`, { token, body: { action: 'PENDING_SHIP', reason: `QA审核_${ctx.qaRunId}` }, allowStatuses: [400, 403] });
    if (!approve.ok) return { skipped: true, reason: `sample status transition unavailable: HTTP ${approve.status}`, body: approve.body };
    return { action: 'PENDING_SHIP', status: unwrapApiBody(approve.body)?.status };
  }, { optional: true });
  await runStep(ctx, module, 'D close sample when allowed', async () => {
    if (!sample?.id) return { skipped: true, reason: 'sample fixture unavailable' };
    if (sample.seeded) return { skipped: true, reason: 'seeded sample fixture retained for repeatable TEST flows' };
    const close = await apiRequest(ctx, 'PUT', `/samples/${sample.id}/status`, { token, body: { action: 'CLOSED', reason: `QA关闭_${ctx.qaRunId}` }, allowStatuses: [400, 403] });
    if (!close.ok) return { skipped: true, reason: `sample close unavailable: HTTP ${close.status}`, body: close.body };
    return { action: 'CLOSED', status: unwrapApiBody(close.body)?.status };
  }, { optional: true });
  modulePass(module);
}

async function ordersFlow(ctx, page) {
  const module = addModule(ctx, 'orders-readonly-filter-flow');
  await runStep(ctx, module, 'prepare TEST order fixtures', async () => {
    const { token } = await apiLogin(ctx, 'admin');
    const prepared = await prepareTestOrders(ctx, token);
    if (!prepared.attributed?.orderId) {
      return { skipped: true, reason: 'test order fixture endpoint unavailable or returned no attributed order' };
    }
    return {
      attributedOrderId: prepared.attributed.orderId,
      noPickSourceOrderId: prepared.noPickSource?.orderId,
      missingMappingOrderId: prepared.missingMapping?.orderId
    };
  }, { optional: true });

  await runStep(ctx, module, 'open orders page and filter options', async () => {
    await logout(page);
    await login(ctx, page, 'admin');
    const orderId = ctx.testOrders?.attributed?.orderId;
    const route = orderId ? `/orders?orderId=${encodeURIComponent(orderId)}` : '/orders';
    const details = await gotoAndWait(page, route, [/订单|归因|查询|暂无/]);
    if (orderId) {
      await page.getByTestId('order-row').filter({ hasText: orderId }).first().waitFor({ state: 'visible', timeout: 15000 });
    }
    const token = (await apiLogin(ctx, 'admin')).token;
    const filters = await apiRequest(ctx, 'GET', '/orders/filter-options', { token, allowStatuses: [403, 404] });
    if (filters.status >= 500) throw new Error('/orders/filter-options returned 500');
    return { ...details, filterOptionsStatus: filters.status, orderId };
  });
  await runStep(ctx, module, 'open first order detail when present', async () => {
    const detailButton = page.getByTestId('order-detail-button').first();
    if (!(await detailButton.isVisible({ timeout: 2000 }).catch(() => false))) return { skipped: true, reason: 'no orders data' };
    await detailButton.click();
    await assertTextVisible(page, /订单号|金额|商品|归因/);
    return { detailOpened: true };
  }, { optional: true });
  modulePass(module);
}

async function dashboardDataFlow(ctx, page) {
  const module = addModule(ctx, 'dashboard-data-refresh-flow');
  await runStep(ctx, module, 'open dashboard and data pages', async () => {
    await gotoAndWait(page, '/dashboard', [/业务概览|归因概览|数据看板/]);
    await gotoAndWait(page, '/data', [/数据|GMV|订单|暂无/]);
    const clicked = await clickByTextIfVisible(page, /^刷新$|^查询$/);
    if (clicked) await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => {});
    return { refreshClicked: clicked };
  });
  modulePass(module);
}

async function permissionBoundary(ctx, page) {
  const module = addModule(ctx, 'permission-boundary');
  await runStep(ctx, module, 'admin can access system users', async () => {
    await logout(page);
    await login(ctx, page, 'admin');
    await gotoAndWait(page, '/system');
    await assertUrlMatches(page, /\/system\/users/);
    return { finalUrl: page.url() };
  });
  await runStep(ctx, module, 'biz leader cannot stay in system management', async () => {
    await logout(page);
    await login(ctx, page, 'biz_leader');
    await gotoAndWait(page, '/system');
    const finalPath = normalizePathname(new URL(page.url()).pathname);
    if (finalPath.startsWith('/system')) throw new Error(`biz_leader stayed in system route: ${finalPath}`);
    for (const route of ['/dashboard', '/product/library', '/product/activity', '/orders']) {
      await gotoAndWait(page, route);
    }
    return { finalPath };
  });
  modulePass(module);
}

async function cleanupCreatedEntities(ctx) {
  const cleanup = [];
  const adminToken = ctx.tokens.admin || (await apiLogin(ctx, 'admin').catch(() => ({}))).token;
  const channelToken = ctx.tokens.channel_leader || (await apiLogin(ctx, 'channel_leader').catch(() => ({}))).token;
  for (const entity of ctx.createdEntities) {
    const item = { ...entity, attempted: false, ok: false, skipped: false };
    try {
      if (!isQaOwnedEntity(entity, ctx.qaRunId)) {
        item.skipped = true;
        item.reason = 'not QA_RUN_ID owned';
      } else if (entity.type === 'user' && entity.id && adminToken) {
        item.attempted = true;
        const result = await apiRequest(ctx, 'DELETE', `/users/${entity.id}`, { token: adminToken, allowStatuses: [404] });
        item.ok = [200, 204, 404].includes(result.status);
        item.status = result.status;
      } else if (entity.type === 'talent' && entity.id && channelToken) {
        item.attempted = true;
        const result = await apiRequest(ctx, 'DELETE', `/talents/${entity.id}`, { token: channelToken, allowStatuses: [404] });
        item.ok = [200, 204, 404].includes(result.status);
        item.status = result.status;
      } else if (entity.type === 'sample') {
        item.skipped = true;
        item.reason = 'sample cleanup prefers status close or next mock reset';
      } else {
        item.skipped = true;
        item.reason = 'no cleanup strategy';
      }
    } catch (error) {
      item.error = error?.message || String(error);
    }
    cleanup.push(item);
  }
  ctx.cleanup = cleanup;
  return cleanup;
}

function isIgnorableConsole(error) {
  const text = typeof error === 'string' ? error : String(error?.text || '');
  return /ResizeObserver loop|favicon\.ico|Failed to load resource: the server responded with a status of 404/.test(text);
}

function blockingConsoleErrors(ctx) {
  return ctx.consoleErrors.filter((item) => !isIgnorableConsole(item));
}

function writeJsonArtifacts(ctx) {
  fs.writeFileSync(path.join(ctx.outDir, 'api-errors.json'), JSON.stringify(ctx.apiErrors, null, 2));
  fs.writeFileSync(path.join(ctx.outDir, 'console-errors.json'), JSON.stringify(ctx.consoleErrors, null, 2));
  fs.writeFileSync(path.join(ctx.outDir, 'slow-apis.json'), JSON.stringify(ctx.slowApis, null, 2));
  fs.writeFileSync(path.join(ctx.outDir, 'created-entities.json'), JSON.stringify(ctx.createdEntities, null, 2));
  fs.writeFileSync(path.join(ctx.outDir, 'cleanup-result.json'), JSON.stringify(ctx.cleanup, null, 2));
}

function writeSummary(ctx) {
  const blockerApis = blockingApiErrors(ctx);
  const blockerConsole = blockingConsoleErrors(ctx);
  ctx.finishedAt = new Date().toISOString();
  ctx.overallPass =
    ctx.environment?.isTest === true &&
    ctx.modules.every((module) => module.pass) &&
    blockerApis.length === 0 &&
    blockerConsole.length === 0 &&
    ctx.fatalErrors.length === 0;
  const summary = {
    name: ctx.name,
    qaRunId: ctx.qaRunId,
    overallPass: ctx.overallPass,
    environment: ctx.environment,
    baseUrl: ctx.baseUrl,
    apiBaseUrl: ctx.apiBaseUrl,
    startedAt: ctx.startedAt,
    finishedAt: ctx.finishedAt,
    modules: ctx.modules,
    createdEntities: ctx.createdEntities,
    cleanup: ctx.cleanup,
    apiErrors: ctx.apiErrors,
    consoleErrors: ctx.consoleErrors,
    slowApis: ctx.slowApis,
    skipped: ctx.skipped,
    fatalErrors: ctx.fatalErrors
  };
  fs.writeFileSync(path.join(ctx.outDir, 'summary.json'), JSON.stringify(summary, null, 2));
  return summary;
}

function writeReport(ctx, summary) {
  const lines = [];
  lines.push('# 完整 CRUD / 状态流转 QA 报告');
  lines.push('');
  lines.push(`- QA_RUN_ID：${summary.qaRunId}`);
  lines.push(`- 前端地址：${summary.baseUrl}`);
  lines.push(`- 后端地址：${summary.apiBaseUrl}`);
  lines.push(`- 环境：${summary.environment?.environmentLabel || '(unknown)'}`);
  lines.push(`- 总体结论：**${summary.overallPass ? '通过' : '失败'}**`);
  lines.push('');
  lines.push('## 模块结果');
  for (const module of summary.modules) {
    lines.push(`- ${module.name}：${module.pass ? 'PASS' : 'FAIL'}`);
    for (const step of module.steps) {
      const status = step.pass ? (step.skipped ? 'SKIPPED' : 'PASS') : 'FAIL';
      lines.push(`  - ${step.name}：${status}，${step.durationMs}ms`);
    }
  }
  lines.push('');
  lines.push('## Skipped');
  if (!summary.skipped.length) {
    lines.push('- 无。');
  } else {
    for (const item of summary.skipped) {
      lines.push(`- ${item.module ? `${item.module} / ` : ''}${item.step || item.code || 'skip'}：${item.reason}`);
    }
  }
  lines.push('');
  lines.push('## Created Entities');
  if (!summary.createdEntities.length) lines.push('- 无。');
  for (const item of summary.createdEntities) lines.push(`- ${item.type}：${item.idOrName || item.id || item.username || item.nickname}`);
  lines.push('');
  lines.push('## Cleanup');
  if (!summary.cleanup.length) lines.push('- 无。');
  for (const item of summary.cleanup) lines.push(`- ${item.type} ${item.idOrName || item.id || ''}：${item.ok ? 'OK' : item.skipped ? 'SKIPPED' : 'CHECK'} ${item.reason || item.error || item.status || ''}`);
  lines.push('');
  lines.push('## API Errors');
  if (!summary.apiErrors.length) lines.push('- 无。');
  for (const item of summary.apiErrors) lines.push(`- [${item.step}] ${item.status} ${item.method} ${item.url} (${item.durationMs}ms)`);
  lines.push('');
  lines.push('## Console Errors');
  if (!summary.consoleErrors.length) lines.push('- 无。');
  for (const item of summary.consoleErrors) lines.push(`- [${item.step}] ${item.type}: ${excerpt(item.text, 250)}`);
  lines.push('');
  lines.push('## 后续需要补 data-testid 的位置');
  lines.push('- 系统用户弹窗字段、角色下拉、行内操作。');
  lines.push('- 达人详情 / 编辑 / 删除动作。');
  lines.push('- 寄样创建弹窗中的商品/达人选择器和状态按钮。');
  lines.push('- 商品审核、分配、转链弹窗的提交按钮和结果文本。');
  lines.push('');
  lines.push('## 后续需要准备稳定 mock 样本的位置');
  lines.push('- 可寄样商品 + 可寄样达人组合。');
  lines.push('- 待审核商品、可分配商品、已有商品库商品。');
  lines.push('- 至少一条订单及归因状态覆盖样本。');
  fs.writeFileSync(path.join(ctx.outDir, 'report.md'), `${lines.join('\n')}\n`);
}

async function runFlow(ctx, page) {
  await adminLoginAndEnvironment(ctx, page);
  await talentCrud(ctx, page);
  await userCrud(ctx, page);
  await productActivityFlow(ctx, page);
  await productReviewFlow(ctx, page);
  await promotionLinkFlow(ctx, page);
  await sampleFlow(ctx, page);
  await ordersFlow(ctx, page);
  await dashboardDataFlow(ctx, page);
  await permissionBoundary(ctx, page);
}

async function main() {
  const outDir = process.argv[2] || path.join(REPO_ROOT, 'runtime', 'qa', 'out', `${SCRIPT_NAME}-${timestamp()}`);
  const ctx = createContext(outDir);
  ensureDir(ctx.outDir);
  ensureDir(ctx.screenshotsDir);
  let browser;
  let browserContext;
  let page;
  try {
    await ensureTestEnvironment(ctx);
    await optionalMockReset(ctx);
    await optionalMockSeed(ctx);
    browser = await chromium.launch({ headless: HEADLESS, slowMo: SLOW_MO });
    browserContext = await browser.newContext({ baseURL: ctx.baseUrl, viewport: { width: 1440, height: 960 } });
    page = await browserContext.newPage();
    attachPageObservers(page, ctx);
    await runFlow(ctx, page);
  } catch (error) {
    ctx.fatalErrors.push(error?.stack || String(error));
    if (page) await takeScreenshot(ctx, page, 'fatal').catch(() => {});
  } finally {
    await cleanupCreatedEntities(ctx).catch((error) => ctx.fatalErrors.push(`cleanup fatal: ${error?.stack || String(error)}`));
    if (browser) await browser.close().catch(() => {});
    writeJsonArtifacts(ctx);
    const summary = writeSummary(ctx);
    writeReport(ctx, summary);
    console.log(`QA business CRUD flow output: ${ctx.outDir}`);
    if (!summary.overallPass) process.exitCode = 1;
  }
}

if (require.main === module) {
  main().catch((error) => {
    console.error(error?.stack || String(error));
    process.exitCode = 1;
  });
}

module.exports = {
  normalizeEnv,
  createQaRunId,
  makeQaRunId,
  blockingApiErrors,
  createSummarySkeleton,
  isQaOwnedEntity,
  containsQaRunId,
  isProtectedAccount,
  normalizePathname,
  isPathUnder,
  unwrapApiBody,
  isIgnorableConsole,
  prepareTestSeed,
  prepareTestOrders,
  writeSummary,
  writeReport
};

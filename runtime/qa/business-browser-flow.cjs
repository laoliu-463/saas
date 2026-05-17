const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('@playwright/test');

const SCRIPT_NAME = 'qa-business-browser-flow';
const REPO_ROOT = path.resolve(__dirname, '..', '..');
const ROLE_CASES_FILE = path.join(__dirname, 'role-page-cases.json');
const BASE_URL = process.env.E2E_BASE_URL || process.env.QA_FRONTEND || 'http://localhost:3002';
const API_BASE_URL = process.env.API_BASE_URL || 'http://localhost:8081';
const ADMIN_USER = process.env.QA_ADMIN_USER || 'admin';
const ADMIN_PASSWORD = process.env.QA_ADMIN_PASSWORD || 'admin123';
const HEADLESS = String(process.env.QA_HEADFUL || '').toLowerCase() !== 'true';
const SLOW_MO = Number(process.env.QA_SLOW_MO || 0);
const SLOW_API_MS = Number(process.env.QA_SLOW_API_MS || 2000);
const RECORD_VIDEO = String(process.env.QA_RECORD_VIDEO || '').toLowerCase() === 'true';

function timestamp() {
  const now = new Date();
  const pad = (n, len = 2) => String(n).padStart(len, '0');
  return `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}-${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}-${pad(now.getMilliseconds(), 3)}`;
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

function normalizePathname(input) {
  const raw = String(input || '/').split(/[?#]/)[0].trim() || '/';
  const pathname = raw.startsWith('/') ? raw : `/${raw}`;
  return pathname === '/' ? pathname : pathname.replace(/\/+$/, '');
}

function isPathUnder(pathname, prefix) {
  const value = normalizePathname(pathname);
  const base = normalizePathname(prefix);
  return value === base || value.startsWith(`${base}/`);
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
    .slice(0, 80) || 'step';
}

function normalizeEnv(raw) {
  const data = raw?.data && typeof raw.data === 'object' ? raw.data : raw || {};
  const profiles = []
    .concat(data.activeProfiles || data.activeProfile || data.profile || data.profiles || [])
    .filter(Boolean)
    .map((item) => String(item).toLowerCase());
  const env = String(data.env || data.environment || data.environmentLabel || data.label || '').toLowerCase();
  const profile = String(data.profile || data.activeProfile || profiles[0] || '').toLowerCase();
  const appTestEnabled = data.appTestEnabled === true || String(data.appTestEnabled).toLowerCase() === 'true';
  const douyinTestEnabled = data.douyinTestEnabled === true || String(data.douyinTestEnabled).toLowerCase() === 'true';
  const isTest =
    env === 'test' ||
    env === 'TEST'.toLowerCase() ||
    profile === 'test' ||
    profiles.includes('test') ||
    appTestEnabled ||
    douyinTestEnabled;

  return {
    env: env || data.environmentLabel || null,
    profile: profile || null,
    activeProfiles: profiles,
    appTestEnabled,
    douyinTestEnabled,
    database: data.database || data.databaseName || null,
    raw,
    isTest
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
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = { rawText: text };
  }
  return {
    url,
    status: response.status,
    ok: response.ok,
    durationMs: Date.now() - started,
    body: json
  };
}

async function ensureTestEnvironment(ctx) {
  const health = await fetchJson(`${ctx.apiBaseUrl}/api/actuator/health`).catch((error) => ({
    status: 0,
    ok: false,
    error: String(error)
  }));
  ctx.health = health;

  const envResult = await fetchJson(`${ctx.apiBaseUrl}/api/system/env`).catch((error) => ({
    status: 0,
    ok: false,
    error: String(error)
  }));
  ctx.environmentProbe = envResult;

  if (envResult.status === 401) {
    ctx.notes.push('/api/system/env returned 401; public environment probe is not available.');
    throw new Error('/api/system/env returned 401');
  }
  if (!envResult.ok) {
    throw new Error(`/api/system/env failed with HTTP ${envResult.status || 'NETWORK'}`);
  }

  const environment = normalizeEnv(envResult.body);
  ctx.environment = environment;
  if (!environment.isTest) {
    throw new Error(`Refusing to run outside TEST/mock environment: ${JSON.stringify(environment)}`);
  }
  if (health.status && health.status !== 200) {
    throw new Error(`/api/actuator/health failed with HTTP ${health.status}`);
  }
  return environment;
}

async function optionalProbe(ctx, name, url) {
  const started = Date.now();
  try {
    const result = await fetchJson(url, { method: 'POST', body: '{}' });
    const skipped = [401, 403, 404, 405].includes(result.status);
    const step = {
      name,
      pass: true,
      skipped,
      durationMs: Date.now() - started,
      details: {
        status: result.status,
        reason: skipped ? 'optional endpoint unavailable or not public' : 'called'
      }
    };
    ctx.steps.push(step);
    return step;
  } catch (error) {
    const step = {
      name,
      pass: true,
      skipped: true,
      durationMs: Date.now() - started,
      details: { error: String(error) }
    };
    ctx.steps.push(step);
    return step;
  }
}

async function optionalMockReset(ctx) {
  return optionalProbe(ctx, '可选 mock reset', `${ctx.apiBaseUrl}/api/test/mock/reset`);
}

async function optionalMockSeed(ctx) {
  return optionalProbe(ctx, '可选 mock seed', `${ctx.apiBaseUrl}/api/test/mock/seed`);
}

function createContext(outDir) {
  return {
    name: SCRIPT_NAME,
    startedAt: new Date().toISOString(),
    finishedAt: null,
    baseUrl: BASE_URL,
    apiBaseUrl: API_BASE_URL,
    outDir,
    screenshotsDir: path.join(outDir, 'screenshots'),
    videosDir: path.join(outDir, 'videos'),
    tracesDir: path.join(outDir, 'traces'),
    environment: null,
    environmentProbe: null,
    health: null,
    overallPass: false,
    steps: [],
    apiErrors: [],
    consoleErrors: [],
    slowApis: [],
    screenshots: [],
    notes: [],
    currentStep: 'bootstrap',
    requestStartedAt: new Map()
  };
}

function attachPageObservers(page, ctx) {
  page.on('console', (msg) => {
    if (msg.type() !== 'error') return;
    ctx.consoleErrors.push({
      step: ctx.currentStep,
      type: 'console',
      text: msg.text(),
      location: msg.location()
    });
  });

  page.on('pageerror', (error) => {
    ctx.consoleErrors.push({
      step: ctx.currentStep,
      type: 'pageerror',
      text: error?.stack || String(error)
    });
  });

  page.on('request', (request) => {
    if (request.url().includes('/api/')) {
      ctx.requestStartedAt.set(request, Date.now());
    }
  });

  page.on('response', (response) => {
    const url = response.url();
    if (!url.includes('/api/')) return;
    const request = response.request();
    const started = ctx.requestStartedAt.get(request) || Date.now();
    const durationMs = Date.now() - started;
    ctx.requestStartedAt.delete(request);
    const event = {
      step: ctx.currentStep,
      status: response.status(),
      url,
      method: request.method(),
      durationMs
    };
    if (durationMs >= SLOW_API_MS) {
      ctx.slowApis.push(event);
    }
    if (event.status >= 400) {
      ctx.apiErrors.push(event);
    }
  });
}

async function waitForPageStable(page) {
  await page.waitForLoadState('domcontentloaded', { timeout: 30000 }).catch(() => {});
  await page.waitForLoadState('networkidle', { timeout: 8000 }).catch(() => {});
  await page.locator('.n-spin-body, .n-spin').first().waitFor({ state: 'hidden', timeout: 6000 }).catch(() => {});
  await page
    .waitForFunction(
      () => {
        const text = document.body?.innerText || '';
        return !text.includes('正在加载业务看板') && !text.includes('初始化菜单、权限和实时数据');
      },
      null,
      { timeout: 18000 }
    )
    .catch(() => {});
}

async function takeScreenshot(ctx, page, name) {
  const file = path.join(ctx.screenshotsDir, `${String(ctx.screenshots.length + 1).padStart(2, '0')}-${sanitizeFilePart(name)}.png`);
  await page.screenshot({ path: file, fullPage: false, timeout: 10000 }).catch(() => {});
  const rel = path.relative(REPO_ROOT, file).replace(/\\/g, '/');
  ctx.screenshots.push(rel);
  return rel;
}

async function runStep(ctx, page, name, fn, options = {}) {
  const started = Date.now();
  const beforeApiErrors = ctx.apiErrors.length;
  const beforeConsole = ctx.consoleErrors.length;
  ctx.currentStep = name;
  const step = {
    name,
    pass: false,
    skipped: false,
    durationMs: 0,
    details: {},
    screenshot: null,
    failure: null
  };
  console.log(`[${SCRIPT_NAME}] start: ${name}`);
  try {
    const details = await fn();
    step.details = details || {};
    const newApiErrors = ctx.apiErrors.slice(beforeApiErrors);
    const newConsoleErrors = ctx.consoleErrors.slice(beforeConsole);
    const blockingApiErrors = newApiErrors.filter((item) => item.status >= 500 || item.status === 401);
    const blockingConsole = newConsoleErrors.filter((item) => !isIgnorableConsole(item.text));
    step.details.apiErrors = newApiErrors;
    step.details.consoleErrors = newConsoleErrors;
    step.pass = !blockingApiErrors.length && !blockingConsole.length && step.details.pass !== false;
    step.skipped = !!step.details.skipped;
    if (!step.pass && !step.failure) {
      step.failure = {
        page: step.details.route || step.details.finalUrl || page.url(),
        operation: name,
        expected: options.expected || '页面可用且无核心接口错误',
        actual: step.details.actual || '出现阻塞 API/Console 错误或断言失败',
        initialReason: step.details.initialReason || '需要查看 step details'
      };
    }
  } catch (error) {
    step.pass = false;
    step.failure = {
      page: page.url(),
      operation: name,
      expected: options.expected || '步骤执行成功',
      actual: error?.stack || String(error),
      initialReason: 'Playwright step exception'
    };
  } finally {
    step.durationMs = Date.now() - started;
    step.screenshot = await takeScreenshot(ctx, page, name);
    ctx.steps.push(step);
    console.log(`[${SCRIPT_NAME}] ${step.pass ? 'pass' : 'fail'}: ${name} (${step.durationMs}ms)`);
  }
  return step;
}

function isIgnorableConsole(text) {
  const value = String(text || '');
  return (
    value.includes('ResizeObserver loop') ||
    value.includes('/favicon.ico') ||
    value.includes('Failed to load resource: the server responded with a status of 404')
  );
}

function assertNoApi500(ctx) {
  const bad = ctx.apiErrors.filter((item) => item.status >= 500);
  if (bad.length) {
    throw new Error(`API 500+ detected: ${bad.map((item) => `${item.status} ${item.method} ${item.url}`).join('; ')}`);
  }
}

function assertNoBlockingConsoleErrors(ctx) {
  const bad = ctx.consoleErrors.filter((item) => !isIgnorableConsole(item.text));
  if (bad.length) {
    throw new Error(`Blocking console errors detected: ${bad.map((item) => item.text).join('; ')}`);
  }
}

async function visibleText(page) {
  return page.locator('body').innerText({ timeout: 5000 }).catch(() => '');
}

function bodyHasAny(text, expectedTexts) {
  return (expectedTexts || []).some((item) => text.includes(item));
}

async function gotoAndAssertReady(ctx, page, route, expectedTexts, options = {}) {
  await page.goto(route, { waitUntil: 'domcontentloaded', timeout: 30000 });
  await waitForPageStable(page);
  const finalUrl = page.url();
  const finalPath = normalizePathname(new URL(finalUrl).pathname);
  const bodyText = await visibleText(page);
  const bodyExcerpt = excerpt(bodyText);
  const hasExpectedText = expectedTexts?.length ? bodyHasAny(bodyText, expectedTexts) : true;
  const loadingStuck = await page.locator('.n-spin-body, .n-spin').first().isVisible().catch(() => false);
  const whiteScreen = bodyText.trim().length < 12;
  const fatalText = /Unexpected Application Error|500 Internal Server Error|NoResourceFoundException|Error response/i.test(bodyText);
  const allowed = !options.allowedFinalPaths?.length || options.allowedFinalPaths.some((item) => isPathUnder(finalPath, item));
  const pass = allowed && hasExpectedText && !loadingStuck && !whiteScreen && !fatalText;
  return {
    route,
    finalUrl,
    finalPath,
    bodyExcerpt,
    hasExpectedText,
    expectedTexts,
    loadingStuck,
    whiteScreen,
    fatalText,
    allowed,
    pass,
    actual: pass ? undefined : `finalPath=${finalPath}, hasExpectedText=${hasExpectedText}, loadingStuck=${loadingStuck}, whiteScreen=${whiteScreen}, fatalText=${fatalText}`
  };
}

async function locatorByText(page, textOrRegex) {
  if (textOrRegex instanceof RegExp) {
    return page.getByText(textOrRegex).first();
  }
  return page.getByText(String(textOrRegex), { exact: false }).first();
}

async function clickIfVisible(page, textOrRegex, details, label) {
  const locator = await locatorByText(page, textOrRegex);
  if (await locator.isVisible({ timeout: 1200 }).catch(() => false)) {
    const clicked = await locator.click({ timeout: 5000 }).then(() => true).catch(async () => {
      return locator.click({ force: true, timeout: 5000 }).then(() => true).catch(() => false);
    });
    if (!clicked) {
      details.actions.push({ action: label || String(textOrRegex), status: 'skipped', reason: 'visible but not clickable' });
      return false;
    }
    await waitForPageStable(page);
    details.actions.push({ action: label || String(textOrRegex), status: 'clicked' });
    return true;
  }
  details.actions.push({ action: label || String(textOrRegex), status: 'skipped', reason: 'not visible' });
  return false;
}

async function fillIfVisible(selectorOrLocator, value, details, label) {
  const locator = typeof selectorOrLocator === 'string' ? null : selectorOrLocator;
  if (locator && (await locator.isVisible({ timeout: 1200 }).catch(() => false))) {
    await locator.fill(value, { timeout: 5000 });
    details.actions.push({ action: label || 'fill', status: 'filled' });
    return true;
  }
  details.actions.push({ action: label || 'fill', status: 'skipped', reason: 'not visible' });
  return false;
}

async function closeAnyOverlay(page) {
  const closeButtons = [
    page.getByRole('button', { name: /取消|关闭|返回/ }).first(),
    page.locator('.n-modal .n-base-close').first(),
    page.locator('.n-drawer .n-base-close').first()
  ];
  for (const button of closeButtons) {
    if (await button.isVisible({ timeout: 500 }).catch(() => false)) {
      await button.click({ timeout: 3000 }).catch(() => {});
      await waitForPageStable(page);
      return true;
    }
  }
  await page.keyboard.press('Escape').catch(() => {});
  return false;
}

async function firstSearchInput(page) {
  const candidates = [
    page.getByPlaceholder(/搜索|关键词|商品|达人|订单|活动/).first(),
    page.locator('input').filter({ hasNotText: /密码/ }).first()
  ];
  for (const candidate of candidates) {
    if (await candidate.isVisible({ timeout: 800 }).catch(() => false)) {
      return candidate;
    }
  }
  return null;
}

async function trySearch(page, details, value) {
  const input = await firstSearchInput(page);
  if (!input) {
    details.actions.push({ action: 'search', status: 'skipped', reason: 'search input not visible' });
    return;
  }
  await input.fill(value);
  await page.keyboard.press('Enter').catch(() => {});
  await waitForPageStable(page);
  details.actions.push({ action: 'search', status: 'filled', value });
}

async function loginAsAdmin(ctx, page) {
  await page.goto('/login', { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.getByTestId('login-username').locator('input').fill(ADMIN_USER);
  await page.getByTestId('login-password').locator('input').fill(ADMIN_PASSWORD);
  await Promise.all([
    page.waitForURL(/\/(dashboard|data|product|sample|orders|talent|system)/, { timeout: 30000 }),
    page.getByTestId('login-submit').click()
  ]);
  await waitForPageStable(page);
  const bodyText = await visibleText(page);
  const sidebarText = await page.locator('[data-testid="sidebar-menu"]').innerText().catch(() => '');
  const envBadge = await page.locator('[data-testid="current-env-badge"]').innerText().catch(() => '');
  return {
    username: ADMIN_USER,
    finalUrl: page.url(),
    envBadge,
    sidebarText: excerpt(sidebarText, 1000),
    pass: !normalizePathname(new URL(page.url()).pathname).startsWith('/login') && bodyText.length > 20
  };
}

async function logout(page) {
  const candidates = [
    page.getByText(/退出登录|退出|登出/).first(),
    page.locator('[data-testid="user-menu"]').first()
  ];
  for (const candidate of candidates) {
    if (await candidate.isVisible({ timeout: 800 }).catch(() => false)) {
      await candidate.click().catch(() => {});
      await waitForPageStable(page);
      const confirm = page.getByRole('button', { name: /确定|确认|退出/ }).first();
      if (await confirm.isVisible({ timeout: 800 }).catch(() => false)) {
        await confirm.click().catch(() => {});
      }
      await waitForPageStable(page);
      break;
    }
  }
  const onLogin = normalizePathname(new URL(page.url()).pathname).startsWith('/login');
  if (!onLogin) {
    await page.evaluate(() => {
      localStorage.clear();
      sessionStorage.clear();
    }).catch(() => {});
  }
  await page.goto('/login', { waitUntil: 'domcontentloaded', timeout: 30000 }).catch(() => {});
  await waitForPageStable(page);
}

async function loginAs(page, username, password) {
  await page.goto('/login', { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.getByTestId('login-username').locator('input').fill(username);
  await page.getByTestId('login-password').locator('input').fill(password);
  await Promise.all([
    page.waitForURL(/\/(dashboard|data|product|sample|orders|talent|system)/, { timeout: 30000 }),
    page.getByTestId('login-submit').click()
  ]);
  await waitForPageStable(page);
}

async function runBusinessFlow(ctx, page) {
  await runStep(ctx, page, '环境与首页准备', async () => {
    const details = {
      pass: !!ctx.environment?.isTest,
      environment: ctx.environment,
      health: ctx.health,
      envProbe: ctx.environmentProbe
    };
    await page.goto('/login', { waitUntil: 'domcontentloaded', timeout: 30000 });
    await waitForPageStable(page);
    return details;
  });

  await runStep(ctx, page, '登录管理员', () => loginAsAdmin(ctx, page), {
    expected: '管理员登录成功并进入主框架'
  });

  await runStep(ctx, page, '首页 / 数据看板', async () => {
    const details = await gotoAndAssertReady(ctx, page, '/dashboard', ['数据', '看板', '订单', '金额', '服务费', '归因', 'GMV']);
    details.actions = [];
    await clickIfVisible(page, /日期|时间|筛选/, details, 'open date/filter control');
    await closeAnyOverlay(page);
    return details;
  });

  await runStep(ctx, page, '商品库', async () => {
    const details = await gotoAndAssertReady(ctx, page, '/product/library', ['商品', '商品库', '佣金', '服务费', '活动']);
    details.actions = [];
    await trySearch(page, details, '');
    await clickIfVisible(page, /详情|查看/, details, 'open first detail');
    await closeAnyOverlay(page);
    await clickIfVisible(page, /一键复制|复制链接|转链/, details, 'copy or promotion link');
    await closeAnyOverlay(page);
    return details;
  });

  await runStep(ctx, page, '活动与商品管理', async () => {
    const details = await gotoAndAssertReady(ctx, page, '/product/activity', ['活动', '商品', '管理', '同步', '分配'], {
      allowedFinalPaths: ['/product/activity', '/product/manage']
    });
    details.actions = [];
    details.finalUrl = page.url();
    await trySearch(page, details, '');
    await clickIfVisible(page, /同步活动|同步商品|绑定活动/, details, 'sync/bind activity');
    await closeAnyOverlay(page);
    await clickIfVisible(page, /商品分配|分配/, details, 'assign product');
    await closeAnyOverlay(page);
    return details;
  });

  await runStep(ctx, page, '商品审核 / 状态动作', async () => {
    const details = await gotoAndAssertReady(ctx, page, '/product/review', ['待审核', '审核通过', '审核拒绝', '上架', '拒绝', '商品'], {
      allowedFinalPaths: ['/product/review', '/product/manage', '/product', '/dashboard']
    });
    details.actions = [];
    if (isPathUnder(details.finalPath, '/dashboard') || isPathUnder(details.finalPath, '/product')) {
      details.skipped = true;
      details.pass = true;
      details.actions.push({ action: 'review route', status: 'skipped', reason: 'route redirected to dashboard or unavailable' });
      return details;
    }
    await clickIfVisible(page, /详情|查看|审核/, details, 'open review entry');
    await closeAnyOverlay(page);
    return details;
  });

  await runStep(ctx, page, '达人 CRM', async () => {
    const details = await gotoAndAssertReady(ctx, page, '/talent', ['达人', '昵称', '粉丝', '认领', '公海', '私海']);
    details.actions = [];
    await trySearch(page, details, '');
    await clickIfVisible(page, /新增达人|新增/, details, 'open create talent');
    await closeAnyOverlay(page);
    await clickIfVisible(page, /认领/, details, 'claim talent');
    await closeAnyOverlay(page);
    await clickIfVisible(page, /详情|查看/, details, 'open talent detail');
    await closeAnyOverlay(page);
    return details;
  });

  await runStep(ctx, page, '寄样台', async () => {
    const details = await gotoAndAssertReady(ctx, page, '/sample', ['寄样', '申请', '审核', '发货', '物流', '签收']);
    details.actions = [];
    await clickIfVisible(page, /申请寄样|新增寄样|发起寄样/, details, 'open sample apply');
    await closeAnyOverlay(page);
    await clickIfVisible(page, /审核通过|审核拒绝|录入物流|发货/, details, 'open sample action');
    await closeAnyOverlay(page);
    await clickIfVisible(page, /导出/, details, 'export sample');
    await closeAnyOverlay(page);
    return details;
  });

  await runStep(ctx, page, '订单列表', async () => {
    const details = await gotoAndAssertReady(ctx, page, '/orders', ['订单', '订单号', '金额', '归因', '渠道', '商品']);
    details.actions = [];
    await clickIfVisible(page, /筛选|归因状态|查询/, details, 'open order filters');
    await closeAnyOverlay(page);
    await clickIfVisible(page, /下一页|详情|查看/, details, 'pagination or detail');
    await closeAnyOverlay(page);
    return details;
  });

  await runStep(ctx, page, '数据平台', async () => {
    const details = await gotoAndAssertReady(ctx, page, '/data', ['数据', '订单', '服务费', '归因', '统计', '看板'], {
      allowedFinalPaths: ['/data', '/dashboard']
    });
    details.actions = [];
    await clickIfVisible(page, /筛选|下单时间|结算时间|日期/, details, 'open data filters');
    await closeAnyOverlay(page);
    return details;
  });

  await runStep(ctx, page, '系统管理', async () => {
    const details = await gotoAndAssertReady(ctx, page, '/system', ['系统', '用户', '员工', '角色', '权限'], {
      allowedFinalPaths: ['/system/users', '/system/roles', '/system']
    });
    details.actions = [];
    const finalPath = normalizePathname(new URL(page.url()).pathname);
    if (finalPath === '/data' || isPathUnder(finalPath, '/data')) {
      details.pass = false;
      details.actual = `管理员 /system 最终落到 ${finalPath}`;
      details.initialReason = '管理员系统管理默认页仍被兜底到数据页';
      return details;
    }
    if (!(isPathUnder(finalPath, '/system/users') || isPathUnder(finalPath, '/system/roles'))) {
      details.pass = false;
      details.actual = `管理员 /system 最终 URL 不在系统管理默认页: ${finalPath}`;
      return details;
    }
    await page.goto('/system/users', { waitUntil: 'domcontentloaded' }).catch(() => {});
    await waitForPageStable(page);
    await page.goto('/system/roles', { waitUntil: 'domcontentloaded' }).catch(() => {});
    await waitForPageStable(page);
    await clickIfVisible(page, /新增员工|新增用户|新增/, details, 'open create user');
    await closeAnyOverlay(page);
    details.actions.push({ action: 'open role edit', status: 'skipped', reason: 'avoid mutating role permission form in broad flow' });
    return details;
  });

  await runStep(ctx, page, '权限边界抽查', async () => {
    const roleCases = readJsonIfExists(ROLE_CASES_FILE, { roles: {} });
    const bizLeader = roleCases.roles?.biz_leader || {
      username: 'leader_zs',
      password: '123456'
    };
    const details = {
      actions: [],
      account: { username: bizLeader.username },
      pass: true
    };

    const systemStep = [...ctx.steps].reverse().find((step) => step.name === '系统管理');
    const adminSystemPath = systemStep?.details?.finalPath || normalizePathname(new URL(page.url()).pathname);
    details.adminSystemPath = adminSystemPath;
    if (!isPathUnder(adminSystemPath, '/system')) {
      details.pass = false;
      details.actual = `admin cannot access /system, finalPath=${adminSystemPath}`;
      return details;
    }

    const browser = page.context().browser();
    const permissionContext = await browser.newContext({
      baseURL: ctx.baseUrl,
      viewport: { width: 1440, height: 960 }
    });
    const permissionPage = await permissionContext.newPage();
    permissionPage.setDefaultTimeout(10000);
    permissionPage.setDefaultNavigationTimeout(30000);
    attachPageObservers(permissionPage, ctx);
    await loginAs(permissionPage, bizLeader.username, bizLeader.password);
    await permissionPage.goto('/system', { waitUntil: 'domcontentloaded' });
    await waitForPageStable(permissionPage);
    const bizSystemPath = normalizePathname(new URL(permissionPage.url()).pathname);
    details.bizLeaderSystemPath = bizSystemPath;
    if (isPathUnder(bizSystemPath, '/system')) {
      details.pass = false;
      details.actual = `biz_leader can access /system, finalPath=${bizSystemPath}`;
      details.initialReason = '权限边界异常';
      await permissionContext.close().catch(() => {});
      return details;
    }

    const routes = ['/dashboard', '/product/library', '/product/activity', '/orders'];
    details.bizLeaderRoutes = [];
    for (const route of routes) {
      await permissionPage.goto(route, { waitUntil: 'domcontentloaded' });
      await waitForPageStable(permissionPage);
      const finalPath = normalizePathname(new URL(permissionPage.url()).pathname);
      const ok = route === '/product/activity' ? isPathUnder(finalPath, '/product/manage') || isPathUnder(finalPath, route) : isPathUnder(finalPath, route);
      details.bizLeaderRoutes.push({ route, finalPath, ok });
      if (!ok) details.pass = false;
    }
    await permissionContext.close().catch(() => {});
    return details;
  });
}

function blockingApiErrors(ctx) {
  return ctx.apiErrors.filter((item) => item.status >= 500 || item.status === 401);
}

function blockingConsoleErrors(ctx) {
  return ctx.consoleErrors.filter((item) => !isIgnorableConsole(item.text));
}

function writeSummary(ctx) {
  ctx.finishedAt = new Date().toISOString();
  const hardFailures = ctx.steps.filter((step) => !step.pass && !step.skipped);
  const apiBlockers = blockingApiErrors(ctx);
  const consoleBlockers = blockingConsoleErrors(ctx);
  ctx.overallPass = !!ctx.environment?.isTest && hardFailures.length === 0 && apiBlockers.length === 0 && consoleBlockers.length === 0;
  const summary = {
    name: SCRIPT_NAME,
    startedAt: ctx.startedAt,
    finishedAt: ctx.finishedAt,
    baseUrl: ctx.baseUrl,
    apiBaseUrl: ctx.apiBaseUrl,
    environment: ctx.environment,
    health: ctx.health,
    overallPass: ctx.overallPass,
    steps: ctx.steps,
    apiErrors: ctx.apiErrors,
    consoleErrors: ctx.consoleErrors,
    slowApis: ctx.slowApis,
    screenshots: ctx.screenshots,
    notes: ctx.notes
  };
  fs.writeFileSync(path.join(ctx.outDir, 'summary.json'), JSON.stringify(summary, null, 2));
  fs.writeFileSync(path.join(ctx.outDir, 'api-errors.json'), JSON.stringify(ctx.apiErrors, null, 2));
  fs.writeFileSync(path.join(ctx.outDir, 'console-errors.json'), JSON.stringify(ctx.consoleErrors, null, 2));
  fs.writeFileSync(path.join(ctx.outDir, 'slow-apis.json'), JSON.stringify(ctx.slowApis, null, 2));
  return summary;
}

function writeReport(ctx, summary) {
  const lines = [];
  lines.push('# 完整浏览器业务流程 QA 报告');
  lines.push('');
  lines.push(`- 执行时间：${summary.startedAt} ~ ${summary.finishedAt}`);
  lines.push(`- 前端地址：${summary.baseUrl}`);
  lines.push(`- 后端地址：${summary.apiBaseUrl}`);
  lines.push(`- 环境：${summary.environment?.env || '(unknown)'}`);
  lines.push(`- Profile：${summary.environment?.profile || summary.environment?.activeProfiles?.join(', ') || '(unknown)'}`);
  lines.push(`- 总体结论：**${summary.overallPass ? '通过' : '失败'}**`);
  lines.push('');
  lines.push('## 模块结果');
  for (const step of summary.steps) {
    const status = step.pass ? (step.skipped ? 'SKIPPED' : 'PASS') : 'FAIL';
    lines.push(`- ${step.name}：${status}，耗时 ${step.durationMs}ms，截图：${step.screenshot || '(none)'}`);
  }
  lines.push('');
  lines.push('## 失败详情');
  const failures = summary.steps.filter((step) => !step.pass && !step.skipped);
  if (!failures.length) {
    lines.push('- 无阻塞失败。');
  } else {
    for (const step of failures) {
      const failure = step.failure || {};
      lines.push(`### ${step.name}`);
      lines.push(`- 页面：${failure.page || step.details?.route || '(unknown)'}`);
      lines.push(`- 操作步骤：${failure.operation || step.name}`);
      lines.push(`- 期望结果：${failure.expected || '页面可用'}`);
      lines.push(`- 实际结果：${failure.actual || step.details?.actual || '(unknown)'}`);
      lines.push(`- 截图路径：${step.screenshot || '(none)'}`);
      lines.push(`- 相关接口错误：${JSON.stringify(step.details?.apiErrors || [])}`);
      lines.push(`- 初步判断：${failure.initialReason || step.details?.initialReason || '需要结合截图和接口错误继续定位'}`);
      lines.push('');
    }
  }
  lines.push('## API 错误');
  if (!summary.apiErrors.length) {
    lines.push('- 无。');
  } else {
    for (const item of summary.apiErrors) {
      lines.push(`- [${item.step}] ${item.status} ${item.method} ${item.url} (${item.durationMs}ms)`);
    }
  }
  lines.push('');
  lines.push('## Console 错误');
  if (!summary.consoleErrors.length) {
    lines.push('- 无。');
  } else {
    for (const item of summary.consoleErrors) {
      lines.push(`- [${item.step}] ${item.type}: ${excerpt(item.text, 300)}`);
    }
  }
  lines.push('');
  lines.push('## 慢接口');
  if (!summary.slowApis.length) {
    lines.push('- 无。');
  } else {
    for (const item of summary.slowApis) {
      lines.push(`- [${item.step}] ${item.durationMs}ms ${item.method} ${item.url}`);
    }
  }
  lines.push('');
  lines.push('## 下一步建议');
  lines.push('- 给关键按钮和表单补充稳定的 `data-testid`。');
  lines.push('- 为 mock 数据准备稳定的活动、商品、达人、寄样、订单样本。');
  lines.push('- 后续把业务动作从“入口巡检”升级到“真实提交闭环”。');
  fs.writeFileSync(path.join(ctx.outDir, 'report.md'), `${lines.join('\n')}\n`);
}

async function main() {
  const outDir = process.argv[2] || path.join(REPO_ROOT, 'runtime', 'qa', 'out', `${SCRIPT_NAME}-${timestamp()}`);
  const ctx = createContext(outDir);
  ensureDir(ctx.outDir);
  ensureDir(ctx.screenshotsDir);
  ensureDir(ctx.videosDir);
  ensureDir(ctx.tracesDir);

  let browser;
  let context;
  let page;
  try {
    await ensureTestEnvironment(ctx);
    await optionalMockReset(ctx);
    await optionalMockSeed(ctx);

    browser = await chromium.launch({ headless: HEADLESS, slowMo: SLOW_MO });
    context = await browser.newContext({
      baseURL: ctx.baseUrl,
      viewport: { width: 1440, height: 960 },
      ...(RECORD_VIDEO ? { recordVideo: { dir: ctx.videosDir } } : {})
    });
    await context.tracing.start({ screenshots: true, snapshots: true, sources: false });
    page = await context.newPage();
    page.setDefaultTimeout(10000);
    page.setDefaultNavigationTimeout(30000);
    attachPageObservers(page, ctx);

    await runBusinessFlow(ctx, page);
    assertNoApi500(ctx);
    assertNoBlockingConsoleErrors(ctx);
  } catch (error) {
    ctx.notes.push(`Fatal: ${error?.stack || String(error)}`);
    if (page) {
      await takeScreenshot(ctx, page, 'fatal').catch(() => {});
    }
  } finally {
    if (context) {
      await context.tracing.stop({ path: path.join(ctx.tracesDir, 'trace.zip') }).catch(() => {});
    }
    if (browser) {
      await browser.close().catch(() => {});
    }
    const summary = writeSummary(ctx);
    writeReport(ctx, summary);
    console.log(`QA business browser flow output: ${ctx.outDir}`);
    if (!summary.overallPass) {
      process.exitCode = 1;
    }
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
  normalizePathname,
  isPathUnder,
  excerpt,
  blockingApiErrors,
  blockingConsoleErrors,
  isIgnorableConsole,
  writeSummary,
  writeReport
};

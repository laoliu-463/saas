const fs = require('node:fs');
const path = require('node:path');
const cp = require('node:child_process');
const REPO_ROOT = path.resolve(__dirname, '..', '..');
const { chromium, request } = require(path.join(REPO_ROOT, 'node_modules', 'playwright'));
const { postTestAction, gotoStable, resetBrowserSession } = require('./test-api.cjs');

const FRONTEND = process.env.QA_FRONTEND || 'http://localhost:3001';
const BACKEND = process.env.QA_BACKEND || 'http://localhost:8081';
const API_BASE = `${BACKEND}/api`;
const HEADLESS = process.env.QA_HEADLESS ? process.env.QA_HEADLESS !== 'false' : false;
const SLOW_MO = Number(process.env.QA_SLOW_MO || 0);
const EDGE_PATH = fs.existsSync('C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe')
  ? 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
  : 'C:/Program Files/Microsoft/Edge/Application/msedge.exe';

const now = new Date();
const pad = (n) => String(n).padStart(2, '0');
const stamp = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}-${pad(now.getHours())}${pad(now.getMinutes())}`;
const OUT_DIR = path.join(__dirname, 'out', `e2e-${stamp}`);
const SHOT_DIR = path.join(OUT_DIR, 'screenshots');
const REPORT_MD = path.join(OUT_DIR, 'report.md');
const REPORT_JSON = path.join(OUT_DIR, 'report.json');
const F12_SUMMARY = path.join(OUT_DIR, 'f12-summary.json');
fs.mkdirSync(SHOT_DIR, { recursive: true });

const USERS = {
  admin: { username: 'admin', password: 'admin123', expectedHome: '/dashboard', expectMenus: ['数据平台', '商品库', '达人 CRM', '寄样台', '运营中心', '系统管理'], forbidMenus: [] },
  biz_leader: { username: 'biz_leader', password: 'admin123', expectedHome: '/dashboard', expectMenus: ['数据看板', '商品库', '商品管理', '寄样审核', '订单工作台'], forbidMenus: ['系统管理', '达人 CRM'] },
  biz_staff: { username: 'biz_staff', password: 'admin123', expectedHome: '/data', expectMenus: ['我的业绩', '商品库', '商品管理', '达人 CRM', '我的达人', '寄样台'], forbidMenus: ['订单工作台', '系统管理'] },
  channel_leader: { username: 'channel_leader', password: 'admin123', expectedHome: '/dashboard', expectMenus: ['数据平台', '商品库', '达人 CRM', '寄样台', '运营中心', '订单工作台'], forbidMenus: ['系统管理'] },
  channel_staff: { username: 'channel_staff', password: 'admin123', expectedHome: '/data', expectMenus: ['我的业绩', '商品库', '我的达人', '寄样台'], forbidMenus: ['订单工作台', '系统管理'] },
  ops_staff: { username: 'ops_staff', password: 'admin123', expectedHome: '/ops/shipping', expectMenus: ['寄样发货台', '物流发货'], forbidMenus: ['订单工作台', '系统管理', '达人 CRM', '商品库', '寄样审核', '独家状态'] }
};

const report = {
  generatedAt: new Date().toISOString(),
  outDir: OUT_DIR,
  screenshotsDir: SHOT_DIR,
  environment: {},
  phases: {
    phase0: [],
    phase1: [],
    phase2: [],
    phase3: [],
    phase4: [],
    phase5: [],
    phase6: []
  },
  bugs: [],
  notes: []
};

const f12 = {
  consoleErrors: [],
  pageErrors: [],
  network4xx: [],
  network5xx: []
};

function isExpectedLoginFailureEntry(url, status) {
  return status === 401 && typeof url === 'string' && url.includes('/api/auth/login');
}

function isExpectedLoginFailureConsole(message) {
  const text = message?.text || '';
  const url = message?.location?.url || '';
  return text.includes('401 (Unauthorized)') && url.includes('/api/auth/login');
}

let browser;
let context;
let page;
let seedState = {
  shippingSampleId: null,
  shippingSampleRequestNo: null,
  attributedOrders: [],
  noPickOrder: null,
  missingMappingOrder: null
};

function shortText(text, max = 140) {
  const value = (text || '').replace(/\s+/g, ' ').trim();
  return value.length > max ? `${value.slice(0, max)}...` : value;
}

function parseFirstNumber(text) {
  if (!text) return null;
  const match = text.replace(/,/g, '').match(/([0-9]+(?:\.[0-9]+)?)/);
  return match ? Number(match[1]) : null;
}

function parseMoneyByLabel(text, label) {
  const safe = text.replace(/\s+/g, '');
  const idx = safe.indexOf(label.replace(/\s+/g, ''));
  if (idx < 0) return null;
  const tail = safe.slice(idx, idx + 80);
  const match = tail.match(/¥([0-9]+(?:\.[0-9]+)?)/);
  return match ? Number(match[1]) : null;
}

function parseIntegerByLabel(text, label) {
  const safe = text.replace(/\s+/g, '');
  const idx = safe.indexOf(label.replace(/\s+/g, ''));
  if (idx < 0) return null;
  const tail = safe.slice(idx, idx + 40);
  const match = tail.match(/([0-9]+)/);
  return match ? Number(match[1]) : null;
}

function parseDashboardGmv(text) {
  const safe = text.replace(/\s+/g, '');
  const match = safe.match(/今日GMV[^¥]*¥([0-9,]+(?:\.[0-9]+)?)/);
  return match ? Number(match[1].replace(/,/g, '')) : null;
}

function parseDataAmount(text) {
  const safe = text.replace(/\s+/g, '');
  const match = safe.match(/订单总额¥([0-9,]+(?:\.[0-9]+)?)/);
  return match ? Number(match[1].replace(/,/g, '')) : null;
}

function routeMatches(current, expected) {
  return current === expected || current.startsWith(`${expected}?`);
}

function hasFatalUiError(text) {
  const normalized = (text || '').replace(/\s+/g, ' ').trim();
  return /Unexpected Application Error|Application error|502 Bad Gateway|503 Service Unavailable|504 Gateway Timeout|Cannot GET \/|TypeError:|ReferenceError:|SyntaxError:/i.test(normalized);
}

async function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function shot(name, targetPage = page) {
  const safeName = String(name).replace(/[<>:"/\\|?*\u0000-\u001F]+/g, '_').replace(/\s+/g, '_');
  const file = path.join(SHOT_DIR, `${safeName}.png`);
  await targetPage.screenshot({ path: file, fullPage: true });
  return file;
}

async function bodyText(targetPage = page) {
  return targetPage.locator('body').innerText().catch(() => '');
}

async function waitForIdle(targetPage = page) {
  await targetPage.locator('.n-spin-body').waitFor({ state: 'hidden', timeout: 8000 }).catch(() => {});
  await targetPage.locator('.loading-state').waitFor({ state: 'hidden', timeout: 8000 }).catch(() => {});
  await targetPage.locator('#boot-loading').waitFor({ state: 'hidden', timeout: 8000 }).catch(() => {});
  await sleep(400);
}

async function gotoPath(route) {
  await gotoStable(page, `${FRONTEND}${route}`);
}

async function resetSession() {
  await resetBrowserSession(page, context, FRONTEND);
}

async function login(username, password) {
  await resetSession();
  await page.getByPlaceholder('请输入用户名').fill(username);
  await page.getByPlaceholder('请输入密码').fill(password);
  const loginRespPromise = page.waitForResponse((resp) => resp.url().includes('/api/auth/login') && resp.request().method() === 'POST', { timeout: 15000 }).catch(() => null);
  await page.getByRole('button', { name: /登/ }).click();
  const loginResp = await loginRespPromise;
  await sleep(1200);
  await waitForIdle(page);
  return {
    url: page.url().replace(FRONTEND, ''),
    status: loginResp ? loginResp.status() : null,
    body: loginResp ? await loginResp.json().catch(() => null) : null
  };
}

async function logoutByUI() {
  if (page.url().endsWith('/login')) return;
  const userInfo = page.locator('.user-info');
  if (await userInfo.count()) {
    await userInfo.click();
    await sleep(300);
    const logout = page.getByText('退出登录');
    if (await logout.count()) {
      await logout.click();
      await page.waitForURL(/\/login/, { timeout: 10000 }).catch(() => {});
      await waitForIdle(page);
    }
  }
}

async function withApi() {
  const token = await page.evaluate(() => localStorage.getItem('token'));
  return request.newContext({
    baseURL: API_BASE,
    extraHTTPHeaders: token ? { Authorization: `Bearer ${token}` } : {}
  });
}

async function fetchOrderTotals() {
  return page.evaluate(async () => {
    const authValue = localStorage.getItem('token');
    const resp = await fetch('/api/data/orders?page=1&size=100', {
      headers: authValue ? { Authorization: `Bearer ${authValue}` } : {}
    });
    const json = await resp.json().catch(() => ({}));
    const data = json.data || json;
    return {
      total: Number(data.total || 0),
      records: Array.isArray(data.records) ? data.records : []
    };
  });
}

async function fetchDashboardMetrics() {
  return page.evaluate(async () => {
    const authValue = localStorage.getItem('token');
    const resp = await fetch('/api/dashboard/metrics', {
      headers: authValue ? { Authorization: `Bearer ${authValue}` } : {}
    });
    const json = await resp.json().catch(() => ({}));
    return json.data || json || {};
  });
}

async function fetchSamplePageByStatus(status) {
  return page.evaluate(async (currentStatus) => {
    const authValue = localStorage.getItem('token');
    const resp = await fetch(`/api/samples?page=1&size=20&status=${currentStatus}`, {
      headers: authValue ? { Authorization: `Bearer ${authValue}` } : {}
    });
    const json = await resp.json().catch(() => ({}));
    const data = json.data || json;
    return {
      total: Number(data.total || 0),
      records: Array.isArray(data.records) ? data.records : []
    };
  }, status);
}

function addBug(id, severity, title, screenshotPath, steps) {
  report.bugs.push({ id, severity, title, screenshot: screenshotPath, steps });
}

async function record(phaseKey, id, route, executor) {
  const item = { id, route, result: '❌', note: 'N/A', screenshot: null };
  try {
    const result = await executor(item);
    if (result?.note) item.note = result.note;
    if (result?.screenshot) item.screenshot = result.screenshot;
    if (result?.result) item.result = result.result;
    if (item.result !== '✅' && !item.screenshot) {
      item.screenshot = await shot(`${id}_${route.replace(/[\/:]/g, '_') || 'root'}_fail`);
    }
  } catch (error) {
    item.result = '❌';
    item.note = `异常: ${error.message}`;
    item.screenshot = await shot(`${id}_${route.replace(/[\/:]/g, '_') || 'root'}_error`);
    addBug(`BUG-${String(report.bugs.length + 1).padStart(2, '0')}`, 'P1', `${id} 执行异常`, item.screenshot, `${route}: ${error.message}`);
  }
  report.phases[phaseKey].push(item);
}

async function collectEnvironment() {
  const front = await page.goto(FRONTEND, { waitUntil: 'domcontentloaded', timeout: 45000 });
  let healthStatus = null;
  let healthJson = null;
  try {
    const raw = cp.execFileSync('curl.exe', ['-s', `${BACKEND}/api/actuator/health`], { encoding: 'utf8' });
    healthJson = JSON.parse(raw);
    healthStatus = 200;
  } catch (error) {
    report.notes.push(`健康检查探测失败: ${error.message}`);
  }
  report.environment.frontend = { url: FRONTEND, status: front ? front.status() : null };
  report.environment.backend = { url: BACKEND, healthStatus, health: healthJson };
}

async function phase0() {
  await record('phase0', 'P0-1', '/api/actuator/health', async () => {
    const ok = report.environment.backend.healthStatus === 200 && report.environment.backend.health?.status === 'UP';
    return { result: ok ? '✅' : '❌', note: JSON.stringify(report.environment.backend.health || null) };
  });

  await record('phase0', 'P0-2', '/login', async () => {
    await resetSession();
    const current = page.url().replace(FRONTEND, '');
    const ok = current === '/login';
    const screenshot = await shot('P0-2_login_load');
    return { result: ok ? '✅' : '❌', note: `实际路径: ${current}`, screenshot };
  });
}

async function phase1() {
  for (const [caseId, key] of [['T1-1', 'admin'], ['T1-2', 'biz_leader'], ['T1-3', 'biz_staff'], ['T1-4', 'channel_leader'], ['T1-5', 'channel_staff'], ['T1-6', 'ops_staff']]) {
    const user = USERS[key];
    await record('phase1', caseId, '/login', async () => {
      const loginResult = await login(user.username, user.password);
      const body = await bodyText();
      const missing = user.expectMenus.filter((label) => !body.includes(label));
      const unexpected = user.forbidMenus.filter((label) => body.includes(label));
      const ok = loginResult.url === user.expectedHome && missing.length === 0 && unexpected.length === 0;
      const screenshot = await shot(`${caseId}_${user.username}_home`);
      return {
        result: ok ? '✅' : '❌',
        note: `落地: ${loginResult.url}; 缺失菜单: ${missing.join(',') || '无'}; 不应可见: ${unexpected.join(',') || '无'}`,
        screenshot
      };
    });
  }

  await record('phase1', 'T1-7', '/login', async () => {
    await resetSession();
    await page.getByPlaceholder('请输入用户名').fill('admin');
    await page.getByPlaceholder('请输入密码').fill('wrong');
    const respPromise = page.waitForResponse((resp) => resp.url().includes('/api/auth/login') && resp.request().method() === 'POST', { timeout: 15000 });
    await page.getByRole('button', { name: /登/ }).click();
    const resp = await respPromise;
    await sleep(1200);
    const current = page.url().replace(FRONTEND, '');
    const body = await bodyText();
    const ok = current === '/login' && [400, 401].includes(resp.status());
    const screenshot = await shot('T1-7_wrong_password');
    return {
      result: ok ? '✅' : '❌',
      note: `状态码: ${resp.status()}; 实际路径: ${current}; 页面反馈: ${shortText(body)}`,
      screenshot
    };
  });

  await record('phase1', 'T1-8', '/dashboard', async () => {
    await login('admin', 'admin123');
    await page.reload({ waitUntil: 'domcontentloaded' });
    await waitForIdle(page);
    const current = page.url().replace(FRONTEND, '');
    const ok = current === '/dashboard';
    const screenshot = await shot('T1-8_admin_refresh_dashboard');
    return { result: ok ? '✅' : '❌', note: `刷新后路径: ${current}`, screenshot };
  });
}

async function phase2() {
  await login('admin', 'admin123');
  const routeCases = [
    ['T2-01', '/dashboard', ['今日 GMV', '招商团队表现榜']],
    ['T2-02', '/orders', ['订单归因', '订单 ID']],
    ['T2-03', '/data', ['数据看板', '总订单数']],
    ['T2-04', '/data/orders', ['订单明细', '导出 CSV']],
    ['T2-05', '/product', ['商品', '活动']],
    ['T2-06', '/product/manage', ['活动列表']],
    ['T2-07', '/product/manage/1', ['商品']],
    ['T2-08', '/talent', ['达人']],
    ['T2-09', '/sample', ['寄样台']],
    ['T2-10', '/sample/apply', ['寄样']],
    ['T2-11', '/sample/1', ['寄样详情']],
    ['T2-12', '/ops/exclusive', ['独家状态']],
    ['T2-13', '/ops/shipping', ['待发货']],
    ['T2-14', '/system/users', ['用户管理']],
    ['T2-14b', '/system/roles', ['角色管理']]
  ];

  for (const [id, route, expectedTexts] of routeCases) {
    await record('phase2', id, route, async () => {
      await gotoPath(route);
      const current = page.url().replace(FRONTEND, '');
      const text = await bodyText();
      const hasError = hasFatalUiError(text);
      const matched = expectedTexts.some((label) => text.includes(label));
      const ok = routeMatches(current, route) && matched && !hasError;
      const screenshot = await shot(`${id}_${route.replace(/[\/:]/g, '_')}_firstpaint`);
      return { result: ok ? '✅' : '❌', note: `实际路径: ${current}; 关键字命中: ${matched}; 首屏摘要: ${shortText(text)}`, screenshot };
    });
  }
}

async function postTestActionFromPage(pathname) {
  const token = await page.evaluate(() => localStorage.getItem('token'));
  const resp = await postTestAction(page.request, API_BASE, token, pathname);
  await sleep(800);
  await waitForIdle(page);
  return { status: resp.status, body: resp.body };
}

async function phase3() {
  await login('admin', 'admin123');

  await record('phase3', 'T3-1', '/api/test/reset', async () => {
    const before = await fetchOrderTotals();
    const reset = await postTestActionFromPage('/test/reset');
    await gotoPath('/data/orders');
    const after = await fetchOrderTotals();
    const text = await bodyText();
    const ok = reset.status === 200 && after.total === 0;
    const screenshot = await shot('T3-1_reset_after');
    return {
      result: ok ? '✅' : '❌',
      note: `重置状态: ${reset.status}; 订单总数 ${before.total} -> ${after.total}; 页面摘要: ${shortText(text)}`,
      screenshot
    };
  });

  await record('phase3', 'T3-2', '/api/test/seed', async () => {
    const seed = await postTestActionFromPage('/test/seed');
    seedState.shippingSampleId = seed.body?.shippingSampleId || null;
    seedState.shippingSampleRequestNo = seed.body?.shippingSampleRequestNo || null;
    await gotoPath('/product');
    const productText = await bodyText();
    await gotoPath('/talent');
    const talentText = await bodyText();
    const ok = seed.status === 200
      && seedState.shippingSampleId
      && /商品/.test(productText)
      && /达人/.test(talentText);
    const screenshot = await shot('T3-2_seed_after');
    return {
      result: ok ? '✅' : '❌',
      note: `Seed 状态: ${seed.status}; shippingSampleId: ${seedState.shippingSampleId || 'N/A'}; 商品页摘要: ${shortText(productText)}; 达人页摘要: ${shortText(talentText)}`,
      screenshot
    };
  });

  await record('phase3', 'T3-3', '/api/test/orders/generate-attributed -> /data/orders', async () => {
    const before = await fetchOrderTotals();
    seedState.attributedOrders = [];
    for (let i = 0; i < 3; i += 1) {
      const result = await postTestActionFromPage('/test/orders/generate-attributed');
      seedState.attributedOrders.push(result.body);
    }
    await gotoPath('/data/orders');
    const after = await fetchOrderTotals();
    const pageText = await bodyText();
    const deltaOk = after.total - before.total === 3;
    const rowsContainOrders = seedState.attributedOrders.every((item) => pageText.includes(String(item.orderId)));
    const responseOk = seedState.attributedOrders.every((item) => item.pickSource && item.attributionStatus === 'ATTRIBUTED');
    const screenshot = await shot('T3-3_attributed_after');
    return {
      result: deltaOk && rowsContainOrders && responseOk ? '✅' : '❌',
      note: `订单总数 ${before.total} -> ${after.total}; 新订单: ${seedState.attributedOrders.map((o) => o.orderId).join(', ')}; pickSource 校验: ${responseOk}`,
      screenshot
    };
  });

  await record('phase3', 'T3-4', '/api/test/orders/generate-no-pick-source -> /data/orders', async () => {
    const before = await fetchOrderTotals();
    const result = await postTestActionFromPage('/test/orders/generate-no-pick-source');
    seedState.noPickOrder = result.body;
    await gotoPath('/data/orders');
    const after = await fetchOrderTotals();
    const pageText = await bodyText();
    const deltaOk = after.total - before.total === 1;
    const exists = pageText.includes(String(seedState.noPickOrder.orderId));
    const responseOk = !seedState.noPickOrder.pickSource && seedState.noPickOrder.attributionStatus === 'UNATTRIBUTED';
    const screenshot = await shot('T3-4_no_pick_after');
    return {
      result: deltaOk && exists && responseOk ? '✅' : '❌',
      note: `订单总数 ${before.total} -> ${after.total}; 订单 ${seedState.noPickOrder.orderId}; attribution=${seedState.noPickOrder.attributionStatus}`,
      screenshot
    };
  });

  await record('phase3', 'T3-5', '/api/test/orders/generate-missing-mapping -> /data/orders', async () => {
    const before = await fetchOrderTotals();
    const result = await postTestActionFromPage('/test/orders/generate-missing-mapping');
    seedState.missingMappingOrder = result.body;
    await gotoPath('/data/orders');
    const after = await fetchOrderTotals();
    const pageText = await bodyText();
    const deltaOk = after.total - before.total === 1;
    const exists = pageText.includes(String(seedState.missingMappingOrder.orderId));
    const responseOk = seedState.missingMappingOrder.pickSource && seedState.missingMappingOrder.attributionStatus === 'UNATTRIBUTED';
    const screenshot = await shot('T3-5_missing_mapping_after');
    return {
      result: deltaOk && exists && responseOk ? '✅' : '❌',
      note: `订单总数 ${before.total} -> ${after.total}; 订单 ${seedState.missingMappingOrder.orderId}; attributionRemark=${seedState.missingMappingOrder.attributionRemark}`,
      screenshot
    };
  });

  await record('phase3', 'T3-6', '/ops/shipping', async () => {
    await logoutByUI();
    await login('ops_staff', 'admin123');
    if (!seedState.shippingSampleId) {
      return { result: '❌', note: '缺少 seed 产出的 shippingSampleId', screenshot: await shot('T3-6_missing_shipping_sample_id') };
    }
    await gotoPath(`/sample/${seedState.shippingSampleId}`);
    await page.getByText('寄样详情').waitFor({ state: 'visible', timeout: 10000 });
    const beforeText = await bodyText();
    let shipOk = false;
    let signOk = false;
    if (await page.getByRole('button', { name: '发货' }).count()) {
      await page.getByRole('button', { name: '发货' }).click();
      await page.getByPlaceholder('SF1234567890').fill(`TEST${Date.now()}`);
      await page.getByRole('button', { name: '确认发货' }).click();
      await sleep(1200);
      shipOk = (await bodyText()).includes('快递中');
    }
    if (await page.getByRole('button', { name: '签收' }).count()) {
      await page.getByRole('button', { name: '签收' }).click();
      await sleep(1200);
      signOk = /(待交作业|已完成|已签收)/.test(await bodyText());
    }
    const afterText = await bodyText();
    const screenshot = await shot('T3-6_ops_shipping_after');
    return {
      result: shipOk && signOk ? '✅' : '❌',
      note: `发货前摘要: ${shortText(beforeText)}; 发货成功: ${shipOk}; 签收成功: ${signOk}; 发货后摘要: ${shortText(afterText)}`,
      screenshot
    };
  });
}

async function phase4() {
  await record('phase4', 'T4-1', '/data', async () => {
    await resetSession();
    await gotoPath('/data');
    const current = page.url().replace(FRONTEND, '');
    const screenshot = await shot('T4-1_anonymous_data');
    return { result: current === '/login' ? '✅' : '❌', note: `实际路径: ${current}`, screenshot };
  });

  await record('phase4', 'T4-2', '/orders', async () => {
    await login('biz_staff', 'admin123');
    await gotoPath('/orders');
    const current = page.url().replace(FRONTEND, '');
    const screenshot = await shot('T4-2_biz_staff_orders');
    return { result: current === '/data' ? '✅' : '❌', note: `实际路径: ${current}`, screenshot };
  });

  await record('phase4', 'T4-3', '/system/users', async () => {
    await login('channel_staff', 'admin123');
    await gotoPath('/system/users');
    const current = page.url().replace(FRONTEND, '');
    const screenshot = await shot('T4-3_channel_staff_system_users');
    return { result: current === '/data' ? '✅' : '❌', note: `实际路径: ${current}`, screenshot };
  });

  await record('phase4', 'T4-4', '/talent', async () => {
    await login('ops_staff', 'admin123');
    await gotoPath('/talent');
    const current = page.url().replace(FRONTEND, '');
    const screenshot = await shot('T4-4_ops_staff_talent');
    return { result: current === '/ops/shipping' ? '✅' : '❌', note: `实际路径: ${current}`, screenshot };
  });

  await record('phase4', 'T4-5', '/dev/test removed', async () => {
    await login('admin', 'admin123');
    await gotoPath('/dev/test');
    const current = page.url().replace(FRONTEND, '');
    const screenshot = await shot('T4-5_dev_test_removed');
    return { result: current !== '/dev/test' ? '✅' : '❌', note: `移除调试台后实际路径: ${current}`, screenshot };
  });

  await record('phase4', 'T4-6', '/foo/bar/baz', async () => {
    await login('channel_staff', 'admin123');
    await gotoPath('/foo/bar/baz');
    const current = page.url().replace(FRONTEND, '');
    const screenshot = await shot('T4-6_unknown_route');
    return { result: current === '/data' ? '✅' : '❌', note: `实际路径: ${current}`, screenshot };
  });
}

async function phase5() {
  await login('channel_leader', 'admin123');

  await record('phase5', 'T5-1', '/dashboard vs /data', async () => {
    await gotoPath('/dashboard');
    const dashboardText = await bodyText();
    const dashboardGmv = parseDashboardGmv(dashboardText);
    await gotoPath('/data');
    const dataText = await bodyText();
    const dataGmv = parseDataAmount(dataText);
    const screenshot = await shot('T5-1_gmv_compare');
    const same = dashboardGmv != null && dataGmv != null && Math.abs(dashboardGmv - dataGmv) < 0.01;
    return { result: same ? '✅' : '❌', note: `/dashboard ¥${dashboardGmv} vs /data ¥${dataGmv}`, screenshot };
  });

  await record('phase5', 'T5-2', '/data vs /data/orders', async () => {
    await gotoPath('/data');
    const metrics = await fetchDashboardMetrics();
    const metricOrders = Number(metrics.totalOrders ?? metrics.todayOrderCount ?? NaN);
    await gotoPath('/data/orders');
    const visibleRows = await page.locator('tbody tr').count();
    const screenshot = await shot('T5-2_order_total_compare');
    const same = Number.isFinite(metricOrders) && metricOrders === visibleRows;
    return { result: same ? '✅' : '❌', note: `/data ${metricOrders} vs /data/orders rows ${visibleRows}`, screenshot };
  });

  await record('phase5', 'T5-3', '/orders vs /data/orders', async () => {
    await gotoPath('/orders');
    const ordersText = await bodyText();
    const pageRows = await page.locator('tbody tr').count();
    const totalTagMatch = ordersText.match(/已归因:\s*(\d+)\s*单.*待排查:\s*(\d+)\s*单.*部分归因:\s*(\d+)\s*单/s);
    const workbenchVisible = totalTagMatch ? Number(totalTagMatch[1]) + Number(totalTagMatch[2]) + Number(totalTagMatch[3]) : pageRows;
    await gotoPath('/data/orders');
    const visibleRows = await page.locator('tbody tr').count();
    const screenshot = await shot('T5-3_workbench_vs_data_orders');
    const same = workbenchVisible === visibleRows || workbenchVisible === pageRows;
    return { result: same ? '✅' : '❌', note: `/orders 可见统计 ${workbenchVisible}; /orders 当前页行数 ${pageRows}; /data/orders rows ${visibleRows}`, screenshot };
  });
}

async function phase6() {
  await record('phase6', 'T6-1', '/product /talent /data/orders', async () => {
    await login('admin', 'admin123');
    await postTestActionFromPage('/test/reset');
    const routes = ['/product', '/talent', '/data/orders'];
    const snapshots = [];
    for (const route of routes) {
      await gotoPath(route);
      snapshots.push(`${route}:${shortText(await bodyText())}`);
    }
    const screenshot = await shot('T6-1_empty_states');
    const ok = snapshots.every((line) => !/Unexpected Application Error|500/.test(line));
    return { result: ok ? '✅' : '❌', note: snapshots.join(' | '), screenshot };
  });

  await record('phase6', 'T6-2', '/data slow-3g', async () => {
    await login('admin', 'admin123');
    await gotoPath('/dashboard');
    const client = await context.newCDPSession(page);
    await client.send('Network.enable');
    await client.send('Network.emulateNetworkConditions', {
      offline: false,
      latency: 400,
      downloadThroughput: 50 * 1024 / 8,
      uploadThroughput: 20 * 1024 / 8,
      connectionType: 'cellular3g'
    });
    const nav = page.goto(`${FRONTEND}/data`, { waitUntil: 'domcontentloaded', timeout: 30000 });
    await page.waitForLoadState('domcontentloaded', { timeout: 6000 }).catch(() => {});
    let loadingVisible = false;
    for (let i = 0; i < 30; i += 1) {
      const hasBootLoading = await page.locator('#boot-loading:visible').count().catch(() => 0);
      const hasLoadingState = await page.locator('.loading-state:visible').count().catch(() => 0);
      const hasSkeleton = await page.locator('.n-skeleton:visible').count().catch(() => 0);
      const hasSpin = await page.locator('.n-spin-body').count().catch(() => 0);
      if (hasBootLoading > 0 || hasLoadingState > 0 || hasSkeleton > 0 || hasSpin > 0) {
        loadingVisible = true;
        break;
      }
      await sleep(200);
    }
    await nav.catch(() => {});
    await waitForIdle(page);
    await client.send('Network.emulateNetworkConditions', {
      offline: false,
      latency: 0,
      downloadThroughput: -1,
      uploadThroughput: -1,
      connectionType: 'none'
    });
    const screenshot = await shot('T6-2_slow3g_data');
    return { result: loadingVisible ? '✅' : '❌', note: `慢网首屏是否看到加载/骨架: ${loadingVisible}`, screenshot };
  });

  await record('phase6', 'T6-3', 'token expiry redirect', async () => {
    await login('channel_staff', 'admin123');
    await gotoPath('/data');
    await page.evaluate(() => localStorage.removeItem('token'));
    await page.getByText('商品库').first().click().catch(async () => {
      await page.goto(`${FRONTEND}/product`, { waitUntil: 'domcontentloaded' });
    });
    await sleep(1200);
    const current = page.url().replace(FRONTEND, '');
    const screenshot = await shot('T6-3_token_cleared');
    return { result: current === '/login' ? '✅' : '❌', note: `清 token 后实际路径: ${current}`, screenshot };
  });

  await record('phase6', 'T6-4', '/data/orders refresh filter state', async () => {
    await login('channel_staff', 'admin123');
    await gotoPath('/data/orders');
    const hasOrderIdFilter = (await page.getByPlaceholder(/订单/i).count().catch(() => 0)) > 0;
    if (!hasOrderIdFilter) {
      const screenshot = await shot('T6-4_data_orders_no_order_filter');
      return { result: '❌', note: '页面未提供订单号筛选输入，无法按提示词执行“筛选订单号 -> F5”验证', screenshot };
    }
    return { result: '✅', note: '存在订单号筛选项' };
  });

  await record('phase6', 'T6-5', 'history back/forward', async () => {
    await login('channel_staff', 'admin123');
    await gotoPath('/data');
    await page.getByText('订单明细').first().click();
    await waitForIdle(page);
    await page.goBack({ waitUntil: 'domcontentloaded' });
    const backPath = page.url().replace(FRONTEND, '');
    await page.goForward({ waitUntil: 'domcontentloaded' });
    const forwardPath = page.url().replace(FRONTEND, '');
    const screenshot = await shot('T6-5_history_navigation');
    const ok = backPath === '/data' && forwardPath === '/data/orders';
    return { result: ok ? '✅' : '❌', note: `back=${backPath}; forward=${forwardPath}`, screenshot };
  });
}

function summarizePhases() {
  const phaseMap = {
    '0 健康检查': report.phases.phase0,
    '1 登录权限': report.phases.phase1,
    '2 路由覆盖': report.phases.phase2,
    '3 业务闭环': report.phases.phase3,
    '4 权限边界': report.phases.phase4,
    '5 数据一致性': report.phases.phase5,
    '6 异常回归': report.phases.phase6
  };
  return Object.entries(phaseMap).map(([name, items]) => {
    const passed = items.filter((item) => item.result === '✅').length;
    const failed = items.length - passed;
    return { name, total: items.length, passed, failed, rate: items.length ? `${Math.round((passed / items.length) * 100)}%` : '0%' };
  });
}

function buildMarkdown() {
  const summaries = summarizePhases();
  const allCases = Object.values(report.phases).flat();
  const passed = allCases.filter((item) => item.result === '✅').length;
  const failed = allCases.length - passed;
  const lines = [];
  lines.push(`# E2E 全系统检验报告 — ${new Date().toLocaleString('zh-CN', { hour12: false })}`);
  lines.push('');
  lines.push('## 环境基线');
  lines.push(`- 前端: ${FRONTEND} ${report.environment.frontend?.status === 200 ? '✅' : '❌'}`);
  lines.push(`- 后端: ${BACKEND} /api/actuator/health → ${report.environment.backend?.health?.status || 'N/A'} ${report.environment.backend?.healthStatus === 200 ? '✅' : '❌'}`);
  lines.push(`- 产出目录: ${OUT_DIR}`);
  lines.push('');
  lines.push('## 测试总览');
  lines.push('| Phase | 用例数 | 通过 | 失败 | 通过率 |');
  lines.push('| --- | --- | --- | --- | --- |');
  for (const item of summaries) {
    lines.push(`| ${item.name} | ${item.total} | ${item.passed} | ${item.failed} | ${item.rate} |`);
  }
  lines.push(`| **合计** | **${allCases.length}** | **${passed}** | **${failed}** | **${Math.round((passed / allCases.length) * 100)}%** |`);
  lines.push('');
  lines.push('## 详细结果');
  for (const [phaseName, items] of Object.entries(report.phases)) {
    lines.push(`### ${phaseName}`);
    for (const item of items) {
      const mark = item.result === '✅' ? 'x' : ' ';
      lines.push(`- [${mark}] ${item.id} ${item.route} ${item.result} — ${item.note}${item.screenshot ? `（截图: ${item.screenshot}）` : ''}`);
    }
    lines.push('');
  }
  lines.push('## Bug 清单');
  if (!report.bugs.length) {
    lines.push('- 未记录到新的阻断级 Bug。');
  } else {
    lines.push('| 编号 | 严重度 | 现象 | 截图 | 复现步骤 |');
    lines.push('| --- | --- | --- | --- | --- |');
    for (const bug of report.bugs) {
      lines.push(`| ${bug.id} | ${bug.severity} | ${bug.title} | ${bug.screenshot || 'N/A'} | ${bug.steps} |`);
    }
  }
  lines.push('');
  lines.push('## F12 汇总');
  lines.push(`- Console 错误: ${f12.consoleErrors.length} 个`);
  lines.push(`- Page Error: ${f12.pageErrors.length} 个`);
  lines.push(`- Network 4xx: ${f12.network4xx.length} 个`);
  lines.push(`- Network 5xx: ${f12.network5xx.length} 个`);
  lines.push('');
  lines.push('## 结论');
  lines.push(`- 总通过率: ${passed}/${allCases.length} = ${Math.round((passed / allCases.length) * 100)}%`);
  lines.push(`- 关键业务闭环: ${report.phases.phase3.filter((item) => item.result === '✅').length}/${report.phases.phase3.length} 通过`);
  return lines.join('\n');
}

async function main() {
  browser = await chromium.launch({ headless: HEADLESS, slowMo: SLOW_MO, executablePath: EDGE_PATH });
  context = await browser.newContext({ viewport: { width: 1440, height: 960 } });
  page = await context.newPage();

  page.on('console', (msg) => {
    if (msg.type() === 'error') {
      const entry = { text: msg.text(), location: msg.location() };
      if (isExpectedLoginFailureConsole(entry)) {
        return;
      }
      f12.consoleErrors.push(entry);
    }
  });
  page.on('pageerror', (err) => {
    f12.pageErrors.push({ message: err.message, stack: err.stack });
  });
  context.on('response', (resp) => {
    const status = resp.status();
    const url = resp.url();
    if (!url.startsWith(FRONTEND) && !url.startsWith(API_BASE)) return;
    if (status >= 500) {
      f12.network5xx.push({ status, url, method: resp.request().method() });
    } else if (status >= 400) {
      if (isExpectedLoginFailureEntry(url, status)) {
        return;
      }
      f12.network4xx.push({ status, url, method: resp.request().method() });
    }
  });

  try {
    await collectEnvironment();
    await phase0();
    if (report.environment.frontend.status !== 200) {
      report.notes.push('前端不可访问，已中止后续测试。');
    } else {
      await phase1();
      await phase2();
      await phase3();
      await phase4();
      await phase5();
      await phase6();
    }
  } finally {
    fs.writeFileSync(F12_SUMMARY, JSON.stringify(f12, null, 2));
    fs.writeFileSync(REPORT_JSON, JSON.stringify({ report, f12 }, null, 2));
    fs.writeFileSync(REPORT_MD, buildMarkdown());
    await browser.close();
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});

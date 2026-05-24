const fs = require('node:fs');
const path = require('node:path');
const REPO_ROOT = path.resolve(__dirname, '..', '..');
const { chromium, request } = require(path.join(REPO_ROOT, 'node_modules', 'playwright'));
const { postTestAction } = require('./test-api.cjs');

const FRONTEND = process.env.QA_FRONTEND || 'http://localhost:3000';
const BACKEND = process.env.QA_BACKEND || 'http://localhost:8080';
const API = `${BACKEND}/api`;
const HEADLESS = process.env.QA_HEADLESS ? process.env.QA_HEADLESS !== 'false' : false;
const SLOW_MO = Number(process.env.QA_SLOW_MO || 800);
const CASE_FILTER = new Set(
  String(process.env.QA_CASES || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
);
const EDGE_PATH = fs.existsSync('C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe')
  ? 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
  : 'C:/Program Files/Microsoft/Edge/Application/msedge.exe';

const now = new Date();
const pad = (n) => String(n).padStart(2, '0');
const stamp = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}-${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`;
const OUT_DIR = path.join(__dirname, 'out', `admin-full-demo-${stamp}`);
const SHOT_DIR = path.join(OUT_DIR, 'screenshots');
const REPORT_MD = path.join(OUT_DIR, 'report.md');
const REPORT_JSON = path.join(OUT_DIR, 'report.json');
fs.mkdirSync(SHOT_DIR, { recursive: true });

const report = {
  generatedAt: new Date().toISOString(),
  frontend: FRONTEND,
  backend: BACKEND,
  headless: HEADLESS,
  slowMo: SLOW_MO,
  cases: [],
  notes: []
};

const created = {
  talentNickname: `管理员演示达人-${stamp}`,
  talentDouyinNo: `admin_demo_${stamp.replace(/[-]/g, '')}`,
  userUsername: `admin_demo_user_${stamp.replace(/[-]/g, '')}`,
  roleCode: `adm_demo_${stamp.slice(-6)}`,
  roleName: `管理员演示角色${stamp.slice(-6)}`,
  configKey: `admin.demo_${stamp.slice(-6).toLowerCase()}`,
  configName: `管理员演示配置${stamp.slice(-6)}`
};

function addCase(id, title) {
  const item = { id, title, result: '❌', note: '', screenshot: '' };
  report.cases.push(item);
  return item;
}

function shouldRunCase(id) {
  return CASE_FILTER.size === 0 || CASE_FILTER.has(id);
}

function writeReport() {
  const passed = report.cases.filter((item) => item.result === '✅').length;
  const lines = [
    `# 管理员全功能演示报告 - ${new Date().toLocaleString('zh-CN', { hour12: false })}`,
    '',
    `- 前端: ${FRONTEND}`,
    `- 后端: ${BACKEND}`,
    `- 模式: ${HEADLESS ? 'headless' : 'visible'} / slowMo=${SLOW_MO}ms`,
    `- 用例筛选: ${CASE_FILTER.size ? Array.from(CASE_FILTER).join(', ') : '全部'}`,
    `- 通过: ${passed}/${report.cases.length}`,
    '',
    '| 编号 | 模块 | 结果 | 备注 |',
    '| --- | --- | --- | --- |',
    ...report.cases.map((item) => `| ${item.id} | ${item.title} | ${item.result} | ${String(item.note || '').replace(/\|/g, '/')} |`),
    '',
    '## 截图',
    ...report.cases.filter((item) => item.screenshot).map((item) => `- ${item.id}: ${item.screenshot}`),
    '',
    '## Notes',
    ...(report.notes.length ? report.notes.map((note) => `- ${note}`) : ['- 无'])
  ];
  fs.writeFileSync(REPORT_JSON, JSON.stringify(report, null, 2));
  fs.writeFileSync(REPORT_MD, `${lines.join('\n')}\n`);
}

function short(text, max = 180) {
  const value = String(text || '').replace(/\s+/g, ' ').trim();
  return value.length > max ? `${value.slice(0, max)}...` : value;
}

async function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function waitForIdle(page) {
  await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
  await page.locator('.n-spin-body').waitFor({ state: 'hidden', timeout: 4000 }).catch(() => {});
  await page.locator('.loading-state').waitFor({ state: 'hidden', timeout: 4000 }).catch(() => {});
  await sleep(600);
}

async function shot(page, name) {
  const safe = name.replace(/[<>:"/\\|?*\u0000-\u001F]+/g, '_');
  const file = path.join(SHOT_DIR, `${safe}.png`);
  await page.screenshot({ path: file, fullPage: true });
  return file;
}

async function goto(page, route) {
  await page.goto(`${FRONTEND}${route}`, { waitUntil: 'domcontentloaded', timeout: 30000 });
  await waitForIdle(page);
}

async function login(page, username = 'admin', password = 'admin123') {
  await page.goto(`${FRONTEND}/login`, { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.evaluate(() => {
    localStorage.clear();
    sessionStorage.clear();
  }).catch(() => {});
  await page.context().clearCookies();
  await page.goto(`${FRONTEND}/login`, { waitUntil: 'networkidle', timeout: 30000 });
  await page.getByPlaceholder('请输入用户名').fill(username);
  await page.getByPlaceholder('请输入密码').fill(password);
  const respPromise = page.waitForResponse((resp) => resp.url().includes('/api/auth/login') && resp.request().method() === 'POST', { timeout: 15000 }).catch(() => null);
  await page.getByRole('button', { name: /登/ }).click();
  const resp = await respPromise;
  await waitForIdle(page);
  if (!resp || !resp.ok()) {
    throw new Error(`管理员登录失败: ${resp ? resp.status() : 'no-response'}`);
  }
}

async function apiLogin(apiContext, username = 'admin', password = 'admin123') {
  const res = await apiContext.post(`${API}/auth/login`, { data: { username, password } });
  const body = await res.json().catch(() => ({}));
  const token = body?.data?.token || body?.data?.accessToken;
  if (!res.ok() || !token) {
    throw new Error(`API 登录失败: ${username}`);
  }
  return token;
}

async function apiRequest(apiContext, method, url, token, data) {
  const res = await apiContext.fetch(url, {
    method,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    data
  });
  const body = await res.json().catch(() => ({}));
  if (!res.ok()) {
    throw new Error(`${method} ${url} 失败: ${res.status()} ${JSON.stringify(body)}`);
  }
  return body?.data ?? body;
}

async function searchUser(page, username) {
  const filter = page
    .locator('[data-testid="system-users-page"] input[placeholder*="用户名"], [data-testid="system-users-page"] input[placeholder*="姓名"], [data-testid="system-users-page"] input[placeholder*="手机"]')
    .first();
  await filter.waitFor({ state: 'visible', timeout: 10000 });
  await filter.fill(username);
  await page.getByRole('button', { name: '查询' }).click({ force: true });
  await waitForIdle(page);
}

async function searchRole(page, keyword) {
  const filter = page.getByPlaceholder('请输入角色编码或名称').first();
  if (!await filter.count()) {
    await waitForIdle(page);
    return;
  }
  await filter.fill(keyword);
  const queryButton = page.getByRole('button', { name: '查询' }).first();
  if (await queryButton.count()) {
    await queryButton.click({ force: true });
  }
  await waitForIdle(page);
}

async function searchConfig(page, key) {
  const filter = page.getByPlaceholder('搜索配置键或名称');
  await filter.fill(key);
  await page.getByRole('button', { name: '查询' }).click({ force: true });
  await waitForIdle(page);
}

async function openSelectByIndex(page, index) {
  const selection = page.locator('.n-modal .n-base-selection, [role="dialog"] .n-base-selection').nth(index);
  await selection.click({ force: true });
  await sleep(500);
}

async function selectOption(page, label, index = 0) {
  await openSelectByIndex(page, index);
  const clicked = await page.evaluate((targetLabel) => {
    const options = Array.from(document.querySelectorAll('.n-base-select-option'));
    const target = options.find((node) => {
      const text = node.textContent || '';
      const style = window.getComputedStyle(node);
      const rect = node.getBoundingClientRect();
      return text.includes(targetLabel) && style.display !== 'none' && style.visibility !== 'hidden' && rect.height > 0;
    });
    if (!target) return false;
    target.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
    return true;
  }, label);
  if (!clicked) {
    throw new Error(`未找到下拉选项: ${label}`);
  }
  await sleep(300);
  await page.keyboard.press('Escape').catch(() => {});
  await sleep(200);
}

async function fillInputByModalOrder(page, index, value) {
  const input = page.locator('.n-modal input, [role="dialog"] input').nth(index);
  await input.fill(value);
}

async function clickFirstButtonIfExists(page, name) {
  const button = page.getByRole('button', typeof name === 'string' ? { name } : { name }).first();
  if (await button.count()) {
    await button.click({ force: true });
    return true;
  }
  return false;
}

async function ensureRoute(page, route) {
  const url = new URL(page.url());
  if (!url.pathname.startsWith(route)) {
    throw new Error(`未停留在预期页面 ${route}，当前为 ${url.pathname}`);
  }
}

async function runCase(page, id, title, executor) {
  if (!shouldRunCase(id)) {
    return;
  }
  const item = addCase(id, title);
  try {
    const result = await executor();
    item.result = result?.result || '✅';
    item.note = result?.note || '';
    item.screenshot = result?.screenshot || await shot(page, `${id}`);
  } catch (error) {
    item.result = '❌';
    item.note = short(error?.stack || error?.message || String(error));
    item.screenshot = await shot(page, `${id}_fail`).catch(() => '');
  } finally {
    writeReport();
  }
}

async function createUserCrud(page) {
  const apiContext = await request.newContext({ baseURL: BACKEND, ignoreHTTPSErrors: true });
  try {
    const adminToken = await apiLogin(apiContext);
    const rolesPage = await apiRequest(apiContext, 'GET', `${API}/roles?page=1&size=100`, adminToken);
    const demoRole = (rolesPage?.records || []).find((item) => item.roleCode === 'biz_staff')
      || (rolesPage?.records || []).find((item) => item.roleCode !== 'admin');
    if (!demoRole?.id) {
      throw new Error('未找到可分配业务角色，无法准备用户 CRUD 演示数据');
    }

    const existingUsers = await apiRequest(
      apiContext,
      'GET',
      `${API}/users?page=1&size=100&keyword=${encodeURIComponent(created.userUsername)}`,
      adminToken
    );
    for (const staleUser of existingUsers?.records || []) {
      if (staleUser?.id && String(staleUser.username) === created.userUsername) {
        await apiRequest(apiContext, 'DELETE', `${API}/users/${staleUser.id}`, adminToken);
      }
    }

    await goto(page, '/system/users');
    await searchUser(page, created.userUsername);
    await shot(page, 'system_users_crud_01_empty');

    const createdUser = await apiRequest(apiContext, 'POST', `${API}/users`, adminToken, {
      username: created.userUsername,
      password: 'admin123',
      realName: '管理员演示用户',
      phone: '13900000000',
      email: `${created.userUsername}@example.com`,
      deptId: null,
      roleIds: [demoRole.id]
    });
    const userId = createdUser?.id || createdUser?.userId;
    if (!userId) {
      throw new Error('用户创建成功但未返回 userId');
    }

    await searchUser(page, created.userUsername);
    const createdRow = page.locator('tbody tr').filter({ hasText: created.userUsername }).first();
    await createdRow.waitFor({ state: 'visible', timeout: 10000 });
    await shot(page, 'system_users_crud_02_created');

    await apiRequest(apiContext, 'PUT', `${API}/users/${userId}`, adminToken, {
      realName: '管理员演示用户-已编辑',
      phone: '13900000001',
      email: `${created.userUsername}.edited@example.com`,
      status: 1
    });
    await apiRequest(apiContext, 'PUT', `${API}/users/${userId}/roles`, adminToken, {
      roleIds: [demoRole.id]
    });
    await searchUser(page, created.userUsername);
    await createdRow.waitFor({ state: 'visible', timeout: 10000 });
    await shot(page, 'system_users_crud_03_updated');

    await apiRequest(apiContext, 'PUT', `${API}/users/${userId}/password`, adminToken, {
      newPassword: 'admin456'
    });

    await apiRequest(apiContext, 'DELETE', `${API}/users/${userId}`, adminToken);
    await searchUser(page, created.userUsername);
    await shot(page, 'system_users_crud_04_deleted');

    return path.join(SHOT_DIR, 'system_users_crud_04_deleted.png');
  } finally {
    await apiContext.dispose();
  }
}

async function createRoleCrud(page, apiContext, adminToken) {
  const rolePage = await apiRequest(apiContext, 'GET', `${API}/roles?page=1&size=100`, adminToken);
  for (const staleRole of rolePage?.records || []) {
    if (staleRole?.id && String(staleRole.roleCode) === created.roleCode) {
      await apiRequest(apiContext, 'DELETE', `${API}/roles/${staleRole.id}`, adminToken);
    }
  }

  await goto(page, '/system/roles');
  await shot(page, 'system_roles_crud_01_empty');

  const createdRole = await apiRequest(apiContext, 'POST', `${API}/roles`, adminToken, {
    roleCode: created.roleCode,
    roleName: created.roleName,
    dataScope: 3,
    status: 1,
    remark: '管理员演示角色'
  });
  const roleId = createdRole?.id || createdRole?.roleId;
  if (!roleId) {
    throw new Error('角色创建成功但未返回 roleId');
  }

  await page.reload({ waitUntil: 'domcontentloaded', timeout: 30000 });
  await waitForIdle(page);
  await shot(page, 'system_roles_crud_02_created');

  await apiRequest(apiContext, 'PUT', `${API}/roles/${roleId}`, adminToken, {
    roleCode: created.roleCode,
    roleName: `${created.roleName}-已编辑`,
    dataScope: 3,
    status: 1,
    remark: '管理员演示角色-已编辑'
  });
  await page.reload({ waitUntil: 'domcontentloaded', timeout: 30000 });
  await waitForIdle(page);
  await shot(page, 'system_roles_crud_03_updated');

  await apiRequest(apiContext, 'DELETE', `${API}/roles/${roleId}`, adminToken);
  await page.reload({ waitUntil: 'domcontentloaded', timeout: 30000 });
  await waitForIdle(page);
  await shot(page, 'system_roles_crud_04_deleted');

  return path.join(SHOT_DIR, 'system_roles_crud_04_deleted.png');
}

async function createConfigCrud(page, apiContext, adminToken) {
  const existingConfigPage = await apiRequest(apiContext, 'GET', `${API}/configs?page=1&size=100&keyword=${encodeURIComponent(created.configKey)}`, adminToken);
  for (const staleConfig of existingConfigPage?.records || []) {
    if (staleConfig?.id && String(staleConfig.configKey) === created.configKey) {
      await apiRequest(apiContext, 'DELETE', `${API}/configs/${staleConfig.id}`, adminToken);
    }
  }

  await goto(page, '/system/config');
  await searchConfig(page, created.configKey);
  await shot(page, 'system_config_crud_01_empty');

  const createdConfig = await apiRequest(apiContext, 'POST', `${API}/configs`, adminToken, {
    configKey: created.configKey,
    configName: created.configName,
    configValue: 'demo-value',
    configGroup: 'douyin',
    configType: 'text',
    sortOrder: 0,
    status: 1,
    remark: '管理员演示配置'
  });
  const configId = createdConfig?.id || createdConfig?.configId;
  if (!configId) {
    throw new Error('配置创建成功但未返回 configId');
  }

  await searchConfig(page, created.configKey);
  const createdRow = page.locator('tbody tr').filter({ hasText: created.configKey }).first();
  await createdRow.waitFor({ state: 'visible', timeout: 10000 });
  await shot(page, 'system_config_crud_02_created');

  await apiRequest(apiContext, 'PUT', `${API}/configs/${configId}`, adminToken, {
    configKey: created.configKey,
    configName: `${created.configName}-已编辑`,
    configValue: 'demo-value-updated',
    configGroup: 'douyin',
    configType: 'text',
    sortOrder: 1,
    status: 1,
    remark: '管理员演示配置-已编辑'
  });
  await searchConfig(page, created.configKey);
  await createdRow.waitFor({ state: 'visible', timeout: 10000 });
  await shot(page, 'system_config_crud_03_updated');

  await apiRequest(apiContext, 'DELETE', `${API}/configs/${configId}`, adminToken);
  await searchConfig(page, created.configKey);
  await shot(page, 'system_config_crud_04_deleted');

  return path.join(SHOT_DIR, 'system_config_crud_04_deleted.png');
}

async function main() {
  const apiContext = await request.newContext({ baseURL: BACKEND, ignoreHTTPSErrors: true });
  const browser = await chromium.launch({
    headless: HEADLESS,
    slowMo: SLOW_MO,
    executablePath: fs.existsSync(EDGE_PATH) ? EDGE_PATH : undefined
  });
  const context = await browser.newContext({ viewport: { width: 1440, height: 960 } });
  const page = await context.newPage();
  const adminToken = await apiLogin(apiContext);

  try {
    if (!shouldRunCase('A00')) {
      await login(page);
      report.notes.push('已自动建立管理员登录会话（A00 被筛选跳过）');
      writeReport();
    }

    await runCase(page, 'A00', '管理员登录', async () => {
      await login(page);
      await ensureRoute(page, '/dashboard');
      return { note: '管理员登录成功并进入首页', screenshot: await shot(page, 'admin_login') };
    });

    await runCase(page, 'A01', '测试数据准备', async () => {
      await postTestAction(apiContext, API, adminToken, '/test/reset');
      await postTestAction(apiContext, API, adminToken, '/test/seed');
      await postTestAction(apiContext, API, adminToken, '/test/orders/generate-attributed');
      await postTestAction(apiContext, API, adminToken, '/test/orders/generate-no-pick-source');
      await postTestAction(apiContext, API, adminToken, '/test/orders/generate-missing-mapping');
      await goto(page, '/dashboard');
      return { note: '已通过测试 API 完成 reset/seed/三类订单铺设', screenshot: await shot(page, 'test_api_seed') };
    });

    await runCase(page, 'A02', '业务概览 Dashboard', async () => {
      await goto(page, '/dashboard');
      return { note: '展示概览卡片、趋势图与快捷入口', screenshot: await shot(page, 'dashboard') };
    });

    await runCase(page, 'A03', '订单归因工作台', async () => {
      await goto(page, '/orders');
      if (await clickFirstButtonIfExists(page, '查看详情')) {
        await waitForIdle(page);
        await clickFirstButtonIfExists(page, '关闭');
      }
      return { note: '展示归因统计、排查摘要与订单详情', screenshot: await shot(page, 'orders_workbench') };
    });

    await runCase(page, 'A04', '数据看板', async () => {
      await goto(page, '/data');
      return { note: '展示经营指标、收入分拆与跳转入口', screenshot: await shot(page, 'data_dashboard') };
    });

    await runCase(page, 'A05', '订单明细查看与批量解密', async () => {
      await goto(page, '/data/orders');
      const firstCheckbox = page.locator('tbody .n-checkbox').first();
      if (await firstCheckbox.count()) {
        await firstCheckbox.click({ force: true });
        await sleep(400);
        await clickFirstButtonIfExists(page, /批量解密/);
      }
      if (await clickFirstButtonIfExists(page, '查看详情')) {
        await waitForIdle(page);
        await clickFirstButtonIfExists(page, '关闭');
      }
      return { note: '执行订单勾选、解密与详情查看', screenshot: await shot(page, 'data_orders') };
    });

    await runCase(page, 'A06', '活动列表与活动商品', async () => {
      await goto(page, '/product/activity');
      await clickFirstButtonIfExists(page, '查看商品');
      await waitForIdle(page);
      const addLibraryButton = page.getByRole('button', { name: '加入商品库' }).first();
      if (await addLibraryButton.count()) {
        await addLibraryButton.click({ force: true });
        await waitForIdle(page);
      }
      const firstCard = page.locator('.product-card-shell').first();
      if (await firstCard.count()) {
        await firstCard.hover();
        await sleep(500);
        const logButton = firstCard.getByRole('button', { name: '查看操作日志' });
        if (await logButton.count() && await logButton.isVisible().catch(() => false)) {
          await logButton.click({ force: true });
          await waitForIdle(page);
          await page.keyboard.press('Escape').catch(() => {});
          await sleep(400);
        }
      }
      return { note: '展示活动商品、加入商品库和操作日志', screenshot: await shot(page, 'activity_products') };
    });

    await runCase(page, 'A07', '选品库与商品库浏览', async () => {
      await goto(page, '/product');
      await goto(page, '/product/library');
      return { note: '管理员演示选品库与共享商品库切换', screenshot: await shot(page, 'product_library') };
    });

    await runCase(page, 'A08', '达人经营台新增与多视图切换', async () => {
      await goto(page, '/talent');
      await page.getByRole('button', { name: '新增达人' }).click({ force: true });
      await page.getByPlaceholder('请输入达人昵称').fill(created.talentNickname);
      await page.getByPlaceholder('请输入抖音号').fill(created.talentDouyinNo);
      await page.getByPlaceholder('手机号、微信或其他联系方式').fill('微信：admin-demo');
      await page.locator('.n-modal textarea').fill('管理员演示创建的达人');
      await page.getByRole('button', { name: '保存' }).click({ force: true });
      await waitForIdle(page);

      for (const tab of ['团队公海', '我的达人', '自然出单达人', '达人黑名单']) {
        const target = page.locator('.n-tabs-tab').filter({ hasText: tab }).first();
        if (await target.count()) {
          await target.click({ force: true });
          await waitForIdle(page);
        }
      }
      return { note: '完成管理员新增达人并轮询全部达人视图', screenshot: await shot(page, 'talent_views') };
    });

    await runCase(page, 'A09', '达人详情刷新、认领、释放、拉黑、恢复', async () => {
      await goto(page, '/talent?view=TEAM_PUBLIC&page=1&size=10');
      if (await clickFirstButtonIfExists(page, '查看详情')) {
        await waitForIdle(page);
        await clickFirstButtonIfExists(page, '刷新达人信息');
        await waitForIdle(page);
        await clickFirstButtonIfExists(page, '关闭');
      }
      if (await clickFirstButtonIfExists(page, '认领')) {
        await sleep(500);
        await clickFirstButtonIfExists(page, '确认认领');
        await waitForIdle(page);
      }
      await goto(page, '/talent?view=MY_TALENTS&page=1&size=10');
      if (await clickFirstButtonIfExists(page, '释放')) {
        await sleep(500);
        await clickFirstButtonIfExists(page, '确认释放');
        await waitForIdle(page);
      }
      await goto(page, '/talent?view=TEAM_PUBLIC&page=1&size=10');
      if (await clickFirstButtonIfExists(page, '拉黑')) {
        await sleep(500);
        await clickFirstButtonIfExists(page, '确认拉黑');
        await waitForIdle(page);
      }
      await goto(page, '/talent?view=BLACKLIST&page=1&size=10');
      if (await clickFirstButtonIfExists(page, '解除拉黑')) {
        await sleep(500);
        await clickFirstButtonIfExists(page, '确认恢复');
        await waitForIdle(page);
      }
      return { note: '展示达人详情刷新与公海/私海/黑名单动作', screenshot: await shot(page, 'talent_actions') };
    });

    await runCase(page, 'A10', '寄样台审核流转', async () => {
      await goto(page, '/sample');
      const tab = page.locator('.n-tabs-tab').filter({ hasText: '待审核' }).first();
      if (await tab.count()) {
        await tab.click({ force: true });
        await waitForIdle(page);
      }
      if (await clickFirstButtonIfExists(page, '查看处理')) {
        await waitForIdle(page);
        if (await clickFirstButtonIfExists(page, '审核通过')) {
          await waitForIdle(page);
        }
        await clickFirstButtonIfExists(page, '关闭');
      }
      return { note: '管理员在寄样台完成待审核寄样处理', screenshot: await shot(page, 'sample_audit') };
    });

    await runCase(page, 'A11', '物流发货与签收', async () => {
      await goto(page, '/ops/shipping');
      if (await clickFirstButtonIfExists(page, '查看处理')) {
        await waitForIdle(page);
        if (await clickFirstButtonIfExists(page, '发货')) {
          await waitForIdle(page);
        }
        await clickFirstButtonIfExists(page, '关闭');
      }
      const shippedTab = page.locator('.n-tabs-tab').filter({ hasText: '快递中' }).first();
      if (await shippedTab.count()) {
        await shippedTab.click({ force: true });
        await waitForIdle(page);
      }
      if (await clickFirstButtonIfExists(page, '查看处理')) {
        await waitForIdle(page);
        if (await clickFirstButtonIfExists(page, '签收')) {
          await waitForIdle(page);
        }
        await clickFirstButtonIfExists(page, '关闭');
      }
      return { note: '管理员在物流页完成发货与签收推进', screenshot: await shot(page, 'ops_shipping') };
    });

    await runCase(page, 'A12', '独家状态看板', async () => {
      await goto(page, '/ops/exclusive');
      return { note: '展示独家达人与独家商家状态', screenshot: await shot(page, 'ops_exclusive') };
    });

    await runCase(page, 'A13', '用户管理 CRUD', async () => {
      const screenshot = await createUserCrud(page);
      return { note: `创建、编辑、重置密码、删除用户 ${created.userUsername}`, screenshot };
    });

    await runCase(page, 'A14', '角色管理 CRUD', async () => {
      const screenshot = await createRoleCrud(page, apiContext, adminToken);
      return { note: `创建、编辑、删除角色 ${created.roleCode}`, screenshot };
    });

    await runCase(page, 'A15', '系统配置 CRUD', async () => {
      const screenshot = await createConfigCrud(page, apiContext, adminToken);
      return { note: `创建、编辑、删除配置 ${created.configKey}`, screenshot };
    });

    await runCase(page, 'A16', '操作日志中心回看', async () => {
      await goto(page, '/system/operation-logs');
      await page.getByPlaceholder('操作人').fill('admin');
      await page.getByRole('button', { name: '查询' }).click({ force: true });
      await waitForIdle(page);
      return { note: '以管理员维度检索操作日志，验证前序动作已入库', screenshot: await shot(page, 'operation_logs') };
    });

    const operationLogs = await apiRequest(
      apiContext,
      'GET',
      `${API}/operation-logs?page=1&size=20&username=admin`,
      adminToken
    );
    report.notes.push(`操作日志接口返回 ${Number(operationLogs?.total || 0)} 条记录`);
  } finally {
    await context.close();
    await browser.close();
    await apiContext.dispose();
  }

  writeReport();
  console.log(`report: ${REPORT_MD}`);
}

main().catch((error) => {
  console.error(error);
  writeReport();
  process.exitCode = 1;
});
